package me.nicofisi.satisfyingplayer

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.concurrent.timerTask

data class ClientInfo(val socket: Socket, val sendExecutor: Executor, var lastPing: Long, var videoChecksum: String? = null)

fun sendToClient(client: ClientInfo, serverMessage: ServerMessage) {
    val pw = PrintWriter(client.socket.getOutputStream())

    client.sendExecutor.execute {
        pw.write(Klaxon().toJsonString(serverMessage))
        pw.write("\n")
        pw.flush()
    }
}

data class MovieInfo(val videoChecksum: String, var lastUpdateMovieTime: Long, var lastUpdateMillis: Long, var isPaused: Boolean)

var movieInfos = emptyList<MovieInfo>()

fun main(args: Array<String>) {
    val serverSocket = ServerSocket(SERVER_PORT)

    var sockets = emptyList<ClientInfo>()

    val timer = Timer()

    timer.scheduleAtFixedRate(timerTask {
        sockets.forEach { clientInfo ->
            val (socket, sendExecutor, lastPing) = clientInfo

            if (System.currentTimeMillis() > lastPing + 15000) {
                println("Closing the connection to " + socket.toString() + " after 15 seconds of no keep alive messages")
                socket.close()
                sockets -= clientInfo
            } else {
                sendExecutor.execute {
                    sendToClient(clientInfo, ServerPingMessage(System.currentTimeMillis()))
                }
            }
        }

        movieInfos.forEach { info ->
            if (sockets.none { it.videoChecksum == info.videoChecksum}) {
                movieInfos -= info
            }
        }
    }, 5000, 5000)

    while (true) {
        val socket = serverSocket.accept()
        println("Handling new connection from " + socket.toString())

        val sendExecutor = Executors.newSingleThreadExecutor()
        val clientInfo = ClientInfo(socket, sendExecutor, System.currentTimeMillis())
        sockets += clientInfo

        Thread {
            try {
                sendToClient(clientInfo, ServerPingMessage(System.currentTimeMillis()))

                val br = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (true) {
                    val jsonLine = br.readLine()
                    val jsonObj = Parser.default().parse(StringBuilder(jsonLine)) as JsonObject

                    println("$socket | $jsonObj")

                    when (jsonObj["name"]) {
                        "ping" -> {
                            val message = Klaxon().parse<ClientPingMessage>(jsonLine)!!
                            clientInfo.videoChecksum = message.videoChecksum
                            clientInfo.lastPing = System.currentTimeMillis()
                        }
                        "pause" -> {
                            val message = Klaxon().parse<ClientPauseMessage>(jsonLine)!!
                            println("$socket paused ${message.videoChecksum} at movie time " + message.movieTime)
                            var info = movieInfos.find { it.videoChecksum == message.videoChecksum }
                            if (info == null) {
                                info = MovieInfo(message.videoChecksum, message.movieTime, System.currentTimeMillis(), true)
                                movieInfos += info
                            } else {
                                info.lastUpdateMillis = System.currentTimeMillis()
                                info.lastUpdateMovieTime = message.movieTime
                                info.isPaused = true
                            }
                            sockets.filter { it.videoChecksum == message.videoChecksum }.forEach {
                                sendToClient(it, ServerPauseMessage(message.movieTime, socket.toString()))
                            }
                        }
                        "continue" -> {
                            val message = Klaxon().parse<ClientContinueMessage>(jsonLine)!!
                            println("$socket unpaused ${message.videoChecksum} at movie time " + message.movieTime)
                            var info = movieInfos.find { it.videoChecksum == message.videoChecksum }
                            if (info == null) {
                                info = MovieInfo(message.videoChecksum, message.movieTime, message.timeMillis, false)
                                movieInfos += info
                            } else {
                                info.lastUpdateMillis = message.timeMillis
                                info.lastUpdateMovieTime = message.movieTime
                                info.isPaused = false
                            }
                            sockets.filter { it.videoChecksum == message.videoChecksum }.forEach {
                                sendToClient(it, ServerContinueMessage(message.movieTime, message.timeMillis, socket.toString()))
                            }
                        }
                        "time_change" -> {
                            val message = Klaxon().parse<ClientTimeChangeMessage>(jsonLine)!!
                            println("$socket changed the time of ${message.videoChecksum} to " + message.movieTime)
                            var info = movieInfos.find { it.videoChecksum == message.videoChecksum }
                            if (info == null) {
                                info = MovieInfo(message.videoChecksum, message.movieTime, System.currentTimeMillis(), message.isPaused)
                                movieInfos += info
                            } else {
                                info.isPaused = message.isPaused
                                info.lastUpdateMillis = message.timeMillis
                                info.lastUpdateMovieTime = message.movieTime
                            }
                            sockets.filter { it.videoChecksum == message.videoChecksum }.forEach {
                                sendToClient(it, ServerTimeChangeMessage(
                                        message.movieTime, message.timeMillis, socket.toString(), message.isPaused))
                            }
                        }
                        "playback_status_request" -> {
                            val message = Klaxon().parse<ClientPlaybackStatusRequestMessage>(jsonLine)!!
                            val info = movieInfos.find { it.videoChecksum == message.videoChecksum }
                            sendToClient(clientInfo,
                                    if (info == null) ServerPlaybackStatusMessage(null, null, null)
                                            else ServerPlaybackStatusMessage(
                                            info.lastUpdateMovieTime + (System.currentTimeMillis() - info.lastUpdateMillis),
                                            System.currentTimeMillis(),
                                            info.isPaused
                                    )
                            )
                        }
                    }
                }
            } catch (ex: Exception) {
                sockets -= clientInfo
                println("Closing the connection to " + socket.toString()
                        + " because of " + ex.javaClass.canonicalName + ": " + ex.message)
                socket.close()
            }
        }.start()
    }
}