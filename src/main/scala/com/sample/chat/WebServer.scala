package com.sample.chat

import java.sql.SQLIntegrityConstraintViolationException

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, ValidationRejection}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{CompletionStrategy, IOResult, Materializer, OverflowStrategy}
import akka.util.ByteString
import com.sample.chat.ChatRoom.ChatRoomName
import com.sample.chat.User.{IncomingMessage, OutgoingMessage, UserName}
import com.sample.chat.repository.{ChatHistory, ChatMessageRepository, ChatRoomRepository, table}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}

object WebServer extends App {

  def start(): Unit = {
    implicit val system: ActorSystem = ActorSystem("chat-actor-system")
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global

    val chatRoom1 = system.actorOf(ChatRoom.props(), "chat1")
    val chatRoom2 = system.actorOf(ChatRoom.props(), "chat2")
    val validRooms = List(chatRoom1, chatRoom2)

    val route: Route =
      path("chat" / Segment / "nickname" / Segment) { (c, n) =>
        convert(c, n) { (selectedRoom, newUser) =>
          get {
            validateChatRoom(selectedRoom, validRooms) match {
              case Some(validChatRoom) => handleWebSocketMessages(addUserToChatRoom(validChatRoom, newUser))
              case None => reject(new ValidationRejection("This didn't work."))
            }
          } ~
          post {
            ChatRoomRepository.insert(table.ChatRoom(selectedRoom.value, newUser.value)).onComplete {
              case Success(count) if count > 0 => println(s"added $count record")
              case Failure(exception) => exception match {
                case e: SQLIntegrityConstraintViolationException => println("duplicate chat name for user")
                case otherException => println(otherException)
              }
            }
            complete("post post post")
          }
        }
      }

    ChatRoomRepository.selectAll.foreach(println)
    val host = "0.0.0.0"
    val port = 8080
    val binding = Await.result(Http().bindAndHandle(route, host, port), 3.seconds)

    println(s"Server started... at $host:$port")
    StdIn.readLine()
    system.terminate()
  }

  def addUserToChatRoom(chatRoom: ChatRoom.Metadata, user: UserName)(implicit system: ActorSystem, ec: ExecutionContext): Flow[Message, Message, NotUsed] = {
    val userActorRef: ActorRef = system.actorOf(Props(new User(chatRoom, user)), user.value) //attaching of User to a ChatRoom
    Flow.fromSinkAndSource(incomingMessages(userActorRef, user, chatRoom), outgoingMessages(userActorRef, chatRoom.name))
  }

  def validateChatRoom(chatRoomName: ChatRoomName, chatRooms: List[ActorRef]): Option[ChatRoom.Metadata] =
    chatRooms
      .collectFirst { case room if (room.path.name == chatRoomName.value) => room }
      .map(ChatRoom.Metadata(_, chatRoomName))

  /**
   * chatroom's ActorRef is hooked via props of a User, every user must be in a chat room
   * sink, exactly one input, requesting, accepting data elements
   * ability of the webserver actor to receive message from a websocket request
   */
  def incomingMessages(userActor: ActorRef, user: UserName, chatRoom: ChatRoom.Metadata)(implicit mat: Materializer, ec: ExecutionContext): Sink[Message, NotUsed] = {
    val newLine = "\n"
    val parallelism = 4
    val streamedMessageTimeout = 5.seconds
    val actorSink: Sink[IncomingMessage, NotUsed] = Sink.actorRef[User.IncomingMessage](userActor, CompletionStrategy.immediately, logError)
    val historySink = Flow[IncomingMessage].mapAsync(2) { (i: IncomingMessage) =>
      ChatMessageRepository.insert(table.ChatMessage(user.value, i.text))
    }

    Flow[Message].mapAsync(parallelism) {
      case TextMessage.Strict(text) => Future.successful(Some(IncomingMessage(s"${user.value} : $text")))
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
  def outgoingMessages(userActor: ActorRef, chatRoomName: ChatRoomName)(implicit mat: Materializer, ec: ExecutionContext): Source[Message, Future[IOResult]] = {
    val historySource: Source[Message, Future[IOResult]] = ChatHistory.unload(chatRoomName).map(TextMessage.Strict)
    val outgoingMessageSource = Source.actorRef[User.OutgoingMessage](100, OverflowStrategy.fail)
      .mapMaterializedValue { outActor =>
        userActor ! User.Connected(outActor)
        NotUsed
      }.map((outMsg: OutgoingMessage) => TextMessage(outMsg.text))

    historySource.concat(outgoingMessageSource)
  }

  def logError: Throwable => Any =  { throwable: Throwable => print(s"Something went wrong: ${throwable.getMessage}") }
  def throwError: Any => Exception = _  => new Exception("Something went wrong")
  def completionImmediatelyMatcher: Any => CompletionStrategy = _ => CompletionStrategy.immediately
  def convert(chatRoomName: String, userName: String)(f: (ChatRoomName, UserName) => Route): Route = f(ChatRoomName(chatRoomName), UserName(userName))

  start()
}
