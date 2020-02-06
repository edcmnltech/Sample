package com.sample.chat

import akka.actor.{Actor, ActorRef}
import com.sample.chat.User.{Connected, IncomingMessage, OutgoingMessage}

object User {
  final case class Connected(outgoing: ActorRef)
  final case class IncomingMessage(text: String)
  final case class OutgoingMessage(text: String)
}

class User(chatRoom: ActorRef, nickname: String) extends Actor {

  def receive: Receive = {
    case Connected(outgoing) =>
      println(s"user created: $nickname")
      context.become(connected(outgoing))
      chatRoom ! ChatRoom.Join
  }

  def connected(outgoing: ActorRef): Receive = {
    case IncomingMessage(text) =>
      chatRoom ! ChatRoom.ChatMessage(text)
    case ChatRoom.ChatMessage(text) =>
      outgoing ! OutgoingMessage(text)
  }

}
