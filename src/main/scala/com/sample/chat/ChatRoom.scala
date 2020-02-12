package com.sample.chat

import akka.actor.{Actor, ActorRef, DeadLetter, Props, Terminated}
import com.sample.chat.ChatRoom.{ChatMessage, Join}

object ChatRoom {
  final case object Join
  final case class ChatMessage(message: String)
  def props() = Props(classOf[ChatRoom])
}

class ChatRoom extends Actor {

  var users: Set[ActorRef] = Set.empty

  def receive: Receive = {
    case Join =>
      println("user joined")
      users += sender()
//      context.watch(sender())

    case Terminated(user) =>
      println("user terminated")
      users -= user

    case msg: ChatMessage =>
      println("sending messages to all users in chatroom")
      users.foreach(_ ! msg)

    case d: DeadLetter =>
      println(s"dead letter sender: ${d.sender} recipient: ${d.recipient} messsage: ${d.message}")

  }
}