package com.sample.chat

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.stream.CompletionStrategy
import com.sample.chat.ChatRoom.{ChatMessage, Join}
import com.sample.chat.repository.table.{ChatRoomId, ChatRoomName}

object ChatRoom {
  final case object Join
  final case class ChatMessage(message: String)

  def props(): Props = Props(classOf[ChatRoom])
}

class ChatRoom extends Actor {
  var users: Set[ActorRef] = Set.empty

  def receive: Receive = {
    case Join =>
      println("user joined")
      users += sender()
      context.watch(sender())
    case Terminated(user) =>
      println(s"user terminated $user")
      users -= user
    case msg: ChatMessage =>
      println("sending messages to all users in chatroom")
      users.foreach(_ ! msg)
    case a: CompletionStrategy =>
      self ! PoisonPill
  }
}