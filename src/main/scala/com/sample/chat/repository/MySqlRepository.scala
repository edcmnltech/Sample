package com.sample.chat.repository

import slick.jdbc.MySQLProfile.backend.Database

trait MySqlRepository {

  val schema = "deebee"

  val db = Database.forConfig(schema)

}
