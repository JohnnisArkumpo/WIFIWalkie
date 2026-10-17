package wifiwalkie.desktop

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import wifiwalkie.core.MorseDecoder
import wifiwalkie.core.Session
import wifiwalkie.desktop.ui.MorseGuide
import wifiwalkie.desktop.ui.MorseSignalButton
import wifiwalkie.desktop.ui.PressDurationGauge
import wifiwalkie.desktop.ui.TranscriptPanel

/**
 * Morse dashboard with a local press control, peer signal indicator and timing gauge.
 */
class SignalWindow(title: String) : Session.Listener {

    /** Called with true when space goes down in this window, false when it comes up. */
    var onLocalSignal: (Boolean) -> Unit = {}

    private val frame = JFrame(title)
    private val status = JLabel()
    private val peerDecodedLine = JLabel("Peer decoded: ")
    private val localButton = MorseSignalButton("HOLD TO SEND", "SENDING…")
    private val peerButton = MorseSignalButton("WAITING FOR PEER", "PEER SENDING…")
    private val pressGauge = PressDurationGauge()
    private val sentTranscript = TranscriptPanel("You sent")
    private val receivedTranscript = TranscriptPanel("They sent")
    private val sentDecoder = MorseDecoder()
    private val receivedDecoder = MorseDecoder()

    private var peerConnected = false
    private var sending = false
    private var peerSending = false
    private var pressedAtNanos: Long? = null
    private var lastPressDurationMs = 0L
    private val peerDecodedText = StringBuilder()

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
        val footer = JPanel(GridLayout(2, 1)).apply {
            add(peerDecodedLine)
            add(status)
        }
        val bottom = JPanel(BorderLayout()).apply {
            add(lower, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }
        frame.add(signalControls, BorderLayout.CENTER)
        frame.add(bottom, BorderLayout.SOUTH)
        frame.pack()
        frame.setLocationRelativeTo(null)

        SpaceBarInput(frame) { down ->
            setLocalSignal(down)
        }
        localButton.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1) setLocalSignal(true)
            }

            override fun mouseReleased(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1) setLocalSignal(false)
            }
        })
        Timer(50) {
            val nowMs = System.nanoTime() / 1_000_000
            val pressStart = pressedAtNanos
            val elapsed = if (sending && pressStart != null) {
                (System.nanoTime() - pressStart) / 1_000_000
            } else lastPressDurationMs
            pressGauge.setElapsed(elapsed)
            sentTranscript.append(sentDecoder.poll(nowMs))
            appendPeerText(receivedDecoder.poll(nowMs))
            updatePeerDecodedLine()
        }.start()
        updateStatus()
    }

    fun show() {
        frame.isVisible = true
    }

    override fun onPeerConnectedChanged(connected: Boolean) {
        println(if (connected) "Peer connected." else "Peer lost: nothing received for ${Session.TIMEOUT_MS} ms.")
        SwingUtilities.invokeLater {
            peerConnected = connected
            if (!connected) {
                val nowMs = System.nanoTime() / 1_000_000
                peerDecoderReset(nowMs)
                peerDecodedText.setLength(0)
                receivedTranscript.clear()
                peerButton.setSignalActive(false)
                peerSending = false
                updatePeerDecodedLine()
            }
            updateStatus()
        }
    }

    override fun onPeerSignalChanged(on: Boolean) {
        SwingUtilities.invokeLater {
            peerSending = on
            val nowMs = System.nanoTime() / 1_000_000
            receivedDecoder.onSignal(on, nowMs)
            peerButton.setSignalActive(on)
            appendPeerText(receivedDecoder.poll(nowMs))
            updatePeerDecodedLine()
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
        val nowMs = System.nanoTime() / 1_000_000
        sentDecoder.onSignal(down, nowMs)
        sentTranscript.append(sentDecoder.poll(nowMs))
        updateStatus()
        onLocalSignal(down)
    }

    private fun updateStatus() {
        val peer = if (peerConnected) "connected" else "waiting for the other computer…"
        val you = if (sending) "SENDING · hold for a dot or dash" else "hold the button or SPACE to send"
        status.text = "Peer: $peer    |    You: $you"
    }

    private fun appendPeerText(text: String) {
        peerDecodedText.append(text)
        if (peerDecodedText.length > 40) peerDecodedText.delete(0, peerDecodedText.length - 40)
        receivedTranscript.append(text)
    }

    private fun updatePeerDecodedLine() {
        val decoded = peerDecodedText.toString().takeLast(40)
        val pattern = receivedDecoder.currentPattern
        peerDecodedLine.text = "Peer decoded: $decoded${if (pattern.isNotEmpty()) " $pattern" else ""}"
    }

    private fun peerDecoderReset(nowMs: Long) {
        receivedDecoder.reset(nowMs)
    }
}
