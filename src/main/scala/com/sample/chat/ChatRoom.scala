package com.sample.chat

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.stream.CompletionStrategy
import com.sample.chat.ChatRoom.{ChatMessage, Join}
import com.sample.chat.repository.table.ChatUserName

object ChatRoom {
  final case object Join
  final case class ChatMessage(message: String, sender: ChatUserName)

  def props(): Props = Props(classOf[ChatRoom])
}

class ChatRoom extends Actor with ActorLogging {
  var users: Set[ActorRef] = Set.empty

  def receive: Receive = {
    case Join =>
      log.info("User joined")
      users += sender()
      context.watch(sender())
    case Terminated(user) =>
      log.info(s"User terminated $user")
      users -= user
    case msg: ChatMessage =>
      log.info("Sending messages to all users in chatroom")
      users.foreach(_ ! msg)
    case _: CompletionStrategy =>
      self ! PoisonPill
  }
}