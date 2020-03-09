package com.sample.chat.repository

import akka.actor.Actor
import com.sample.chat.repository.table.Implicits._
import com.sample.chat.repository.table._
import shapeless.Succ
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.impl.Promise
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

object ChatRoomRepository extends MySqlRepository {

  sealed abstract class ChatRoomRepositoryException(msg: String) extends Exception(msg)
  class NoSuchChatRoomException(name: ChatRoomName) extends ChatRoomRepositoryException(s"No chat room with name: ${name.value} found.")
  class UserCannotJoinRoomException(username: ChatUserName, room: ChatRoomName) extends ChatRoomRepositoryException(s"User with username: ${username.value} cannot join room: ${room.value}.")

  val chatRoomTable = TableQuery[ChatRoomTable]

  def selectAll: Future[Seq[ChatRoom]] = {db.run(chatRoomTable.result)}

  def selectByName(name: ChatRoomName): Future[ChatRoom] = {
    val query = chatRoomTable.filter(_.name === name).result.headOption

    db.run(query) flatMap {
      case Some(room) => Future.successful(room)
      case None       => Future.failed(throw new NoSuchChatRoomException(name))}
  }

  def checkIfValidUser(username: ChatUserName, name: ChatRoomName, password: Option[ChatRoomPassword]): Future[Boolean] = {
    val basicQuery = chatRoomTable.filter(r => r.creator === username && r.name === name)
    val query = password match {
      case None       => basicQuery
      case Some(pass) => basicQuery ++ chatRoomTable.filter(r => r.password === pass && r.name === name)}

    db.run(query.result.headOption) flatMap {
      case Some(_) => Future.successful(true)
      case None    => Future.failed(throw new UserCannotJoinRoomException(username, name))}
  }

  def insert(chatRoom: ChatRoom): Future[Int] = {
    val query = chatRoomTable += chatRoom
    db.run(query)
  }

}