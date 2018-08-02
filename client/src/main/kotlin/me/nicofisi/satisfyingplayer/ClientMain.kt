package me.nicofisi.satisfyingplayer

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.cyanogenmod.updater.utils.MD5
import java.awt.BorderLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Timer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.swing.*
import kotlin.concurrent.timerTask


const val SERVER_IP = "127.0.0.1"

lateinit var socket: Socket

lateinit var sendExecutor: Executor

fun sendToServer(clientMessage: ClientMessage) {
    sendExecutor.execute {
        val pw = PrintWriter(socket.getOutputStream())
        pw.write(Klaxon().toJsonString(clientMessage))
        pw.write("\n")
        pw.flush()
    }
}

var currentVideoChecksum: String? = null

fun main(args: Array<String>) {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())


    val lastPlayedStore = Paths.get(System.getenv("LOCALAPPDATA"), "SatisfyingPlayer", "last-played")

    val frame = JFrame("SatisfyingPlayer")
    frame.setBounds(100, 100, 600, 400)

    val contentPane = JPanel()
    contentPane.layout = BorderLayout()

    val timer = Timer()

    val serverStatus = JLabel("Connecting to the server")

    val dotChangingTask = timerTask {
        SwingUtilities.invokeLater {
            val dotAmount = serverStatus.text.count { it == '.' }
            serverStatus.text = if (dotAmount < 3) (serverStatus.text + ".") else serverStatus.text.replace(".", "")
        }
    }

    timer.scheduleAtFixedRate(dotChangingTask, 250, 250)

    contentPane.add(serverStatus, BorderLayout.NORTH)

    val controlsPane = JPanel()

    contentPane.add(controlsPane, BorderLayout.SOUTH)

    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane = contentPane
    frame.isVisible = true

    val fileChooser = JFileChooser({
        val filePath = if (Files.exists(lastPlayedStore)) lastPlayedStore.toFile().readText().trim() else ""
        if (filePath.isEmpty()) System.getProperty("user.home") else filePath
    }.invoke())

    fileChooser.addActionListener {
        if (it.actionCommand == "ApproveSelection") {
            if (!Files.exists(lastPlayedStore)) {
                Files.createDirectory(lastPlayedStore.parent)
                Files.createFile(lastPlayedStore)
            }
            lastPlayedStore.toFile().writeText(fileChooser.selectedFile.absolutePath)
            currentVideoChecksum = MD5.calculate1MBMD5(fileChooser.selectedFile) + "-" + fileChooser.selectedFile.length()
            PlayerFrame.openAndPlay(fileChooser.selectedFile)
        }
    }

    contentPane.add(fileChooser, BorderLayout.CENTER)

    Thread {
        try {
            socket = Socket(SERVER_IP, SERVER_PORT)
            sendExecutor = Executors.newSingleThreadExecutor()

            while (true) {
                val br = BufferedReader(InputStreamReader(socket.getInputStream()))

                val jsonLine = br.readLine()

                val jsonObj = Parser().parse(StringBuilder(jsonLine)) as JsonObject

                when (jsonObj["name"]) {
                    "ping" -> sendToServer(ClientPingMessage(currentVideoChecksum, System.currentTimeMillis()))
                    "pause" -> {
                        val message = Klaxon().parse<ServerPauseMessage>(jsonLine)!!
                        PlayerFrame.lastStatus = "paused by " + message.byUser
                        PlayerFrame.updateWindowTitle()
                        if (PlayerFrame.mediaPlayerComponent?.mediaPlayer?.isPlaying != false) {
                            PlayerFrame.mediaPlayerComponent?.mediaPlayer?.setPause(true)
                        }
                    }
                    "continue" -> {
                        val message = Klaxon().parse<ServerContinueMessage>(jsonLine)!!
                        PlayerFrame.lastStatus = "unpaused by " + message.byUser
                        PlayerFrame.updateWindowTitle()
                        if (PlayerFrame.mediaPlayerComponent?.mediaPlayer?.isPlaying != true) {
                            PlayerFrame.mediaPlayerComponent?.mediaPlayer?.time =
                                    (System.currentTimeMillis() - message.timeMillis) + message.movieTime
                            PlayerFrame.mediaPlayerComponent?.mediaPlayer?.setPause(false)
                        }
                    }
                    "time_change" -> {
                        val message = Klaxon().parse<ServerContinueMessage>(jsonLine)!!
                        PlayerFrame.lastStatus = "time changed by " + message.byUser
                        PlayerFrame.updateWindowTitle()
                        PlayerFrame.mediaPlayerComponent?.mediaPlayer?.time =
                                (System.currentTimeMillis() - message.timeMillis) + message.movieTime
                    }
                    "playback_status" -> {
                        val message = Klaxon().parse<ServerPlaybackStatusMessage>(jsonLine)!!
                        if (message.isPaused == null) {
                            PlayerFrame.lastStatus = "you are the first person watching this (unpause to start)"
                        } else {
                            PlayerFrame.mediaPlayerComponent?.mediaPlayer?.time =
                                    message.movieTime!! + (System.currentTimeMillis() - message.timeMillis!!)
                            PlayerFrame.mediaPlayerComponent?.mediaPlayer?.setPause(message.isPaused!!)
                        }
                    }
                }

                SwingUtilities.invokeLater {
                    dotChangingTask.cancel()
                    serverStatus.text = "Connected to the server"
                }
            }

        } catch (ex: Exception) {
            dotChangingTask.cancel()
            SwingUtilities.invokeLater {
                dotChangingTask.cancel()
                frame.toFront()
                serverStatus.text = "Server connection error: " + ex.message
            }
        }
    }.start()
}