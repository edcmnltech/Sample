package com.sample.chat

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, ValidationRejection}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import akka.util.Timeout
import com.sample.chat.User.{IncomingMessage, OutgoingMessage}
import com.sample.chat.repository.table._
import com.sample.chat.repository.{ChatMessageRepository, ChatRoomRepository, ChatUserRepository, table}
import com.sample.chat.util.CORSHandler
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._

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

object WebServer extends App with CORSHandler {

  private var chatRooms: Set[ChatRoomActorRef] = Set.empty

  def start(): Unit = {
    implicit val system: ActorSystem = ActorSystem("chat-actor-system")
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global

    val route: Route =
      corsHandler(path("chat" / Segment / "nickname" / Segment) { (c, n) =>
        convert(c, n) { (selectedRoom, user) =>
          get {
            val validations: Future[Flow[Message, Message, NotUsed]] = for {
//                validUser <- validateChatUser(user)
              validChatRoom <- validateChatRoom(selectedRoom)
//                validChatRoomPassword <- validateChatRoomPassword(validChatRoom.meta.name, new ChatRoomPassword("password"))
              flow <- Future(addUserToChatRoom(validChatRoom, user))
            } yield flow

            //NOTE: If there is no recovery of the future, there will be silent failure
            validations.recoverWith { a =>
              Future.failed(a)
            }

            onComplete(validations) {
              case Success(flow) => handleWebSocketMessages(flow)
              case Failure(exception) => logAndReject(exception)
            }
          }
        }
      } ~
      path("chatrooms"){
        get {
          onComplete(ChatRoomRepository.selectAll) {
            case Success(value) => complete(value.asJson)
            case Failure(exception) => reject(new ValidationRejection("This didn't work."))
          }
        } ~
        post {
          //FIXME: unsafe!
          entity(as[table.ChatRoom]) { room =>

            val validations: Future[Int] = for {
                validUser <- validateChatUser(room.creator)
                addedChatRoomId <- ChatRoomRepository.insert(room)
              } yield addedChatRoomId

            validations.recoverWith { a =>
              Future.failed(a)
            }

            onComplete(validations) {
              case Success(roomId) => complete(s"Chat room: $roomId record")
              case Failure(exception) => logAndReject(exception)
            }
          }
        }
      } ~
      path("auth") {
        entity(as[VerifyChatRoomCreator]) { verify =>
          post {
            onComplete(ChatRoomRepository.checkIfValidUser(verify.userName, verify.roomName, verify.password)) {
              case Success(true) => complete("Auth success.")
              case Success(false) => reject
              case Failure(exception) => logAndReject(exception)
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

  def addUserToChatRoom(chatRoom: table.ChatRoomActorRef, user: ChatUserName)(implicit system: ActorSystem, ec: ExecutionContext): Flow[Message, Message, NotUsed] = {
    val userActorRef: ActorRef = system.actorOf(Props(new User(chatRoom, user)), user.value) //attaching of User to a ChatRoom
    Flow.fromSinkAndSource(incomingMessages(userActorRef, user, chatRoom), outgoingMessages(userActorRef, chatRoom.meta.id))
  }

  def validateChatUser(username: ChatUserName)(implicit executionContext: ExecutionContext): Future[ChatUser] = ChatUserRepository.selectByName(username)

  def validateChatRoom(name: ChatRoomName)(implicit executionContext: ExecutionContext, system: ActorSystem, timeout: Timeout = Timeout(5.seconds)): Future[ChatRoomActorRef] =
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

  def validateChatRoomPassword(room: table.ChatRoomName, pass: ChatRoomPassword)(implicit executionContext: ExecutionContext, system: ActorSystem): Future[Boolean] =
    ChatRoomRepository.checkIfPasswordMatch(room, pass)

  /**
   * chatroom's ActorRef is hooked via props of a User, every user must be in a chat room
   * sink, exactly one input, requesting, accepting data elements
   * ability of the webserver actor to receive message from a websocket request
   */
  def incomingMessages(userActor: ActorRef, user: ChatUserName, chatRoom: ChatRoomActorRef)(implicit mat: Materializer, ec: ExecutionContext): Sink[Message, NotUsed] = {
    val newLine = "\n"
    val parallelism = 4
    val streamedMessageTimeout = 5.seconds
    val actorSink: Sink[IncomingMessage, NotUsed] = Sink.actorRef[User.IncomingMessage](userActor, CompletionStrategy.immediately, logError)
    val historySink = Flow[IncomingMessage].mapAsync(2) { incoming =>
      ChatMessageRepository.insert(table.ChatMessage(user.value, incoming.text, chatRoom.meta.id))
    }

    Flow[Message].mapAsync(parallelism) {
      case TextMessage.Strict(text) => Future.successful(Some(IncomingMessage(text)))
      case TextMessage.Streamed(textStream) => textStream
        .completionTimeout(streamedMessageTimeout)
        .runFold(StringBuilder.newBuilder)((builder, s) => builder.append(s))
        .map(b => Option(IncomingMessage(b.toString())))
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        Future.successful(None)
    }.collect { case Some(msg) => msg }
      .via(historySink)
      .viaMat(Flow[table.ChatMessage].map(s => IncomingMessage(s"${s.sender} : ${s.message}")))(Keep.right)
      .to(actorSink)
  }

  /**
   * user sends to its chatroom actor `outgoing ! OutgoingMessage(author, text)`, therefore triggering
   * source, exactly one output, emitting data elements
   * one time setting up of the source
   * ability of the actor to send message to a chatroom, then sending to all users connected to it
   */
  def outgoingMessages(userActor: ActorRef, chatRoomName: ChatRoomId)(implicit mat: Materializer, ec: ExecutionContext): Source[Message, NotUsed] = {
    val historySource = Source.fromPublisher(ChatMessageRepository.selectByRoomId(chatRoomName)).map(a => TextMessage.Strict(a.message))
    val outgoingMessageSource = Source.actorRef[User.OutgoingMessage](100, OverflowStrategy.fail)
      .mapMaterializedValue { outActor =>
        userActor ! User.Connected(outActor)
        NotUsed
      }.map((outMsg: OutgoingMessage) => TextMessage(outMsg.text))

    historySource.concat(outgoingMessageSource)
  }

  def logError: Throwable => Any =  { throwable: Throwable => print(s"Something went wrong: ${throwable.getMessage}") }
  def throwError: Any => Exception = _  => new Exception("Something went wrong")
  def logAndReject: Throwable => Route = { exception =>
    println(s"ERORR: $exception")
    reject(new ValidationRejection(exception.getMessage))
  }
  def completionImmediatelyMatcher: Any => CompletionStrategy = _ => CompletionStrategy.immediately
  def convert(chatRoomName: String, userName: String)(f: (ChatRoomName, ChatUserName) => Route): Route = f(new ChatRoomName(chatRoomName), new ChatUserName(userName))

  start()
}