package com.sample.chat

import akka.actor.{ActorRef, ActorSystem, InvalidActorNameException, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, ValidationRejection}
import akka.stream.scaladsl.{Broadcast, Flow, Sink, Source}
import akka.stream.{ActorMaterializer, CompletionStrategy, Materializer, OverflowStrategy}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.sample.chat.User.{IncomingMessage, OutgoingMessage}
import com.sample.chat.repository.table._
import com.sample.chat.repository.{ChatMessageRepository, ChatRoomRepository, ChatUserRepository, VerifyChatRoomCreator, table}
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
//object Main extends App {
//  def t(i:Int) = new Thread{
//    override def run(): Unit = while(true){
//      1+1
//    }
//  }
//  (1 to 72).foreach { i =>
//    val tt = t(i)
//    tt.start()
//  }
//}

object WebServer extends App with CORSHandler {

  private var chatRooms: Set[ChatRoomActorRef] = Set.empty

  def start(): Unit = {

    implicit val system: ActorSystem = ActorSystem("chat-actor-system")
    implicit val materializer = ActorMaterializer.create(system)
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
                validUser <- validateChatUser(room.creator)
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
//    implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")

    val newLine = "\n"
    val parallelism = 4
    val streamedMessageTimeout = 5.seconds
//    val actorSink: Sink[IncomingMessage, NotUsed] = Sink.actorRef[User.IncomingMessage](userActor, CompletionStrategy.immediately)
//    val historySink: Flow[IncomingMessage, Nothing, NotUsed] = Flow[IncomingMessage].mapAsync(4) { incoming =>
//      ChatMessageRepository.insert(table.ChatMessage(user.value, incoming.text, chatRoom.meta.id))
//    }
//    val historySink: Flow[Message, Sink[Future[immutable.Seq[IncomingMessage]], Future[Done]], NotUsed] = StreamGraphs.batchInsert("eli", new ChatRoomId(1))

//    Flow[Message].mapAsync(parallelism) {
//      case TextMessage.Strict(text) => Future.successful(Some(IncomingMessage(text)))
//      case TextMessage.Streamed(textStream) => textStream
//        .completionTimeout(streamedMessageTimeout)
//        .runFold(StringBuilder.newBuilder)((builder, s) => builder.append(s))
//        .map(b => Option(IncomingMessage(b.toString())))
//      case bm: BinaryMessage =>
//        bm.dataStream.runWith(Sink.ignore)
//        Future.successful(None)
//    }.collect { case Some(msg) => msg }
//      .via(historySink)
//      .viaMat(Flow[table.ChatMessage].map(s => IncomingMessage(s"${s.sender} : ${s.message}")))(Keep.right)
//      .to(actorSink)

    /// START

    val messageFlow = Flow[Message].map { //then try mapAsync(parallelism)
      case TextMessage.Strict(text) => Future.successful(IncomingMessage(text))
      case TextMessage.Streamed(textStream) => textStream
        .completionTimeout(streamedMessageTimeout)
        .runFold(StringBuilder.newBuilder)((builder, s) => builder.append(s))
        .map(b => IncomingMessage(b.toString()))
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        Future.successful(IncomingMessage(""))
    }
      .groupedWithin(2, .5.seconds)
      .map(Future.sequence(_))

    val printing: Any => Unit = a => println("Kobeed_19 "+a)
//
    val printSink: Sink[Future[Seq[IncomingMessage]], Future[Done]] = Sink.foreach(printing(_))

    val dbSink: Sink[Future[Seq[IncomingMessage]], Future[Done]] = Sink.foreach[Future[Seq[IncomingMessage]]](ChatMessageRepository.insertBulk)
    val actorSink: Sink[Future[Seq[IncomingMessage]], NotUsed] = Sink.actorRef[Future[Seq[IncomingMessage]]](userActor, CompletionStrategy.immediately)

    val compositeSink = Sink.combine(dbSink, actorSink, printSink)(Broadcast[Future[Seq[IncomingMessage]]](_))

    //    def actorSink(userActor: ActorRef): Sink[IncomingMessage, NotUsed] = Sink.actorRef[User.IncomingMessage](userActor, CompletionStrategy.immediately)

    messageFlow.to(compositeSink)

    /// END
  }

  /**
   * user sends to its chatroom actor `outgoing ! OutgoingMessage(author, text)`, therefore triggering
   * source, exactly one output, emitting data elements
   * one time setting up of the source
   * ability of the actor to send message to a chatroom, then sending to all users connected to it
   */
  def outgoingMessages(userActor: ActorRef, chatRoomName: ChatRoomId)(implicit mat: Materializer, ec: ExecutionContext): Source[Message, NotUsed] = {
    val historySource = Source.fromPublisher(ChatMessageRepository.selectByRoomId(chatRoomName)).map(a => TextMessage.Strict(a.message)).buffer(1000, OverflowStrategy.backpressure)
    val outgoingMessageSource = Source.actorRef[User.OutgoingMessage](100, OverflowStrategy.fail).buffer(1000, OverflowStrategy.backpressure)
      .mapMaterializedValue { outActor =>
        println(s"Source materialized actor: $outActor")
        userActor ! User.Connected(outActor)
        NotUsed
      }.map((outMsg: OutgoingMessage) => TextMessage(outMsg.text))

    historySource.concat(outgoingMessageSource)
  }

  def logError: Throwable => Any =  { throwable: Throwable => print(s"Something went wrong: ${throwable.getMessage}") }
  def throwError: Any => Exception = _  => new Exception("Something went wrong")
  def logAndReject: Throwable => Route = { exception =>
    println(s"ERROR: $exception")
    exception match {
      case _: InvalidActorNameException => reject;
      case _ => reject(new ValidationRejection(exception.getMessage))
    }
  }
  def completionImmediatelyMatcher: Any => CompletionStrategy = _ => CompletionStrategy.immediately
  def convert(cid: Int, cname: String, userName: String)(f: (ChatRoomId, ChatRoomName, ChatUserName) => Route): Route = f(new ChatRoomId(cid), new ChatRoomName(cname), new ChatUserName(userName))

  start()
}