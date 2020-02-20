package com.sample.chat.repository

import com.sample.chat.repository.table.Implicits._
import com.sample.chat.repository.table.{ChatRoom, ChatRoomName, ChatRoomTable}
import slick.basic.DatabasePublisher
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

object ChatRoomRepository extends MySqlRepository {

  val chatRoomTable = TableQuery[ChatRoomTable]

  def selectAll: DatabasePublisher[ChatRoom] = {
    db.stream(chatRoomTable.result)
  }

  def selectByName(name: ChatRoomName)(implicit ec: ExecutionContext): Future[ChatRoom] = {
    val query = chatRoomTable.filter(_.name === name).result.headOption
    db.run(query) flatMap {
      case Some(room) => Future.successful(room)
      case None => Future.failed(throw new Exception("not exist"))
    }
  }

  def insert(chatRoom: ChatRoom): Future[Int] = {
    val query = chatRoomTable += chatRoom
    db.run(query)
  }

}
