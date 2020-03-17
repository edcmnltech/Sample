package com.sample.chat.repository

import com.sample.chat.User.IncomingMessage
import com.sample.chat.repository.table.Implicits._
import com.sample.chat.repository.table.{ChatMessage, ChatMessageTable, ChatRoomId, ChatUserName}
import slick.basic.DatabasePublisher
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.Future


object ChatMessageRepository extends MySqlRepository {

  val chatMessageTable = TableQuery[ChatMessageTable]

  def selectByRoomId(roomId: ChatRoomId): DatabasePublisher[ChatMessage] = {
    val query = chatMessageTable.filter(_.roomId === roomId)
    db.stream(query.result)
  }

  def insert(chatMessage: ChatMessage): Future[ChatMessage] = {
    val query = chatMessageTable returning chatMessageTable.map(_.id) += chatMessage
    db.run(query).map(_ => chatMessage)
  }

  def insertBulk(roomId: ChatRoomId)(chatMessages: Future[Seq[IncomingMessage]]): Future[Option[Int]] = {
    chatMessages.flatMap { msg =>
      val msgs = msg.map(i => ChatMessage(i.sender.value, i.text,  roomId))
      db.run(chatMessageTable ++= msgs)
    }
  }

}
