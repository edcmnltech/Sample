package com.sample.chat.repository

import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.MySQLProfile.api._

object Implicits {
//    FIXME: OffsetDateTime is not converted to long when stored in the database
//    private val long2OffsetDateTime: Long => OffsetDateTime = epochSecond => OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault())
//    private val offsetDateTime2Long: OffsetDateTime => Long = _.toEpochSecond
//    implicit val offsetDateTime2UnixTimeStamp: BaseColumnType[OffsetDateTime] = MappedColumnType.base[OffsetDateTime, Long](offsetDateTime2Long, long2OffsetDateTime) //https://github.com/slick/slick/issues/1987

    implicit val idMapper: JdbcType[ChatRoomId] with BaseTypedType[ChatRoomId] = MappedColumnType.base[ChatRoomId, Int](_.value, new ChatRoomId(_))
    implicit val chatNameMapper: JdbcType[ChatRoomName] with BaseTypedType[ChatRoomName] = MappedColumnType.base[ChatRoomName, String](_.value, new ChatRoomName(_))
    implicit val chatUserMapper: JdbcType[ChatUserName] with BaseTypedType[ChatUserName] = MappedColumnType.base[ChatUserName, String](_.value, new ChatUserName(_))
    implicit val chatPasswordMapper: JdbcType[ChatRoomPassword] with BaseTypedType[ChatRoomPassword] = MappedColumnType.base[ChatRoomPassword, String](_.value, new ChatRoomPassword(_))
}
