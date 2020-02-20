package com.sample.chat.repository

import com.sample.chat.repository.table.Implicits._
import com.sample.chat.repository.table.{ChatMessage, ChatMessageTable, ChatRoomId}
import slick.basic.DatabasePublisher
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

object ChatMessageRepository extends MySqlRepository {

  val chatMessageTable = TableQuery[ChatMessageTable]

  def selectByRoomId(roomId: ChatRoomId)(implicit ec: ExecutionContext): DatabasePublisher[ChatMessage] = {
    val query = chatMessageTable.filter(_.roomId === roomId)
    db.stream(query.result)
  }

  def insert(chatMessage: ChatMessage)(implicit ec: ExecutionContext): Future[ChatMessage] = {
    val query = chatMessageTable returning chatMessageTable.map(_.id) += chatMessage
    db.run(query).map(_ => chatMessage)
  }

}
