package com.sample.chat.model

import java.nio.file.{OpenOption, Paths, StandardOpenOption}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink, Source}
import akka.stream.{IOResult, Materializer, SystemMaterializer}
import akka.util.ByteString
import com.sample.chat.User.IncomingMessage

import scala.concurrent.{ExecutionContext, Future}

object ChatHistory {

  // 160 characters is the fb messenger's maximum limit,
  // if text exceeds to the limit stream will fail
  // TODO: Check how to catch limit when reading per line
  // TODO: Add safe checking of path

  def unload(chatRoom: String)(implicit materializer: Materializer): Source[String, Future[IOResult]] = {
    println("loading from history...")
    val path = Paths.get(s"src/main/resources/db/$chatRoom.txt")
    FileIO.fromPath(path)
      .via(Framing.delimiter(ByteString("\n"), 160, true))
      .map(_.utf8String)
  }

  def load(chatRoom: String)(implicit materializer: Materializer): Sink[ByteString, Future[IOResult]] = {
    println("saving to history...")
    val path = Paths.get(s"src/main/resources/db/$chatRoom.txt")
    FileIO.toPath(path, Set(StandardOpenOption.WRITE, StandardOpenOption.APPEND))
  }

}
