package com.sample.chat

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill}
import akka.stream.CompletionStrategy
import com.sample.chat.User.{Connected, IncomingMessage, OutgoingMessage}
import com.sample.chat.repository.table.{ChatRoomActorRef, ChatUserName}

import scala.concurrent.{ExecutionContext, Future}

object User {
  final case class Connected(outgoing: ActorRef)
  final case class IncomingMessage(text: String, sender: ChatUserName)
  final case class OutgoingMessage(text: String, sender: ChatUserName)
}

class User(chatRoom: ChatRoomActorRef, userName: ChatUserName)(implicit ec: ExecutionContext) extends Actor with ActorLogging {


  def receive: Receive = {
    case Connected(outgoing) =>
      log.info(s"User connected: ${userName.value}")
      context.become(connected(outgoing))
      chatRoom.actorRef ! ChatRoom.Join
  }

  def connected(outgoing: ActorRef): Receive = {
    case IncomingMessage(text, sender) =>
      log.info(s"Msg in <- ${chatRoom.actorRef}")
      chatRoom.actorRef ! ChatRoom.ChatMessage(text, sender)
    case a: Future[Seq[IncomingMessage]] =>
      a.map{ xxx =>
        xxx.map { msg =>
          log.info(s"Msg in <- ${chatRoom.actorRef}")
          chatRoom.actorRef ! ChatRoom.ChatMessage(msg.text, msg.sender)
        }
      }
    case ChatRoom.ChatMessage(text, sender) =>
      log.info(s"Msg out -> $outgoing")
      outgoing ! OutgoingMessage(text, sender)
    case _: CompletionStrategy =>
      self ! PoisonPill
    case wth =>
      log.error(s"ERROR with: $wth")
  }

}
