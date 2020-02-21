package com.sample.chat.repository.table

import akka.actor.ActorRef
import com.sample.chat.repository.table.Implicits._
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{ProvenShape, Tag}

class ChatRoomId(val value: Int) extends AnyVal
class ChatRoomName(val value: String) extends AnyVal
class ChatRoomPassword(val value: String) extends AnyVal
final case class ChatRoom(name: ChatRoomName, creator: ChatUserName, password: ChatRoomPassword, id: ChatRoomId = new ChatRoomId(0))
final case class ChatRoomActorRef(actorRef: ActorRef, meta: ChatRoom)

class ChatRoomTable(tag: Tag) extends Table[ChatRoom](tag, "chatroom"){

  def id: Rep[ChatRoomId] = column[ChatRoomId]("id", O.AutoInc)
  def name: Rep[ChatRoomName] = column[ChatRoomName]("name", O.PrimaryKey)
  def password: Rep[ChatRoomPassword] = column[ChatRoomPassword]("password")
  def creator: Rep[ChatUserName] = column[ChatUserName]("creator", O.PrimaryKey)

  def * : ProvenShape[ChatRoom] = (name, creator, password, id).mapTo[ChatRoom]

}