package tutorial

import java.awt.BorderLayout
import java.awt.Button
import java.net.Socket
import java.util.Timer
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.timerTask
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil.close
import jdk.nashorn.internal.runtime.ScriptingFunctions.readLine
import java.io.InputStreamReader
import java.io.BufferedReader
import com.sun.xml.internal.ws.streaming.XMLStreamWriterUtil.getOutputStream
import java.io.PrintWriter
import java.net.ConnectException


const val SERVER_IP = "127.0.0.1"
const val SERVER_PORT = 34857

fun main(args: Array<String>) {
    val frame = JFrame("SatisfyingPlayer")
    frame.setBounds(100, 100, 400, 300)

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
    val button = Button("Hi")
    controlsPane.add(button)

    contentPane.add(controlsPane, BorderLayout.SOUTH)

    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane = contentPane
    frame.isVisible = true

    button.addActionListener {
        PlayerFrame.openAndPlay(
                "E:\\Documents\\Torrents\\The Hunger Games (2012) [1080p]\\The.Hunger.Games.1080p.BluRay.x264.YIFY.mp4")
    }


    Thread {
        try {
            val socket = Socket(SERVER_IP, SERVER_PORT)

            val br = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = PrintWriter(socket.getOutputStream(), true)

            println("server says: " + br.readLine())

            val userInputBR = BufferedReader(InputStreamReader(System.`in`))
            val userInput = userInputBR.readLine()

            out.println(userInput)

            println("server says: " + br.readLine())

            if ("exit".equals(userInput, ignoreCase = true)) {
                socket.close()
            }
        } catch (ex: ConnectException) {
            dotChangingTask.cancel()
            SwingUtilities.invokeLater {
                serverStatus.text = "Failed to connect to the server: " + ex.message
            }
        }
    }.start()
}