package com.sample.chat

import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.sample.chat.ChatRoom.{ChatMessage, Join}

object ChatRoom {
  final case object Join
  final case class ChatMessage(message: String)
  def props() = Props(classOf[ChatRoom])
  var chatRooms: Set[ActorRef] = Set.empty
}

class ChatRoom extends Actor {

  var users: Set[ActorRef] = Set.empty

  def receive: Receive = {
    case Join =>
      users += sender()
      context.watch(sender())

    case Terminated(user) =>
      users -= user

    case msg: ChatMessage =>
      users.foreach(_ ! msg)

  }
}