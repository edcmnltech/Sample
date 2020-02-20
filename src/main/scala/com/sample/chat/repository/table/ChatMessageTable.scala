package com.sample.chat.repository.table

import java.time.OffsetDateTime

import com.sample.chat.repository.table.Implicits._
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{ProvenShape, Tag}

final case class ChatMessage(sender: String, message: String, roomId: ChatRoomId, timeSent: Long = OffsetDateTime.now().toEpochSecond, id: Long = 0L)

class ChatMessageTable(tag: Tag) extends Table[ChatMessage](tag, "chatmessage"){

  def sender: Rep[String] = column[String]("sender")
  def message: Rep[String] = column[String]("message")
  def roomId: Rep[ChatRoomId] = column[ChatRoomId]("roomId")
  def timeSent: Rep[Long] = column[Long]("timeSent")
  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def * : ProvenShape[ChatMessage] = (sender, message, roomId, timeSent, id).mapTo[ChatMessage]

}