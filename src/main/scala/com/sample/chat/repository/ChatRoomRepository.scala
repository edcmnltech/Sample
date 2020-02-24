package com.sample.chat.repository

import com.sample.chat.repository.table.Implicits._
import com.sample.chat.repository.table._
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

object ChatRoomRepository extends MySqlRepository {

  val chatRoomTable = TableQuery[ChatRoomTable]

  def selectAll: Future[Seq[ChatRoom]] = db.run(chatRoomTable.result)

  def selectByName(name: ChatRoomName)(implicit ec: ExecutionContext): Future[ChatRoom] = {
    val query = chatRoomTable.filter(_.name === name).result.headOption
    db.run(query) flatMap {
      case Some(room) => Future.successful(room)
      case None => Future.failed(throw new SlickException(s"No chat room with name: ${name.value} found."))
    }
  }

  def checkIfPasswordMatch(name: ChatRoomName, password: ChatRoomPassword)(implicit ec: ExecutionContext): Future[Boolean] = {
    val query = chatRoomTable.filter(_.password === password).filter(_.name === name).result.headOption
    db.run(query) flatMap {
      case Some(_) => Future.successful(true)
      case None => Future.failed(throw new SlickException(s"Incorrect password!"))
    }
  }

  def checkIfValidUser(userName: ChatUserName, name: ChatRoomName, password: Option[ChatRoomPassword])(implicit ec: ExecutionContext): Future[Boolean] = {
    val basicQuery = chatRoomTable.filter(r => r.creator === userName && r.name === name)
    val query = password match {
      case None => basicQuery
      case Some(pass) => basicQuery ++ chatRoomTable.filter(r => r.password === pass && r.name === name)
    }
    db.run(query.result.headOption) flatMap {
      case Some(_) => Future.successful(true)
      case None => Future.successful(false)
    }
  }

  def insert(chatRoom: ChatRoom): Future[Int] = {
    val query = chatRoomTable += chatRoom
    db.run(query)
  }

}
