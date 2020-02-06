package com.sample.chat

import akka.NotUsed
import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.sample.chat.User.IncomingMessage

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

object WebServer extends App {

  def start(): Unit = {
    implicit val system = ActorSystem("chat-actor-system")

    val chatRoom = system.actorOf(ChatRoom.props, "chat")

    def newUser(): Flow[Message, Message, NotUsed] = {
      val userActor = system.actorOf(Props(new User(chatRoom)))

      //sink, exactly one input, requesting, accepting data elements
      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message].map {
          case TextMessage.Strict(text) => IncomingMessage(text)
        }.to(Sink.actorRef[User.IncomingMessage](userActor, PoisonPill, logError))

      //source, exactly one output, emitting data elements
      val outgoingMessages: Source[Message, NotUsed] =
        Source.actorRef[User.OutgoingMessage](10, OverflowStrategy.fail).mapMaterializedValue { outActor =>
          userActor ! User.Connected(outActor)
          NotUsed
        }.map((outMsg: User.OutgoingMessage) => TextMessage(outMsg.text))

      Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
    }

    val route =
      path("chat") {
        get {
          handleWebSocketMessages(newUser)
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

  start()
}
