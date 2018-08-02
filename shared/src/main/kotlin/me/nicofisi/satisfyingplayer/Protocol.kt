@file:Suppress("unused")

package me.nicofisi.satisfyingplayer

import com.beust.klaxon.Json

const val SERVER_PORT = 34857


abstract class Message(val name: String)

interface ClientMessage

interface ServerMessage


class PingMessage : Message("ping"), ClientMessage, ServerMessage



class ClientPauseMessage(
        val movieTime: Long
) : Message("pause"), ClientMessage

class ClientContinueMessage(
        val movieTime: Long,
        val timeMillis: Long
) : Message("continue"), ClientMessage


class ClientTimeChangeMessage(
        val movieTime: Long,
        val timeMillis: Long
)



class ServerPauseMessage(
        val movieTime: Long,
        @Json(name = "by_user")
        val byUser: String
) : Message("pause"), ServerMessage

class ServerContinueMessage(
        val movieTime: Long,
        val timeMillis: Long,
        @Json(name = "by_user")
        val byUser: String
) : Message("continue"), ServerMessage

class ServerTimeChangeMessage(
        val movieTime: Long,
        val timeMillis: Long,
        @Json(name = "by_user")
        val byUser: String
)