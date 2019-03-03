package me.nicofisi.satisfyingplayer

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.TrackDescription
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
        currentVideoChecksum = null
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

            mediaPlayerComponent = object : EmbeddedMediaPlayerComponent() {
                var lastLeftClick: Long = 0
                var pauseTask: TimerTask? = null

                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != 1) return // only left click

                    if (lastLeftClick > System.currentTimeMillis() - LEFT_CLICK_PAUSE_DELAY) {
                        mediaPlayer().fullScreen().toggle()
                        pauseTask?.cancel()
                        pauseTask = null
                        lastLeftClick = 0
                    } else {
                        lastLeftClick = System.currentTimeMillis()
                        pauseTask = timerTask {
                            setAndSendPause(mediaPlayer().status().isPlaying)
                        }
                        timer?.schedule(pauseTask, LEFT_CLICK_PAUSE_DELAY)
                    }
                }

                override fun mouseWheelMoved(e: MouseWheelEvent) {
                    updateVolume((5 * if (e.unitsToScroll > 0) -1 else 1))
                }

                fun updateVolume(delta: Int) {
                    val volume = max(mediaPlayer().audio().volume() + delta, 0)
                    currentVolume = volume
                    mediaPlayer().audio().setVolume(volume)
                    updateWindowTitle()
                }

                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        32 -> // space
                            setAndSendPause(mediaPlayer().status().isPlaying)
                        37 -> { // left arrow
                            mediaPlayer().controls().skipTime(if (e.isShiftDown || e.isAltDown || e.isControlDown) -30000 else -5000)
                            sendToServer(ClientTimeChangeMessage(
                                    currentVideoChecksum!!, mediaPlayer().status().time(), System.currentTimeMillis(), !mediaPlayer().status().isPlaying))
                        }
                        38 -> { // arrow up
                            updateVolume(5)
                        }
                        39 -> { // right arrow
                            mediaPlayer().controls().skipTime(if (e.isShiftDown || e.isAltDown || e.isControlDown) 30000 else 5000)
                            sendToServer(ClientTimeChangeMessage(
                                    currentVideoChecksum!!, mediaPlayer().status().time(), System.currentTimeMillis(), !mediaPlayer().status().isPlaying))
                        }
                        40 -> { // arrow down
                            updateVolume(-5)
                        }
                        83 -> { // 's' key
                            val currentTrackId = mediaPlayer().subpictures().track()
                            var found = false
                            var newTrackDescription: TrackDescription? = null
                            for (trackDesc in mediaPlayer().subpictures().trackDescriptions()) {
                                if (found) {
                                    newTrackDescription = trackDesc
                                    break
                                }

                                if (trackDesc.id() == currentTrackId)
                                    found = true
                            }
                            mediaPlayer().subpictures().setTrack(newTrackDescription?.id() ?: -1)

                            mediaPlayer().marquee().setText("Subtitle track: ${newTrackDescription?.description()
                                    ?: "disabled"}")
                        }
                        122 -> // f11
                            mediaPlayer().fullScreen().toggle()

                    }
                    if (e.keyCode == 32) { // space
                        mediaPlayer().controls().pause()
                        lastStatus = "synchronizing with the server"
                    }
                }
            }

            frame?.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            frame?.contentPane = contentPane
            frame?.isVisible = true

            mediaPlayerComponent!!.apply {
                contentPane.add(this, BorderLayout.CENTER)

                mediaPlayer().apply {
                    currentVolume = audio().volume()

                    fullScreen().strategy(Win32FullScreenStrategy(frame))
                    media().play(file.absolutePath)
                    controls().setPause(true)
                }
            }

            updateWindowTitle()

            sendToServer(ClientPlaybackStatusRequestMessage(currentVideoChecksum!!))

            frame?.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    closeFrame()
                }
            })
        }
    }
}