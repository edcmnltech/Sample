package com.sample.chat

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MalformedQueryParamRejection, RejectionHandler, RequestContext, Route, RouteResult, ValidationRejection}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{CompletionStrategy, IOResult, Materializer, OverflowStrategy}
import akka.util.ByteString
import com.sample.chat.User.{IncomingMessage, OutgoingMessage}
import com.sample.chat.model.ChatHistory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.StdIn

final case class TextWithAuthor(text: String, author: String)

object WebServer extends App {

  def start(): Unit = {
    implicit val system = ActorSystem("chat-actor-system")
    implicit val ec = ExecutionContext.global

    val chatRoom1 = system.actorOf(ChatRoom.props, "chat1")
    val chatRoom2 = system.actorOf(ChatRoom.props, "chat2")

    def newUser(chatRoomRef: ActorRef, nickname: String): Flow[Message, Message, NotUsed] = {
      val userActor = system.actorOf(Props(new User(chatRoomRef, nickname)), nickname)
      val chatRoomName = chatRoomRef.path.name

      Flow.fromSinkAndSource(incomingMessages(userActor, nickname, chatRoomName), outgoingMessages(userActor, chatRoomName))
    }

    val route: Route =
      path("chat" / Segment / "nickname" / Segment) { (chatRoomName, nickname) =>
        get {
          val chatRoomActorRef: Option[ActorRef] = chatRoomName match {
            case "chat1" => Option(chatRoom1)
            case "chat2" => Option(chatRoom2)
            case _ => None
          }
          chatRoomActorRef match {
            case Some(actorRef) => handleWebSocketMessages(newUser(actorRef, nickname))
            case None => reject(new ValidationRejection("This didn't work."))
          }
        }
      }

    val binding = Await.result(Http().bindAndHandle(route, "localhost", 8080), 3.seconds)

    println("Server started...")
    StdIn.readLine()
    system.terminate()
  }

  def logError: Throwable => Any =  { throwable: Throwable => print(s"something went wrong: ${throwable.getMessage}") }
  def throwError: Any => Exception = { _  => new Exception("Something went wrong") }
  def completionImmediatelyMatcher: Any => CompletionStrategy = { _ => CompletionStrategy.immediately }

  /**
   *
   * @param userActor
   * @param nickname
   * @return
   *
   * chatroom's ActorRef is hooked via props of a User, every user must be in a chat room
   * sink, exactly one input, requesting, accepting data elements
   * ability of the webserver actor to receive message from a websocket request
   */
  def incomingMessages(userActor: ActorRef, nickname: String, chatRoomName: String)(implicit mat: Materializer, ec: ExecutionContext): Sink[Message, NotUsed] = {
    val parallelism = 4;
    val streamedMessageTimeout: FiniteDuration = 5 seconds

    val actorSink: Sink[IncomingMessage, NotUsed]  = Sink.actorRef[User.IncomingMessage](userActor, CompletionStrategy.immediately, logError)
    val historySink: Sink[ByteString, Future[IOResult]] = ChatHistory.load(chatRoomName)

    Flow[Message].mapAsync(parallelism) {
      case TextMessage.Strict(text) => Future.successful(Some(IncomingMessage(s"$nickname : $text")))
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        Future.successful(None) }
      .collect { case Some(msg) => msg }
      .via(Flow[IncomingMessage].map(im => ByteString(im.text+"\n")))
      .alsoToMat(historySink)(Keep.right)
      .viaMat(Flow[ByteString].map(a => IncomingMessage(a.utf8String)))(Keep.right)
      .to(actorSink)
  }

  /**
   *
   * @param userActor
   * @return
   *
   * user sends to its chatroom actor `outgoing ! OutgoingMessage(author, text)`, therefore triggering
   * source, exactly one output, emitting data elements
   * one time setting up of the source
   * ability of the actor to send message to a chatroom, then sending to all users connected to it
   */
  def outgoingMessages(userActor: ActorRef, chatRoomName: String)(implicit mat: Materializer, ec: ExecutionContext): Source[Message, Future[IOResult]] = {
    val historySource: Source[Message, Future[IOResult]] = ChatHistory.unload(chatRoomName).map(TextMessage.Strict)
    val outgoingMessageSource = Source.actorRef[User.OutgoingMessage](100, OverflowStrategy.fail)
      .mapMaterializedValue { outActor =>
        userActor ! User.Connected(outActor)
        NotUsed
      }.map { outMsg: OutgoingMessage => TextMessage(outMsg.text) }

    historySource.concat(outgoingMessageSource)
  }

  def rejectionHandler: RejectionHandler = {
    RejectionHandler.newBuilder()
      .handleNotFound(complete((StatusCodes.NotFound, "Page not found")))
      .result()
  }

  start()
}
