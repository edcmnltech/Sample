package com.sample.chat

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, DeadLetter, Props, UnhandledMessage}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.Flow
import com.sample.chat.User.{Connected, IncomingMessage, OutgoingMessage, UserName}
import com.sample.chat.WebServer.{incomingMessages, outgoingMessages}
import akka.http.scaladsl.model.ws.Message

import scala.concurrent.ExecutionContext

object User {
  final case class UserName(value: String) extends AnyVal
  final case class Connected(outgoing: ActorRef)
  final case class IncomingMessage(text: String)
  final case class OutgoingMessage(text: String)
}

class User(chatRoom: ChatRoom.Metadata, userName: UserName) extends Actor {

  def receive: Receive = {
    case Connected(outgoing) =>
      println(s"user created: ${userName}")
      context.become(connected(outgoing))
      chatRoom.actorRef ! ChatRoom.Join
  }

  def connected(outgoing: ActorRef): Receive = {
    case IncomingMessage(text) =>
      println(s"msg in <- ${chatRoom.actorRef}")
      chatRoom.actorRef ! ChatRoom.ChatMessage(text)
    case ChatRoom.ChatMessage(text) =>
      println(s"msg out -> $outgoing")
      outgoing ! OutgoingMessage(text)
  }

}
