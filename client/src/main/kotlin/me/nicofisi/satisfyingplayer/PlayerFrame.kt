package me.nicofisi.satisfyingplayer

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.fullscreen.windows.Win32FullScreenStrategy
import java.awt.BorderLayout
import java.awt.event.*
import java.io.File
import java.lang.Integer.max
import java.util.*
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.timerTask

object PlayerFrame {
    const val LEFT_CLICK_PAUSE_DELAY = 200L

    var frame: JFrame? = null
    var mediaPlayerComponent: EmbeddedMediaPlayerComponent? = null

    var lastStatus = "paused"
    var currentVolume: Int? = null
    var fileName: String? = null

    fun updateWindowTitle() {
        SwingUtilities.invokeLater {
            frame?.title = "$fileName - ${formatTime(mediaPlayerComponent?.mediaPlayer()?.status()?.time() ?: -1)}" +
                    " - $currentVolume% volume - $lastStatus - SatisfyingPlayer"
        }
    }

    var timer: Timer? = null

    fun closeFrame() {
        timer?.cancel()
        mediaPlayerComponent?.release()
        mediaPlayerComponent = null
        fileName = null
        frame?.dispose()
        frame?.isVisible = false
    }

    fun formatTime(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = millis / (1000 * 60) - (hours * 60)
        val seconds = millis / 1000 - (minutes * 60 - hours * 60 * 60)
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    fun setAndSendPause(paused: Boolean) {
        lastStatus = "synchronizing with the server"
        updateWindowTitle()
        mediaPlayerComponent!!.mediaPlayer()!!.controls().setPause(paused)
        if (paused) {
            sendToServer(ClientPauseMessage(currentVideoChecksum!!, mediaPlayerComponent!!.mediaPlayer().status().time()))
        } else {
            sendToServer(ClientContinueMessage(currentVideoChecksum!!, mediaPlayerComponent!!.mediaPlayer().status().time(), System.currentTimeMillis()))
        }
    }

    fun openAndPlay(file: File) {
        fileName = file.name

        NativeDiscovery().discover()

        timer = Timer()

        timer?.scheduleAtFixedRate(timerTask { updateWindowTitle() }, 500, 250)

        SwingUtilities.invokeLater {
            frame = JFrame("Player - SatisfyingPlayer")
            frame?.setBounds(100, 100, 1080, 720)

            val contentPane = JPanel()
            contentPane.layout = BorderLayout()

            println("hi")

            mediaPlayerComponent = object : EmbeddedMediaPlayerComponent() {}

            println("hi2")

            currentVolume = mediaPlayerComponent!!.mediaPlayer()!!.audio().volume()
            updateWindowTitle()

            contentPane.add(mediaPlayerComponent, BorderLayout.CENTER)

//            val controlsPane = JPanel()
//
//            val pauseButton = JButton("Pause")
//            val rewindButton = JButton("Rewind")
//            val skipButton = JButton("Skip")
//            val weirdButton = JButton("Weird button")
//
//
//            listOf(pauseButton, rewindButton, skipButton, weirdButton).forEach { controlsPane.add(it) }
//
//            contentPane.add(controlsPane, BorderLayout.SOUTH)

            frame?.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            frame?.contentPane = contentPane
            frame?.isVisible = true
            mediaPlayerComponent!!.mediaPlayer().fullScreen().strategy(Win32FullScreenStrategy(frame))
            mediaPlayerComponent!!.mediaPlayer().media().play(file.absolutePath)
            mediaPlayerComponent!!.mediaPlayer().controls().setPause(true)

            sendToServer(ClientPlaybackStatusRequestMessage(currentVideoChecksum!!))

            contentPane.addMouseListener(object : MouseListener {
                override fun mouseReleased(e: MouseEvent?) {

                }

                override fun mouseEntered(e: MouseEvent?) {

                }

                override fun mouseExited(e: MouseEvent?) {

                }

                override fun mousePressed(e: MouseEvent?) {

                }

                override fun mouseClicked(e: MouseEvent?) {
//                frame.extendedState = JFrame.MAXIMIZED_BOTH
//                frame.isUndecorated = true
//                frame.isVisible = true
                }
            })
            frame?.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    closeFrame()
                }
            })

//            pauseButton.addActionListener {
//                mediaPlayerComponent.mediaPlayer.pause()
//            }
//            rewindButton.addActionListener {
//                mediaPlayerComponent.mediaPlayer.skip(-10000)
//            }
//            skipButton.addActionListener {
//                mediaPlayerComponent.mediaPlayer.skip(10000)
//            }
//            weirdButton.addActionListener {
//                mediaPlayerComponent.mediaPlayer.toggleFullScreen()
//            }
        }
    }
}