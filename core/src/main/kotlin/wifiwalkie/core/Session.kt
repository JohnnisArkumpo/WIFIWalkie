package wifiwalkie.core

import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Sends this computer's on/off signal to the peer and tracks the peer's signal.
 *
 * The local state is sent whenever it changes and again every [HEARTBEAT_MS].
 * The repeats act as a heartbeat: if nothing arrives from the peer for
 * [TIMEOUT_MS], the peer counts as disconnected and its signal is turned off.
 */
class Session(private val transport: Transport, private val listener: Listener) : Closeable {

    /** Called on a background thread. Implementations must return quickly. */
    interface Listener {
        fun onPeerConnectedChanged(connected: Boolean)
        fun onPeerSignalChanged(on: Boolean)
    }

    // Lets the peer tell a restarted copy of this program apart from late packets.
    private val sessionId = Random.nextInt()

    // All sending and timeout checks run on this one thread, so `sequence` needs no lock.
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "session").apply { isDaemon = true }
    }
    private var sequence = 0

    @Volatile private var localOn = false

    // Peer state, guarded by `this`.
    private var peerConnected = false
    private var peerOn = false
    private var peerSessionId: Int? = null
    private var peerSequence = 0
    private var lastHeardNanos = 0L

    fun start() {
        transport.start(::onMessage)
        worker.scheduleAtFixedRate(::tick, 0, HEARTBEAT_MS, TimeUnit.MILLISECONDS)
    }

    /** Call when the local key goes down or up. */
    fun setLocalSignal(on: Boolean) {
        if (on == localOn) return
        localOn = on
        worker.execute(::sendState)
    }

    private fun sendState() {
        sequence++
        transport.send(SignalMessage(localOn, sessionId, sequence))
    }

    private fun tick() {
        sendState()
        synchronized(this) {
            if (peerConnected && System.nanoTime() - lastHeardNanos > TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS)) {
                peerConnected = false
                listener.onPeerConnectedChanged(false)
                if (peerOn) {
                    peerOn = false
                    listener.onPeerSignalChanged(false)
                }
            }
        }
    }

    private fun onMessage(message: SignalMessage) {
        synchronized(this) {
            // UDP can deliver packets late or out of order. Ignore anything older than what we've already seen.
            if (message.sessionId == peerSessionId && message.sequence <= peerSequence) return
            peerSessionId = message.sessionId
            peerSequence = message.sequence
            lastHeardNanos = System.nanoTime()

            if (!peerConnected) {
                peerConnected = true
                listener.onPeerConnectedChanged(true)
            }
            if (message.on != peerOn) {
                peerOn = message.on
                listener.onPeerSignalChanged(message.on)
            }
        }
    }

    override fun close() {
        worker.shutdownNow()
        transport.close()
    }

    companion object {
        const val HEARTBEAT_MS = 100L
        const val TIMEOUT_MS = 1000L
    }
}
