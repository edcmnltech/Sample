package com.sample.chat

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
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

    def newUser(room: ActorRef): Flow[Message, Message, NotUsed] = {
      val userActor = system.actorOf(Props(new User(room)))

      //chatroom's ActorRef is hooked via props of a User, every user must be in a chat room
      //sink, exactly one input, requesting, accepting data elements
      //ability of the webserver actor to receive message from a websocket request
      val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) => IncomingMessage("nickname", text)
      }.to(Sink.actorRef[User.IncomingMessage](userActor, PoisonPill, logError))

      //user sends to its chatroom actor `outgoing ! OutgoingMessage(author, text)`, therefore triggering
      //source, exactly one output, emitting data elements
      //one time setting up of the source
      //ability of the actor to send message to a chatroom, then sending to all users connected to it
      val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[User.OutgoingMessage](10, OverflowStrategy.fail).mapMaterializedValue { outActor =>
        userActor ! User.Connected("nickname", outActor)
        NotUsed
      }.map((outMsg: User.OutgoingMessage) => TextMessage(outMsg.text))

      Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
    }

    //endpoint to send chat, and then broadcasts the message to different user
    val route =
      path("chat") {
        get {
          //hooking of the actor to a chatroom
          handleWebSocketMessages(newUser(chatRoom))
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

  def extractUsernameFlow: Flow[String, Tuple2, NotUsed] = {

  }

  start()
}