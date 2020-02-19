package com.sample.chat

import akka.actor.{Actor, ActorRef, PoisonPill}
import akka.stream.CompletionStrategy
import com.sample.chat.User.{Connected, IncomingMessage, OutgoingMessage, UserName}
import com.sample.chat.repository.table.ChatRoomActorRef

object User {
  final case class UserName(value: String) extends AnyVal
  final case class Connected(outgoing: ActorRef)
  final case class IncomingMessage(text: String)
  final case class OutgoingMessage(text: String)
}

class User(chatRoom: ChatRoomActorRef, userName: UserName) extends Actor {

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
    case a: CompletionStrategy =>
      self ! PoisonPill
    case wth =>
      println(s"what kind of message $wth")
  }

}
