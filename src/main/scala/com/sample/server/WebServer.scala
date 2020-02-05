package com.sample.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContextExecutor

object WebServer {
  def main(args: Array[String]): Unit = {

    implicit val actorSystem = ActorSystem()
    implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
    implicit val eventStream = actorSystem.eventStream

    val chatRepo = new TextFileRepository()
    val chatRoomRepo = new TextFileChatRoomRepository()

    val route =
//      post {
//        concat(
//          path("client" / Segment) { username =>
//            entity(as[Message]) { message =>
//              chatRepo.append(message)
//              complete(s"OK ${message.username}")
//            }
//          }
//        )
//      } ~
      get {
        concat(
          path("chatrooms") {
            complete(chatRoomRepo.getAll())
          }
        )
      } ~
      post {
        concat(
          path("chatroom" / Segment / Segment) { (chatRoom, nickname) =>
            entity(as[Message]) { msg =>
              val message = msg.copy(username = nickname)
              chatRepo.append(message, ChatRoom(chatRoom))
              complete(s"OK ${message.username}")
            }
          }
        )
      } ~
      get {
        concat(
          path("chatroom" / Segment) { name =>
            println(s"get all chat for $name")
            complete(chatRepo.get(ChatRoom(name)))
          }
        )
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    // terminate the server
    // bindingFuture.flatMap(_.unbind()).onComplete(_ => actorSystem.terminate())
  }

}