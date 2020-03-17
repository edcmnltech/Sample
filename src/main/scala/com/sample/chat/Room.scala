package com.sample.chat

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.stream.CompletionStrategy
import com.sample.chat.Room.{ChatMessage, Join}
import com.sample.chat.repository.ChatUserName

object Room {
  final case object Join
  final case class ChatMessage(message: String, sender: ChatUserName)

  def props(): Props = Props(classOf[Room])
}

class Room extends Actor with ActorLogging {
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