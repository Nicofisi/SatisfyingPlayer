@file:Suppress("unused")

package me.nicofisi.satisfyingplayer

const val SERVER_PORT = 34857

const val PROTOCOL_VERSION = 4


abstract class Message(val name: String)

interface ClientMessage

interface ServerMessage



class ClientPingMessage(
        val videoChecksum: String? = null,
        val timeMillis: Long
) : Message("ping"), ClientMessage

class ClientPauseMessage(
        val videoChecksum: String,
        val movieTime: Long
) : Message("pause"), ClientMessage

class ClientContinueMessage(
        val videoChecksum: String,
        val movieTime: Long,
        val timeMillis: Long
) : Message("continue"), ClientMessage


class ClientTimeChangeMessage(
        val videoChecksum: String,
        val movieTime: Long,
        val timeMillis: Long,
        val isPaused: Boolean
) : Message("time_change"), ClientMessage

class ClientPlaybackStatusRequestMessage(
        val videoChecksum: String
) : Message("playback_status_request"), ClientMessage



class ServerPingMessage(
        val timeMillis: Long,
        val peopleConnected: Int

) : Message("ping"), ServerMessage

class ServerPauseMessage(
        val movieTime: Long,
        val byUser: String
) : Message("pause"), ServerMessage

class ServerContinueMessage(
        val movieTime: Long,
        val timeMillis: Long,
        val byUser: String
) : Message("continue"), ServerMessage

class ServerTimeChangeMessage(
        val movieTime: Long,
        val timeMillis: Long,
        val byUser: String,
        val isPaused: Boolean
) : Message("time_change"), ServerMessage

class ServerPlaybackStatusMessage(
        val movieTime: Long? = null,
        val timeMillis: Long? = null,
        val isPaused: Boolean? = null
) : Message("playback_status"), ServerMessage