package com.sample.server

import java.io.File

final case class ChatRoom(name: String)

trait ChatRoomRepository {
  def getAll(): List[ChatRoom]
}

class TextFileChatRoomRepository extends ChatRoomRepository {
  override def getAll(): List[ChatRoom] = {
    val chatRoomsDir = new File("src/db/chatrooms")
    chatRoomsDir.list()

    chatRoomsDir.list().toList.map { file =>
      ChatRoom(file.stripSuffix(".txt"))
    }
  }
}