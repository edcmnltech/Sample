package com.sample.chat.repository

import akka.actor.ActorRef
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{ProvenShape, TableQuery}

import scala.concurrent.{ExecutionContext, Future}

trait ChatRoomRepository extends ChatRoomTable { this: MySqlRepository =>

  import Implicits._

  sealed abstract class ChatRoomRepositoryException(msg: String) extends Exception(msg)
  class NoSuchChatRoomException(name: ChatRoomName) extends ChatRoomRepositoryException(s"No chat room with name: ${name.value} found.")
  class UserCannotJoinRoomException(username: ChatUserName, room: ChatRoomName) extends ChatRoomRepositoryException(s"User with username: ${username.value} cannot join room: ${room.value}.")

  def selectAll: Future[Seq[ChatRoom]] = {db.run(chatRoomTable.result)}

  def selectByRoomName(name: ChatRoomName)(implicit _ec: ExecutionContext = ec): Future[ChatRoom] = {
    val query = chatRoomTable.filter(_.name === name).result.headOption

    db.run(query) flatMap {
      case Some(room) => Future.successful(room)
      case None       => Future.failed(throw new NoSuchChatRoomException(name))}
  }

  def checkIfValidUser(username: ChatUserName, name: ChatRoomName, password: Option[ChatRoomPassword])(implicit _ec: ExecutionContext = ec): Future[Boolean] = {
    val basicQuery = chatRoomTable.filter(r => r.creator === username && r.name === name)
    val query = password match {
      case None       => basicQuery
      case Some(pass) => basicQuery ++ chatRoomTable.filter(r => r.password === pass && r.name === name)}

    db.run(query.result.headOption) flatMap {
      case Some(_) => Future.successful(true)
      case None    => Future.failed(throw new UserCannotJoinRoomException(username, name))}
  }

  def insertRoom(chatRoom: ChatRoom): Future[Int] = {
    val query = chatRoomTable += chatRoom
    db.run(query)
  }

}

final class ChatRoomId(val value: Int) extends AnyVal
final class ChatRoomName(val value: String) extends AnyVal
final class ChatRoomPassword(val value: String) extends AnyVal
final case class ChatRoom(name: ChatRoomName, creator: ChatUserName, password: ChatRoomPassword, id: ChatRoomId = new ChatRoomId(0))
final case class ChatRoomActorRef(actorRef: ActorRef, meta: ChatRoom)

private[repository] trait ChatRoomTable { this: MySqlRepository =>

  import Implicits._

  class ChatRoomTable(tag: Tag) extends Table[ChatRoom](tag, "chatroom"){
    def id: Rep[ChatRoomId] = column[ChatRoomId]("id", O.AutoInc)
    def name: Rep[ChatRoomName] = column[ChatRoomName]("name", O.PrimaryKey)
    def password: Rep[ChatRoomPassword] = column[ChatRoomPassword]("password")
    def creator: Rep[ChatUserName] = column[ChatUserName]("creator", O.PrimaryKey)
    def * : ProvenShape[ChatRoom] = (name, creator, password, id).mapTo[ChatRoom]
  }

  protected val chatRoomTable = TableQuery[ChatRoomTable]
}

