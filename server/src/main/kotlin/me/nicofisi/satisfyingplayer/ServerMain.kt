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

data class ClientInfo(val socket: Socket, val sendExecutor: Executor, var lastPing: Long)

fun sendToClient(client: ClientInfo, serverMessage: ServerMessage) {
    val pw = PrintWriter(client.socket.getOutputStream())

    client.sendExecutor.execute {
        pw.write(Klaxon().toJsonString(serverMessage))
        pw.write("\n")
        pw.flush()
    }
}

fun main(args: Array<String>) {
    val serverSocket = ServerSocket(SERVER_PORT)

    var sockets = emptyList<ClientInfo>()

    val timer = Timer()

    timer.scheduleAtFixedRate(timerTask {
        sockets.forEach { clientInfo ->
            val (socket, sendExecutor, lastPing) = clientInfo

            if (System.currentTimeMillis() > lastPing + 15000) {
                println("Closing the connection from " + socket.toString() + " after 15 seconds of no keep alive messages")
                socket.close()
                sockets -= clientInfo
            } else {
                sendExecutor.execute {
                    sendToClient(clientInfo, PingMessage())
                }
            }
        }
    }, 5000, 5000)

    while (true) {
        val socket = serverSocket.accept()

        val sendExecutor = Executors.newSingleThreadExecutor()
        val clientInfo = ClientInfo(socket, sendExecutor, System.currentTimeMillis())
        sockets += clientInfo

        Thread {
            try {

                sendToClient(clientInfo, PingMessage())

                val br = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (true) {
                    val jsonLine = br.readLine()
                    val jsonObj = Parser().parse(StringBuilder(jsonLine)) as JsonObject

                    when (jsonObj["name"]) {
                        "ping" -> clientInfo.lastPing = System.currentTimeMillis()
                        "pause" -> {
                            val message = Klaxon().parse<ClientPauseMessage>(jsonLine)!!
                            sockets.forEach {
                                sendToClient(it, ServerPauseMessage(message.movieTime, socket.toString()))
                            }
                        }
                        "continue" -> {
                            val message = Klaxon().parse<ClientContinueMessage>(jsonLine)!!
                            sockets.forEach {
                                sendToClient(it, ServerContinueMessage(message.movieTime, message.timeMillis, socket.toString()))
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                println("Closing the connection to " + socket.toString()
                        + " because of " + ex.javaClass.canonicalName + ": " + ex.message)
            }
        }.start()
    }
}