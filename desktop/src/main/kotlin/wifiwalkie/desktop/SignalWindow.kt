package wifiwalkie.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import wifiwalkie.core.Session
import wifiwalkie.core.morse.MorseDecoder
import wifiwalkie.desktop.ui.MorseGuide
import wifiwalkie.desktop.ui.MorseSignalButton
import wifiwalkie.desktop.ui.PressDurationGauge
import wifiwalkie.desktop.ui.TranscriptPanel

/**
 * Morse dashboard with a local press control, peer signal indicator and timing gauge.
 */
class SignalWindow(title: String) : Session.Listener {

    /** Called with true when the Morse key (M or the button) goes down in this window, false when it comes up. */
    var onLocalSignal: (Boolean) -> Unit = {}

    /** Called with true when space (push-to-talk) goes down in this window, false when it comes up. */
    var onTalk: (Boolean) -> Unit = {}

    private val frame = JFrame(title)
    private val status = JLabel()
    private val localButton = MorseSignalButton("HOLD TO SEND", "SENDING…")
    private val peerButton = MorseSignalButton("WAITING FOR PEER", "PEER SENDING…")
    private val pressGauge = PressDurationGauge()
    private val sentTranscript = TranscriptPanel("You sent")
    private val receivedTranscript = TranscriptPanel("They sent")
    private val sentDecoder = MorseDecoder()
    private val receivedDecoder = MorseDecoder()

    private var peerConnected = false
    private var talking = false
    private var peerTalking = false
    private var micAvailable = true
    private var sending = false
    private var peerSending = false
    private var pressedAtNanos: Long? = null
    private var lastPressDurationMs = 0L

    init {
        status.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)

        val transcripts = JPanel(GridLayout(1, 2, 8, 0)).apply {
            add(sentTranscript)
            add(receivedTranscript)
        }
        val lower = JPanel(BorderLayout(8, 0)).apply {
            add(MorseGuide(), BorderLayout.WEST)
            add(transcripts, BorderLayout.CENTER)
        }
        lower.preferredSize = Dimension(760, 205)

        val localControls = JPanel(BorderLayout(12, 0)).apply {
            border = BorderFactory.createTitledBorder("Your signal")
            add(localButton, BorderLayout.CENTER)
            add(pressGauge, BorderLayout.EAST)
        }
        val peerControls = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Incoming signal")
            add(peerButton, BorderLayout.CENTER)
        }
        val signalControls = JPanel(GridLayout(1, 2, 10, 0)).apply {
            add(localControls)
            add(peerControls)
            preferredSize = Dimension(760, 235)
        }

        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        val bottom = JPanel(BorderLayout()).apply {
            add(lower, BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
        }
        frame.add(signalControls, BorderLayout.CENTER)
        frame.add(bottom, BorderLayout.SOUTH)
        frame.pack()
        frame.setLocationRelativeTo(null)

        KeyHoldInput(frame, KeyEvent.VK_M) { down ->
            setLocalSignal(down)
        }
        KeyHoldInput(frame, KeyEvent.VK_SPACE) { down ->
            talking = down && micAvailable
            updateStatus()
            onTalk(down)
        }
        localButton.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1) setLocalSignal(true)
            }

            override fun mouseReleased(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1) setLocalSignal(false)
            }
        })
        Timer(30) {
            val pressStart = pressedAtNanos
            val elapsed = if (sending && pressStart != null) {
                (System.nanoTime() - pressStart) / 1_000_000
            } else lastPressDurationMs
            pressGauge.setElapsed(elapsed)
            sentTranscript.append(sentDecoder.update(sending))
            receivedTranscript.append(receivedDecoder.update(peerSending))
        }.start()
        updateStatus()
    }

    fun show() {
        frame.isVisible = true
    }

    override fun onPeerConnectedChanged(peerId: Int, connected: Boolean) {
        println(if (connected) "Peer connected." else "Peer lost: nothing received for ${Session.TIMEOUT_MS} ms.")
        SwingUtilities.invokeLater {
            peerConnected = connected
            updateStatus()
        }
    }

    /** Shows whether the other person's voice is playing. Call from any thread. */
    fun setPeerTalking(on: Boolean) {
        SwingUtilities.invokeLater {
            peerTalking = on
            updateStatus()
        }
    }

    /** Call once at startup if there's no microphone, so push-to-talk shows as unavailable. */
    fun setMicAvailable(available: Boolean) {
        SwingUtilities.invokeLater {
            micAvailable = available
            updateStatus()
        }
    }

    override fun onPeerSignalChanged(peerId: Int, on: Boolean) {
        SwingUtilities.invokeLater {
            peerSending = on
            peerButton.setSignalActive(on)
            receivedTranscript.append(receivedDecoder.update(on))
        }
    }

    private fun setLocalSignal(down: Boolean) {
        if (sending == down) return
        sending = down
        if (down) {
            pressedAtNanos = System.nanoTime()
            lastPressDurationMs = 0
        } else {
            val startedAt = pressedAtNanos
            if (startedAt != null) lastPressDurationMs = (System.nanoTime() - startedAt) / 1_000_000
            pressedAtNanos = null
        }
        localButton.setSignalActive(down)
        sentTranscript.append(sentDecoder.update(down))
        updateStatus()
        onLocalSignal(down)
    }

    private fun updateStatus() {
        val peer = when {
            !peerConnected -> "waiting for the other computer…"
            peerTalking -> "connected · TALKING"
            else -> "connected"
        }
        val voice = when {
            !micAvailable -> "no microphone"
            talking -> "TALKING"
            else -> "hold SPACE to talk"
        }
        val morse = if (sending) "SENDING · hold for a dot or dash" else "hold M or the button for Morse"
        status.text = "Peer: $peer    |    You: $voice · $morse"
        // A green status line while the other person's voice is playing.
        status.isOpaque = peerTalking
        status.background = PEER_TALKING_COLOR
        status.repaint()
    }

    private companion object {
        val PEER_TALKING_COLOR = Color(0xA5D6A7)
    }
}
