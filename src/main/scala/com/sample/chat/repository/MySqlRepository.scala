package com.sample.chat.repository

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import slick.jdbc.MySQLProfile.backend.Database

import scala.concurrent.ExecutionContext

trait MySqlRepository {

  val schema = "deebee"

  val db = Database.forConfig(schema)

  implicit val ec: ExecutionContext = ExecutionContext.global
//  implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")

}
