package com.sample.chat.model

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.scaladsl.Framing
import akka.stream.{IOResult, Materializer, SystemMaterializer}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object ChatHistory {

  // 160 characters is the fb messenger's maximum limit,
  // if text exceeds to the limit stream will fail
  // TODO: Check how to catch limit when reading per line
  // TODO: Add safe checking of path

  def loadHistoryFrom(chatRoom: String)(implicit materializer: Materializer): Source[String, Future[IOResult]] = {
    println("loading from history...")
    val path = Paths.get(s"src/main/resources/db/$chatRoom.txt")
    FileIO.fromPath(path)
      .via(Framing.delimiter(ByteString(System.lineSeparator()), 160, true))
      .map(_.utf8String)
  }

}
