package me.nicofisi.satisfyingplayer

import uk.co.caprica.vlcj.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.embedded.windows.Win32FullScreenStrategy
import java.awt.BorderLayout
import java.awt.event.*
import java.io.File
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
            frame?.title = "$fileName - ${formatTime(mediaPlayerComponent?.mediaPlayer?.time ?: -1)}" +
                    " - $currentVolume% volume - $lastStatus - SatisfyingPlayer"
        }
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
        mediaPlayerComponent!!.mediaPlayer!!.setPause(paused)
        if (paused) {
            sendToServer(ClientPauseMessage(currentVideoChecksum!!, mediaPlayerComponent!!.mediaPlayer.time))
        } else {
            sendToServer(ClientContinueMessage(currentVideoChecksum!!, mediaPlayerComponent!!.mediaPlayer.time, System.currentTimeMillis()))
        }
    }

    fun openAndPlay(file: File) {
        fileName = file.name

        NativeDiscovery().discover()

        val timer = Timer()

        timer.scheduleAtFixedRate(timerTask { updateWindowTitle() }, 500, 250)

        SwingUtilities.invokeLater {
            frame = JFrame("Player - SatisfyingPlayer")
            frame?.setBounds(100, 100, 1080, 720)

            val contentPane = JPanel()
            contentPane.layout = BorderLayout()

            mediaPlayerComponent = object : EmbeddedMediaPlayerComponent() {
                var lastLeftClick: Long = 0
                var pauseTask: TimerTask? = null

                override fun mouseClicked(e: MouseEvent) {
                    if (lastLeftClick > System.currentTimeMillis() - LEFT_CLICK_PAUSE_DELAY) {
                        mediaPlayer.toggleFullScreen()
                        pauseTask?.cancel()
                        pauseTask = null
                        lastLeftClick = 0
                    } else {
                        lastLeftClick = System.currentTimeMillis()
                        pauseTask = timerTask {
                            setAndSendPause(mediaPlayer.isPlaying)
                        }
                        timer.schedule(pauseTask, LEFT_CLICK_PAUSE_DELAY)
                    }
                }

                override fun mouseWheelMoved(e: MouseWheelEvent) {
                    updateVolume((5 * if (e.unitsToScroll > 0) -1 else 1))
                }

                fun updateVolume(delta: Int) {
                    val volume = mediaPlayer.volume + delta
                    currentVolume = volume
                    mediaPlayer.volume = volume
                    updateWindowTitle()
                }

                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        32 -> // space
                            setAndSendPause(mediaPlayer.isPlaying)
                        37 -> { // left arrow
                            mediaPlayer.skip(if (e.isShiftDown || e.isAltDown || e.isControlDown) -30000 else -5000)
                            sendToServer(ClientTimeChangeMessage(
                                    currentVideoChecksum!!, mediaPlayer.time, System.currentTimeMillis(), !mediaPlayer.isPlaying))
                        }
                        38 -> { // arrow up
                            updateVolume(5)
                        }
                        39 -> { // right arrow
                            mediaPlayer.skip(if (e.isShiftDown || e.isAltDown || e.isControlDown) 30000 else 5000)
                            sendToServer(ClientTimeChangeMessage(
                                    currentVideoChecksum!!, mediaPlayer.time, System.currentTimeMillis(), !mediaPlayer.isPlaying))
                        }
                        40 -> { // arrow down
                            updateVolume(-5)
                        }
                        122 -> // f12
                            mediaPlayer.toggleFullScreen()
                    }
                    if (e.keyCode == 32) { // space
                        mediaPlayer.pause()
                        lastStatus = "synchronizing with the server"
                    }
                }
            }

            currentVolume = mediaPlayerComponent!!.mediaPlayer!!.volume
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
            mediaPlayerComponent!!.mediaPlayer.setFullScreenStrategy(Win32FullScreenStrategy(frame))
            mediaPlayerComponent!!.mediaPlayer.playMedia(file.absolutePath)
            mediaPlayerComponent!!.mediaPlayer.setPause(true)

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
                    timer.cancel()
                    mediaPlayerComponent?.release()
                    frame?.dispose()
                    frame?.isVisible = false
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