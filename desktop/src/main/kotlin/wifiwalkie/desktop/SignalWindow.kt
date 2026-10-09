package wifiwalkie.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import wifiwalkie.core.Session

/**
 * The window: white while the peer holds space, black otherwise, with a
 * status line underneath. Create it on the Swing thread.
 */
class SignalWindow(title: String) : Session.Listener {

    /** Called with true when space goes down in this window, false when it comes up. */
    var onLocalSignal: (Boolean) -> Unit = {}

    private val frame = JFrame(title)
    private val light = JPanel()
    private val status = JLabel()

    private var peerConnected = false
    private var sending = false

    init {
        light.background = Color.BLACK
        light.preferredSize = Dimension(480, 320)
        status.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)

        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.add(light, BorderLayout.CENTER)
        frame.add(status, BorderLayout.SOUTH)
        frame.pack()
        frame.setLocationRelativeTo(null)

        SpaceBarInput(frame) { down ->
            sending = down
            updateStatus()
            onLocalSignal(down)
        }
        updateStatus()
    }

    fun show() {
        frame.isVisible = true
    }

    override fun onPeerConnectedChanged(connected: Boolean) {
        println(if (connected) "Peer connected." else "Peer lost: nothing received for ${Session.TIMEOUT_MS} ms.")
        SwingUtilities.invokeLater {
            peerConnected = connected
            updateStatus()
        }
    }

    override fun onPeerSignalChanged(on: Boolean) {
        SwingUtilities.invokeLater {
            light.background = if (on) Color.WHITE else Color.BLACK
        }
    }

    private fun updateStatus() {
        val peer = if (peerConnected) "connected" else "waiting for the other computer…"
        val you = if (sending) "SENDING" else "hold SPACE to send"
        status.text = "Peer: $peer    |    You: $you"
    }
}
