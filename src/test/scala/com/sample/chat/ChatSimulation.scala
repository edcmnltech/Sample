package com.sample.chat

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class ChatSimulation extends Simulation {

  val scn = scenario("sendMessage")
    .exec(ws("").connect("ws://localhost:8080/chatid/1/chatname/chat1/nickname/eli"))
    .exec(ws("").sendText("sample message"))

  setUp(
    scn.inject(
      atOnceUsers(1000)
    )
  )
}
