package com.sample.server

import java.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

final case class Message(username: String, text: String)

trait MessageRepository {
  def append(message: Message, chatRoom: ChatRoom): Unit

  def get(chatRoom: ChatRoom): List[String]
}

class TextFileRepository(implicit val ec: ExecutionContextExecutor) extends MessageRepository {

  override def append(message: Message, chatRoom: ChatRoom): Unit = {
    //temp
    println(s"${message.username} sent a chat")
    val file = new File(s"src/db/chatrooms/${chatRoom.name}.txt")
    val bw = new BufferedWriter(new FileWriter(file, true))

    bw.write(s"${message.username}: ${message.text}\n")
    bw.close()
  }

  override def get(chatRoom: ChatRoom): List[String] = {
    val source = scala.io.Source.fromFile(s"src/db/chatrooms/${chatRoom.name}.txt")
    source.getLines().toList
  }
}
