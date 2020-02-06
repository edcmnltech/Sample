package com.sample.chat

import akka.actor.{Actor, ActorRef}
import com.sample.chat.User.{Connected, IncomingMessage, OutgoingMessage}

object User {
  final case class Connected(nickname: String, outgoing: ActorRef)
  final case class IncomingMessage(author: String, text: String)
  final case class OutgoingMessage(author: String, text: String)
}

class User(chatRoom: ActorRef) extends Actor {

  def receive: Receive = {
    case Connected(nickname, outgoing) =>
      println(s"user created: $nickname")
      context.become(connected(nickname, outgoing))
      chatRoom ! ChatRoom.Join
  }

  def connected(nickname: String, outgoing: ActorRef): Receive = {
    case IncomingMessage(author, text) =>
      chatRoom ! ChatRoom.ChatMessage(author, text)
    case ChatRoom.ChatMessage(author, text) =>
      outgoing ! OutgoingMessage(author, text)
  }

}
