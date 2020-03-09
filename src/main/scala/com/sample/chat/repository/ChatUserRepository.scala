package com.sample.chat.repository

import com.sample.chat.repository.table.Implicits._
import com.sample.chat.repository.table.{ChatMessage, ChatMessageTable, ChatRoomId, ChatUser, ChatUserName, ChatUserTable}
import slick.basic.DatabasePublisher
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

object ChatUserRepository extends MySqlRepository {

  sealed abstract class ChatUserRepositoryException(msg: String) extends Exception(msg)
  class NoSuchUserException(username: ChatUserName) extends ChatUserRepositoryException(s"No user with username: ${username.value} found.")

  val chatUserTable = TableQuery[ChatUserTable]

  def selectByName(username: ChatUserName): Future[ChatUser] = {
    val query = chatUserTable.filter(_.username === username).result.headOption
    db.run(query).flatMap {
      case Some(value) => Future.successful(value)
      case None        => Future.failed(throw new NoSuchUserException(username))}
  }

  def insert(chatUser: ChatUser): Future[Int] = {
    val query = chatUserTable returning chatUserTable.map(_.id) += chatUser
    db.run(query)
  }

}
