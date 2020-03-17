package com.sample.chat

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, InvalidActorNameException, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, ValidationRejection}
import akka.stream.scaladsl.{Broadcast, Flow, Sink, Source}
import akka.stream.{ActorMaterializer, CompletionStrategy, Materializer, OverflowStrategy}
import akka.util.Timeout
import com.sample.chat.User.{IncomingMessage, OutgoingMessage}
import com.sample.chat.repository.table._
import com.sample.chat.repository._
import com.sample.chat.util.CORSHandler
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}

/**
 * Add Password per Chatroom and User
 *
 * https://owasp.org/www-project-cheat-sheets/cheatsheets/Session_Management_Cheat_Sheet.html#Session_ID_Properties
 * https://owasp.org/www-project-cheat-sheets/cheatsheets/Authentication_Cheat_Sheet.html
 *
 * https://guide.freecodecamp.org/jquery/jquery-ajax-post-method/
 */

/**
 * TODO:
 * 1. Improve database persistence of the message, maybe 1000 per batch insert instead of saving every chat.
 * 2. Update sql tables that use LONG to use com.byteslounge.slickrepo.version.InstantVersion instead
 */

object WebServer extends App with CORSHandler {

  private var chatRooms: Set[ChatRoomActorRef] = Set.empty

  def start(): Unit = {

    implicit val system: ActorSystem = ActorSystem("chat-actor-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer.create(system)
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global

    val route: Route =
      corsHandler(path("chatid" / IntNumber / "chatname" / Segment / "nickname" / Segment) { (cid, cname, n) =>
        convert(cid, cname, n) { (selectedRoomId, selectedRoom, user) =>
          get {
            val validations: Future[Flow[Message, Message, NotUsed]] = for {
              validChatRoom <- validateChatRoom(selectedRoom)
              flow <- Future(linkChatRoomAndUser(validChatRoom, user))
            } yield flow

            //NOTE: If there is no recovery of the future, there will be silent failure
            validations.recoverWith { case a => Future.failed(a) }

            onComplete(validations) {
              case Success(flow)      => handleWebSocketMessages(flow)
              case Failure(exception) => logAndReject(exception)
            }
          }
        }
      } ~
      path("chatrooms"){
        get {
          onComplete(ChatRoomRepository.selectAll) {
            case Success(value)     => complete(value.asJson)
            case Failure(exception) => reject(ValidationRejection(s"Error in retrieving chatrooms: ${exception.getMessage}"))
          }
        } ~
        post {
          //FIXME: unsafe!
          entity(as[table.ChatRoom]) { room =>
            val validations: Future[Int] = for {
                validUser       <- validateChatUser(room.creator)
                addedChatRoomId <- ChatRoomRepository.insert(room)
              } yield addedChatRoomId

            validations.recoverWith { case a => Future.failed(a) }

            onComplete(validations) {
              case Success(roomId)    => complete(s"Chat room: $roomId record")
              case Failure(exception) => logAndReject(exception)
            }
          }
        }
      } ~
      path("auth") {
        entity(as[VerifyChatRoomCreator]) { verify =>
          post {
            onComplete(ChatRoomRepository.checkIfValidUser(verify.userName, verify.roomName, Option(verify.password))) {
              case Success(_)           => complete("Auth success.")
              case Failure(exception)   => logAndReject(exception)
            }
          }
        }
      })

    val host = "0.0.0.0"
//    val host = "192.168.147.165"
    val port = 8080
    val binding = Await.result(Http().bindAndHandle(route, host, port), 3.seconds)

    system.log.info("Server started at... http://{}:{}/", host, port)
    StdIn.readLine()
    system.terminate()
  }

  def linkChatRoomAndUser(chatRoom: table.ChatRoomActorRef, user: ChatUserName)(implicit system: ActorSystem, ec: ExecutionContext, mat: ActorMaterializer): Flow[Message, Message, NotUsed] = {
    val userActorRef: ActorRef = system.actorOf(Props(new User(chatRoom, user))/*, user.value*/) //attaching of User to a ChatRoom
    Flow.fromSinkAndSource(incomingMessages(userActorRef, user, chatRoom), outgoingMessages(userActorRef, chatRoom.meta.id))
  }

  def validateChatUser(username: ChatUserName)(implicit executionContext: ExecutionContext): Future[ChatUser] = ChatUserRepository.selectByName(username)

  def validateChatRoom(name: ChatRoomName)(implicit executionContext: ExecutionContext, system: ActorSystem, timeout: Timeout = Timeout(5.seconds)): Future[ChatRoomActorRef] = {
    Source.fromFuture(ChatRoomRepository.selectByName(name))

    ChatRoomRepository.selectByName(name) map { room =>
      chatRooms.find(_.meta == room) match {
        case None =>
          val actorRef = system.actorOf(com.sample.chat.ChatRoom.props(), room.name.value)
          val chatRoomActorRef = ChatRoomActorRef(actorRef, room)
          chatRooms += chatRoomActorRef
          chatRoomActorRef
        case Some(r) => r
      }
    }
  }

  /**
   * chatroom's ActorRef is hooked via props of a User, every user must be in a chat room
   * sink, exactly one input, requesting, accepting data elements
   * ability of the webserver actor to receive message from a websocket request
   */
  def incomingMessages(userActor: ActorRef, user: ChatUserName, chatRoom: ChatRoomActorRef)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem): Sink[Message, NotUsed] = {
    val streamedMessageTimeout = 5.seconds

    val messageFlow = Flow[Message].map {
      case TextMessage.Strict(text) => Future.successful(IncomingMessage(text, user))
      case TextMessage.Streamed(textStream) => textStream
        .completionTimeout(streamedMessageTimeout)
        .runFold(StringBuilder.newBuilder)((builder, s) => builder.append(s))
        .map(b => IncomingMessage(b.toString(), user))
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        Future.successful(IncomingMessage("", user)) }
      .groupedWithin(10, .5.seconds)
      .map(Future.sequence(_))

    val insertBulk          = ChatMessageRepository.insertBulk(chatRoom.meta.id)(_)
    val dbSink              = Sink.foreach[Future[Seq[IncomingMessage]]](msgs => insertBulk(msgs))
    val actorSink           = Sink.actorRef[Future[Seq[IncomingMessage]]](userActor, CompletionStrategy.immediately)
    val incomingMessageSink = Sink.combine(dbSink, actorSink)(Broadcast[Future[Seq[IncomingMessage]]](_))

    messageFlow.to(incomingMessageSink)
  }

  /**
   * user sends to its chatroom actor `outgoing ! OutgoingMessage(author, text)`, therefore triggering
   * source, exactly one output, emitting data elements
   * one time setting up of the source
   * ability of the actor to send message to a chatroom, then sending to all users connected to it
   */
  def outgoingMessages(userActor: ActorRef, chatRoomName: ChatRoomId)(implicit mat: Materializer, ec: ExecutionContext): Source[Message, NotUsed] = {
    def msgFormat(sender: String, msg: String) = s"$sender: $msg"
    val historySource         = Source.fromPublisher(ChatMessageRepository.selectByRoomId(chatRoomName)).map(a => TextMessage.Strict(msgFormat(a.sender, a.message))).buffer(1000, OverflowStrategy.backpressure)
    val outgoingMessageSource = Source.actorRef[User.OutgoingMessage](100, OverflowStrategy.fail).buffer(1000, OverflowStrategy.backpressure)
      .mapMaterializedValue { outActor =>
        println(s"Source materialized actor: $outActor")
        userActor ! User.Connected(outActor)
        NotUsed
      }.map((outMsg: OutgoingMessage) => TextMessage(msgFormat(outMsg.sender.value, outMsg.text)))

    historySource.concat(outgoingMessageSource)
  }

  def logError: Throwable => Any =  { throwable: Throwable => print(s"Something went wrong: ${throwable.getMessage}") }
  def throwError: Any => Exception = _  => new Exception("Something went wrong")
  def logAndReject: Throwable => Route = { exception =>
    println(s"ERROR: $exception")
    exception match {
      case _: InvalidActorNameException => reject;
      case _ => reject(ValidationRejection(exception.getMessage))
    }
  }
  def completionImmediatelyMatcher: Any => CompletionStrategy = _ => CompletionStrategy.immediately
  def convert(cid: Int, cname: String, userName: String)(f: (ChatRoomId, ChatRoomName, ChatUserName) => Route): Route = f(new ChatRoomId(cid), new ChatRoomName(cname), new ChatUserName(userName))

  start()
}