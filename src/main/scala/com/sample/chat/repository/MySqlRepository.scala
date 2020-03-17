package com.sample.chat.repository

import java.util.concurrent.Executors

import slick.jdbc.MySQLProfile.backend.Database

import scala.concurrent.ExecutionContext

trait MySqlRepository {

  object DatabaseExecutionContext {
    private val processors = Runtime.getRuntime.availableProcessors()
    val noOfThread: Int = processors * 2
  }

  implicit val ioThreadPool: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(DatabaseExecutionContext.noOfThread))

  val schema = "deebee"

  val db = Database.forConfig(schema)
}
