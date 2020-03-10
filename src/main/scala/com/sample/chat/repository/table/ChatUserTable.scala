package com.sample.chat.repository.table

import com.sample.chat.repository.table.Implicits._
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{ProvenShape, Tag}

class ChatUserName(val value: String) extends AnyVal
class ChatUserId(val value: String) extends AnyVal
final case class ChatUser(id: ChatRoomId, username: ChatUserName)

class ChatUserTable(tag: Tag) extends Table[ChatUser](tag, "chatuser"){

  def id: Rep[ChatRoomId] = column[ChatRoomId]("id", O.AutoInc)
  def username: Rep[ChatUserName] = column[ChatUserName]("name", O.PrimaryKey)

  def * : ProvenShape[ChatUser] = (id, username).mapTo[ChatUser]

}