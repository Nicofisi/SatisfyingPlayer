package me.nicofisi.satisfyingplayer

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.cyanogenmod.updater.utils.MD5
import java.awt.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.Timer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.swing.*
import kotlin.concurrent.timerTask
import javax.swing.BoxLayout


const val SERVER_IP = "player.nicofi.si"

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

var isSuccessfullyConnectedToServer = false

var currentVideoChecksum: String? = null

object MenuFrame {
    private val lastPlayedStore = Paths.get(System.getenv("LOCALAPPDATA"), "SatisfyingPlayer", "last-played")!!

    lateinit var frame: JFrame

    lateinit var mainPanel: JPanel
    lateinit var northPanel: JPanel
    lateinit var centerPanel: JPanel
    lateinit var centerOfCenterPanel: JPanel
    lateinit var chooseFileButtonContainerPanel: JPanel
    lateinit var userInfoContainerPanel: JPanel
    lateinit var userInfoContainerContainerPanel: JPanel
    lateinit var southPanel: JPanel

    lateinit var serverStatusLabel: JLabel
    lateinit var fileUrlTextField: JTextField
    lateinit var chooseFileButton: JButton
    lateinit var fileChooser: JFileChooser
    lateinit var peopleConnectedLabel: JLabel
    lateinit var peopleWatchingThisLabel: JLabel
    lateinit var playReconnectButton: JButton

    lateinit var dotChangingTask: TimerTask

    private val timer = Timer()

    fun start() {
        var lastFileChooserSelectedFile = if (Files.exists(lastPlayedStore)) lastPlayedStore.toFile().readText().trim() else ""
        if (lastFileChooserSelectedFile.isEmpty()) {
            lastFileChooserSelectedFile = System.getProperty("user.home")
        }


        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

        frame = JFrame("SatisfyingPlayer")

        mainPanel = JPanel()
        northPanel = JPanel()
        centerPanel = JPanel()
        centerOfCenterPanel = JPanel()
        chooseFileButtonContainerPanel = JPanel()
        userInfoContainerPanel = JPanel()
        userInfoContainerContainerPanel = JPanel()
        southPanel = JPanel()

        mainPanel.layout = BorderLayout()
        centerPanel.layout = GridBagLayout()
        centerOfCenterPanel.layout = BoxLayout(centerOfCenterPanel, BoxLayout.Y_AXIS)
        chooseFileButtonContainerPanel.layout = GridBagLayout()
        userInfoContainerPanel.layout = GridBagLayout()
        userInfoContainerContainerPanel.layout = BoxLayout(userInfoContainerContainerPanel, BoxLayout.Y_AXIS)

        serverStatusLabel = JLabel("Connecting to the server")
        fileUrlTextField = JTextField(lastFileChooserSelectedFile, 70)
        chooseFileButton = JButton("Open file selector")
        fileChooser = JFileChooser(lastFileChooserSelectedFile)
        peopleConnectedLabel = JLabel("People connected: fetching")
        peopleWatchingThisLabel = JLabel("People currently watching this: fetching")
        playReconnectButton = JButton("Play")

        mainPanel.add(northPanel, BorderLayout.NORTH)
        mainPanel.add(centerPanel, BorderLayout.CENTER)
        mainPanel.add(southPanel, BorderLayout.SOUTH)
        northPanel.add(serverStatusLabel, BorderLayout.CENTER)
        centerPanel.add(centerOfCenterPanel, GridBagConstraints())
        centerOfCenterPanel.add(fileUrlTextField)
        centerOfCenterPanel.add(chooseFileButtonContainerPanel)
        centerOfCenterPanel.add(userInfoContainerPanel)
        userInfoContainerPanel.add(userInfoContainerContainerPanel)
        userInfoContainerContainerPanel.add(peopleConnectedLabel)
        userInfoContainerContainerPanel.add(peopleWatchingThisLabel)
        chooseFileButtonContainerPanel.add(chooseFileButton, GridBagConstraints())
        southPanel.add(playReconnectButton, BorderLayout.CENTER)

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane = mainPanel
        frame.isVisible = true
        frame.setBounds(100, 100, 600, 250)
        frame.isResizable = false
        frame.setLocationRelativeTo(null)

        playReconnectButton.isEnabled = false

        fileChooser.selectedFile = File(lastFileChooserSelectedFile)

        fileUrlTextField.addActionListener {
            if (isSuccessfullyConnectedToServer && File(fileUrlTextField.text).isFile) {
                playReconnectButton.isEnabled = true
            }
        }

        playReconnectButton.addActionListener {
            when (playReconnectButton.text) {
                "Play" -> {
                    if (!Files.exists(lastPlayedStore)) {
                        Files.createDirectory(lastPlayedStore.parent)
                        Files.createFile(lastPlayedStore)
                    }
                    lastPlayedStore.toFile().writeText(fileChooser.selectedFile.absolutePath)
                    currentVideoChecksum = MD5.calculate1MBMD5(fileChooser.selectedFile) + "-" + fileChooser.selectedFile.length()
                    PlayerFrame.openAndPlay(fileChooser.selectedFile)
                }
                "Reconnect" -> {
                    println("rec")
                    reconnect()
                }
            }
        }

        chooseFileButton.addActionListener {
            val result = fileChooser.showOpenDialog(frame)
            if (result == JFileChooser.APPROVE_OPTION) {
                fileUrlTextField.text = fileChooser.selectedFile.absolutePath
                playReconnectButton.grabFocus()
            }
        }

        reconnect()
    }

    private var serverConnectionThread: Thread? = null

    private fun reconnect() {
        require(serverConnectionThread?.isAlive != true)

        dotChangingTask = timerTask {
            SwingUtilities.invokeLater {
                val dotAmount = serverStatusLabel.text.count { it == '.' }
                serverStatusLabel.text = if (dotAmount < 3) (serverStatusLabel.text + ".") else serverStatusLabel.text.replace(".", "")
            }
        }

        timer.scheduleAtFixedRate(dotChangingTask, 250, 250)

        serverConnectionThread = Thread {
            try {
                serverStatusLabel.text = "Connecting to the server"
                playReconnectButton.isEnabled = false
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
                            if (PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.status()?.isPlaying != false) {
                                PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.controls()?.setPause(true)
                            }
                        }
                        "continue" -> {
                            val message = Klaxon().parse<ServerContinueMessage>(jsonLine)!!
                            PlayerFrame.lastStatus = "unpaused by " + message.byUser
                            PlayerFrame.updateWindowTitle()
                            if (PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.status()?.isPlaying != true) {
                                PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.controls()
                                        ?.setTime((System.currentTimeMillis() - message.timeMillis) + message.movieTime)
                                PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.controls()?.setPause(false)
                            }
                        }
                        "time_change" -> {
                            val message = Klaxon().parse<ServerContinueMessage>(jsonLine)!!
                            PlayerFrame.lastStatus = "time changed by " + message.byUser
                            PlayerFrame.updateWindowTitle()
                            PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.controls()
                                    ?.setTime((System.currentTimeMillis() - message.timeMillis) + message.movieTime)
                        }
                        "playback_status" -> {
                            val message = Klaxon().parse<ServerPlaybackStatusMessage>(jsonLine)!!
                            if (message.isPaused == null) {
                                PlayerFrame.lastStatus = "you are the first person watching this (unpause to start)"
                            } else {
                                PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.controls()
                                        ?.setTime(message.movieTime!! + (System.currentTimeMillis() - message.timeMillis!!))
                                PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.controls()?.setPause(message.isPaused!!)
                            }
                        }
                    }

                    dotChangingTask.cancel()
                    SwingUtilities.invokeLater {
                        playReconnectButton.text = "Play"
                        serverStatusLabel.text = "Connected to the server"
                        isSuccessfullyConnectedToServer = true
                        playReconnectButton.isEnabled = true
                    }
                }

            } catch (ex: Exception) {
                dotChangingTask.cancel()
                SwingUtilities.invokeLater {
                    frame.toFront()
                    isSuccessfullyConnectedToServer = false
                    serverStatusLabel.text = "Server connection error: " + ex.message
                    playReconnectButton.text = "Reconnect"
                    playReconnectButton.isEnabled = true
                    PlayerFrame.mediaPlayerComponent?.mediaPlayer()?.controls()?.setPause(true)
                    // PlayerFrame.lastStatus = "Disconnected from the server: " + ex.message
                    // PlayerFrame.updateWindowTitle()
                    PlayerFrame.closeFrame()
                }
            }
        }
        serverConnectionThread!!.start()
    }
}

fun main(args: Array<String>) {
    MenuFrame.start()
}