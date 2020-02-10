package com.sample.chat

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.{CompletionStrategy, IOResult, Materializer, OverflowStrategy, SystemMaterializer}
import akka.stream.scaladsl.{FileIO, Flow, Keep, RunnableGraph, Sink, Source}
import akka.util.ByteString
import com.sample.chat.Total.Increment
import com.sample.chat.User.IncomingMessage
import com.sample.chat.model.ChatHistory

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.StdIn

final case class TextWithAuthor(text: String, author: String)

object WebServer extends App {

  def start(): Unit = {
    implicit val system = ActorSystem("chat-actor-system")
    implicit val ec = ExecutionContext.global

    val chatRoom1 = system.actorOf(ChatRoom.props, "chat1")
    val chatRoom2 = system.actorOf(ChatRoom.props, "chat2")

    //Composite Flow
    def newUser(chatroomRef: ActorRef, nickname: String): Flow[Message, Message, NotUsed] = {
      val userActor = system.actorOf(Props(new User(chatroomRef, nickname)), nickname)

      val chatRoomName = chatroomRef.path.name
      val source1: Source[Message, Future[IOResult]] = ChatHistory.loadHistoryFrom(chatRoomName).map(TextMessage.Strict)
      val source2: Source[Message, NotUsed.type] = outgoingMessages(userActor)
      val combinedSource: Source[Message, Future[IOResult]] = source1.concat(source2)

      Flow.fromSinkAndSource(incomingMessages(userActor, nickname), combinedSource)
    }

    //endpoint to send chat, and then broadcasts the message to different user
    val route =
      path("chat" / Segment / "nickname" / Segment) { (chatRoomName, nickname) =>
        get {
          val chatRoomActorRef = chatRoomName match {
            case "chat1" => chatRoom1
            case "chat2" => chatRoom2
            //TODO: catch wild card scenario, maybe use custom ADT.
          }
          handleWebSocketMessages(newUser(chatRoomActorRef, nickname))
        }
      }

    val binding = Await.result(Http().bindAndHandle(route, "localhost", 8080), 3.seconds)

    println("Server started")
    StdIn.readLine()
    system.terminate()
  }

  def logError: Throwable => Any = {
    case throwable: Throwable => print(s"something went wrong: ${throwable.getMessage}")
  }

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
  def incomingMessages(userActor: ActorRef, nickname: String): Sink[Message, NotUsed] = {
    Flow[Message].map {
      case TextMessage.Strict(text) => IncomingMessage(nickname+" : "+text)
    }.to(Sink.actorRef[User.IncomingMessage](userActor, CompletionStrategy.immediately, logError))
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
  def outgoingMessages(userActor: ActorRef): Source[TextMessage.Strict, NotUsed.type] = {
    Source.actorRef[User.OutgoingMessage](100, OverflowStrategy.fail) //fail for now
    .mapMaterializedValue { outActor =>
      userActor ! User.Connected(outActor)
      NotUsed
    }.map((outMsg: User.OutgoingMessage) => TextMessage(outMsg.text))

  }

  start()
}
