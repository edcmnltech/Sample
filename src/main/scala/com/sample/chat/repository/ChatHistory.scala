package com.sample.chat.repository

import java.nio.file.{Paths, StandardOpenOption}

import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import com.sample.chat.ChatRoom.ChatRoomName
import slick.lifted.{Query, TableQuery}
import slick.dbio.DBIOAction._
import slick.jdbc.MySQLProfile._

import scala.concurrent.{ExecutionContext, Future}

object ChatHistory {

  // 160 characters is the fb messenger's maximum limit,
  // if text exceeds to the limit stream will fail
  // TODO: Check how to catch limit when reading per line
  // TODO: Add safe checking of path

  def unload(chatRoom: ChatRoomName)(implicit mat: Materializer): Source[String, Future[IOResult]] = {
    println("loading from history...")
    val path = Paths.get(s"src/main/resources/db/${chatRoom.value}.txt")
    FileIO.fromPath(path)
      .via(Framing.delimiter(ByteString("\n"), 160, true))
      .map(_.utf8String)
  }

  def load(chatRoom: ChatRoomName)(implicit mat: Materializer): Sink[ByteString, Future[IOResult]] = {
    println("saving to history...")
    val path = Paths.get(s"src/main/resources/db/${chatRoom.value}.txt")
    FileIO.toPath(path, Set(StandardOpenOption.WRITE, StandardOpenOption.APPEND))
  }

}
