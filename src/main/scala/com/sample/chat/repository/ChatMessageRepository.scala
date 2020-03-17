package com.sample.chat.repository

import java.time.OffsetDateTime

import com.sample.chat.User.IncomingMessage
import slick.basic.DatabasePublisher
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{ProvenShape, TableQuery}

import scala.concurrent.{ExecutionContext, Future}

trait ChatMessageRepository extends ChatMessageTable { this: MySqlRepository =>

  import Implicits._

  def selectByRoomId(roomId: ChatRoomId): DatabasePublisher[ChatMessage] = {
    val query = chatMessageTable.filter(_.roomId === roomId)
    db.stream(query.result)
  }

  def insert(chatMessage: ChatMessage)(implicit _ec: ExecutionContext = ec): Future[ChatMessage] = {
    val query = chatMessageTable returning chatMessageTable.map(_.id) += chatMessage
    db.run(query).map(_ => chatMessage)
  }

  def insertBulk(roomId: ChatRoomId)(chatMessages: Future[Seq[IncomingMessage]])(implicit _ec: ExecutionContext = ec): Future[Option[Int]] = {
    chatMessages.flatMap { msg =>
      val msgs = msg.map(i => ChatMessage(i.sender.value, i.text,  roomId))
      db.run(chatMessageTable ++= msgs)
    }
  }

}

final case class ChatMessage(sender: String, message: String, roomId: ChatRoomId, timeSent: Long = OffsetDateTime.now().toEpochSecond, id: Long = 0L)

private[repository] trait ChatMessageTable { this: MySqlRepository =>

  import Implicits._

  private[ChatMessageTable] class ChatMessageTable(tag: Tag) extends Table[ChatMessage](tag, "chatmessage") {

    def sender: Rep[String] = column[String]("sender")
    def message: Rep[String] = column[String]("message")
    def roomId: Rep[ChatRoomId] = column[ChatRoomId]("roomId")
    def timeSent: Rep[Long] = column[Long]("timeSent")
    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def * : ProvenShape[ChatMessage] = (sender, message, roomId, timeSent, id).mapTo[ChatMessage]
  }

  protected val chatMessageTable = TableQuery[ChatMessageTable]
}
