package com.sample.chat.repository.table

import akka.actor.ActorRef
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{ProvenShape, Tag}
import Implicits._

class ChatRoomId(val value: Int) extends AnyVal
class ChatRoomName(val value: String) extends AnyVal
final case class ChatRoom(name: ChatRoomName, creator: String, id: ChatRoomId = new ChatRoomId(0))
final case class ChatRoomActorRef(actorRef: ActorRef, meta: ChatRoom)

class ChatRoomTable(tag: Tag) extends Table[ChatRoom](tag, "chatroom"){

  def id: Rep[ChatRoomId] = column[ChatRoomId]("id", O.AutoInc)
  def name: Rep[ChatRoomName] = column[ChatRoomName]("name", O.PrimaryKey)
  def creator: Rep[String] = column[String]("creator", O.PrimaryKey)

  def * : ProvenShape[ChatRoom] = (name, creator, id).mapTo[ChatRoom]

}