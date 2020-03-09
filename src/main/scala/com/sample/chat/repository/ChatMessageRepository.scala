package com.sample.chat.repository

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.sample.chat.User.IncomingMessage
import com.sample.chat.repository.table.Implicits._
import com.sample.chat.repository.table.{ChatMessage, ChatMessageTable, ChatRoomId}
import slick.basic.DatabasePublisher
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}


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

//  def insertBulk(chatMessages: Future[Seq[ChatMessage]]): Future[Option[Int]] = {
//    chatMessages.flatMap { a =>
//       db.run(chatMessageTable ++= a)
//    }
//  }
//
  def insertBulk(chatMessages: Future[Seq[IncomingMessage]]): Future[Option[Int]] = {
    chatMessages.flatMap { a =>
      val aaa = a.map(i => ChatMessage("eli", i.text,  new ChatRoomId(1)))
      db.run(chatMessageTable ++= aaa)
    }
  }

}
