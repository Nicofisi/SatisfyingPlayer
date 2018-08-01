package tutorial

import uk.co.caprica.vlcj.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.embedded.windows.Win32FullScreenStrategy
import java.awt.BorderLayout
import java.awt.event.*
import java.nio.file.Paths
import java.util.*
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.timerTask

object PlayerFrame {
    const val LEFT_CLICK_PAUSE_DELAY = 200L

    fun openAndPlay(filePath: String) {
        NativeDiscovery().discover()

        val timer = Timer()

        SwingUtilities.invokeLater {
            val frame = JFrame("SatisfyingPlayer - ${Paths.get(filePath).fileName}")
            frame.setBounds(100, 100, 1080, 720)

            val contentPane = JPanel()
            contentPane.layout = BorderLayout()

            val mediaPlayerComponent = object : EmbeddedMediaPlayerComponent() {
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
                            mediaPlayer.pause()
                        }
                        timer.schedule(pauseTask, LEFT_CLICK_PAUSE_DELAY)
                    }
                }

                override fun mouseWheelMoved(e: MouseWheelEvent) {

                }

                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        32 -> // space
                            mediaPlayer.pause()
                        122 -> // f12
                            mediaPlayer.toggleFullScreen()
                    }
                    if (e.keyCode == 32) { // space
                        mediaPlayer.pause()
                    }
                }
            }

            contentPane.add(mediaPlayerComponent, BorderLayout.CENTER)

            val controlsPane = JPanel()

            val pauseButton = JButton("Pause")
            val rewindButton = JButton("Rewind")
            val skipButton = JButton("Skip")
            val weirdButton = JButton("Weird button")


            listOf(pauseButton, rewindButton, skipButton, weirdButton).forEach { controlsPane.add(it) }

            contentPane.add(controlsPane, BorderLayout.SOUTH)

            frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            frame.contentPane = contentPane
            frame.isVisible = true
            mediaPlayerComponent.mediaPlayer.setFullScreenStrategy(Win32FullScreenStrategy(frame))
            mediaPlayerComponent.mediaPlayer.playMedia(filePath)
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
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    mediaPlayerComponent.release()
                }
            })

            pauseButton.addActionListener {
                mediaPlayerComponent.mediaPlayer.pause()
            }
            rewindButton.addActionListener {
                mediaPlayerComponent.mediaPlayer.skip(-10000)
            }
            skipButton.addActionListener {
                mediaPlayerComponent.mediaPlayer.skip(10000)
            }
            weirdButton.addActionListener {
                mediaPlayerComponent.mediaPlayer.toggleFullScreen()
            }
        }
    }
}