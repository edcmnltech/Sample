package com.sample.chat

import akka.actor.{Actor, ActorRef, DeadLetter, UnhandledMessage}
import akka.http.scaladsl.model.ws.TextMessage
import com.sample.chat.User.{Connected, IncomingMessage, OutgoingMessage}

object User {
  final case class Connected(outgoing: ActorRef)
  final case class IncomingMessage(text: String)
  final case class OutgoingMessage(text: String)
}

import akka.http.scaladsl.model.ws.Message

class User(chatRoom: ActorRef, nickname: String) extends Actor {

  def receive: Receive = {
    case Connected(outgoing) =>
      println(s"user created: $nickname")
      context.become(connected(outgoing))
      chatRoom ! ChatRoom.Join
  }

  def connected(outgoing: ActorRef): Receive = {
    case IncomingMessage(text) =>
      println(s"msg in <- $chatRoom")
      chatRoom ! ChatRoom.ChatMessage(text)
    case ChatRoom.ChatMessage(text) =>
      println(s"msg out -> $outgoing")
      outgoing ! OutgoingMessage(text)
    case d: DeadLetter =>
      println(s"dead letter sender: ${d.sender} recepient: ${d.recipient} messsage: ${d.message}")
    case u: UnhandledMessage =>
      println(s"dead letter sender: ${u.sender} recepient: ${u.recipient} messsage: ${u.message}")
    case x: Message =>
      println(s"default $x")
  }

}
