package com.sample.chat.repository

import slick.jdbc.MySQLProfile.api._
import slick.lifted.{ProvenShape, TableQuery}

import scala.concurrent.{ExecutionContext, Future}

final case class VerifyChatRoomCreator(userName: ChatUserName, roomName: ChatRoomName, password: ChatRoomPassword)

trait ChatUserRepository extends ChatUserTable { this: MySqlRepository =>

  import Implicits._

  sealed abstract class ChatUserRepositoryException(msg: String) extends Exception(msg)
  class NoSuchUserException(username: ChatUserName) extends ChatUserRepositoryException(s"No user with username: ${username.value} found.")

  def selectByUsername(username: ChatUserName)(implicit _ec: ExecutionContext = ec): Future[ChatUser] = {
    val query = chatUserTable.filter(_.username === username).result.headOption
    db.run(query).flatMap {
      case Some(value) => Future.successful(value)
      case None        => Future.failed(throw new NoSuchUserException(username))}
  }

  def insert(chatUser: ChatUser): Future[ChatRoomId] = {
    val query = chatUserTable returning chatUserTable.map(_.id) += chatUser
    db.run(query)
  }

}

class ChatUserName(val value: String) extends AnyVal
final class ChatUserId(val value: String) extends AnyVal
final case class ChatUser(id: ChatRoomId, username: ChatUserName)

private[repository] trait ChatUserTable { this: MySqlRepository =>

  import Implicits._

  private[ChatUserTable] class ChatUserTable(tag: Tag) extends Table[ChatUser](tag, "chatuser"){
    def id: Rep[ChatRoomId] = column[ChatRoomId]("id", O.AutoInc)
    def username: Rep[ChatUserName] = column[ChatUserName]("name", O.PrimaryKey)
    def * : ProvenShape[ChatUser] = (id, username).mapTo[ChatUser]
  }

  val chatUserTable = TableQuery[ChatUserTable]

}