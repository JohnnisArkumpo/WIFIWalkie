package wifiwalkie.core

import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Sends this computer's on/off signal and audio to the peer, and tracks the peer.
 *
 * The local signal is sent whenever it changes and again every [HEARTBEAT_MS].
 * The repeats act as a heartbeat: if nothing at all arrives from the peer for
 * [TIMEOUT_MS], the peer counts as disconnected and its signal is turned off.
 *
 * [sessionId] identifies this run of the program in every message it sends.
 */
class Session(
    private val transport: Transport,
    private val listener: Listener,
    val sessionId: Int = Random.nextInt(),
) : Closeable {

    /**
     * Called on a background thread. Implementations must return quickly.
     *
     * `peerId` is the peer's session ID. With one peer it's always the same until
     * the peer restarts; it's there so group calls can tell senders apart.
     */
    interface Listener {
        fun onPeerConnectedChanged(peerId: Int, connected: Boolean)
        fun onPeerSignalChanged(peerId: Int, on: Boolean)

        /** One frame of the peer's audio, in the order it arrived (which may not be [sequence] order). */
        fun onPeerAudio(peerId: Int, sequence: Int, startOfTransmission: Boolean, pcm: ByteArray) {}
    }

    // All sending and timeout checks run on this one thread, so the sequence counters need no lock.
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "session").apply { isDaemon = true }
    }
    private var signalSequence = 0
    private var audioSequence = 0

    @Volatile private var localOn = false

    /** Everything known about the peer. Task G turns this into a map keyed by session ID. */
    private class PeerState(val sessionId: Int) {
        var connected = false
        var signalOn = false
        var signalSequence = 0
        var lastHeardNanos = 0L
    }

    // Guarded by `this`.
    private var peer: PeerState? = null

    fun start() {
        transport.start(::onMessage)
        worker.scheduleAtFixedRate(::tick, 0, HEARTBEAT_MS, TimeUnit.MILLISECONDS)
    }

    /** Call when the local key goes down or up. */
    fun setLocalSignal(on: Boolean) {
        if (on == localOn) return
        localOn = on
        worker.execute(::sendSignal)
    }

    /**
     * Sends one frame of audio. Call from any thread. [pcm] is copied, so the
     * caller may reuse its buffer. Set [startOfTransmission] on the first frame
     * of each push-to-talk press.
     */
    fun sendAudio(pcm: ByteArray, startOfTransmission: Boolean) {
        val frame = pcm.copyOf()
        worker.execute {
            audioSequence++
            transport.send(Message.AudioFrame(sessionId, audioSequence, startOfTransmission, frame))
        }
    }

    private fun sendSignal() {
        signalSequence++
        transport.send(Message.Signal(sessionId, signalSequence, localOn))
    }

    private fun tick() {
        sendSignal()
        synchronized(this) {
            val current = peer ?: return
            if (current.connected && System.nanoTime() - current.lastHeardNanos > TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS)) {
                disconnect(current)
            }
        }
    }

    private fun onMessage(message: Message) {
        synchronized(this) {
            var current = peer
            if (current == null || current.sessionId != message.sessionId) {
                // A new peer, or the same one restarted. Report the old one gone first, so playback can reset.
                if (current != null && current.connected) disconnect(current)
                current = PeerState(message.sessionId)
                peer = current
            }

            // Any valid message proves the peer is alive.
            current.lastHeardNanos = System.nanoTime()
            if (!current.connected) {
                current.connected = true
                listener.onPeerConnectedChanged(current.sessionId, true)
            }

            when (message) {
                is Message.Signal -> {
                    // UDP can deliver packets late or out of order. Ignore any signal older than what we've already seen.
                    if (message.sequence <= current.signalSequence) return
                    current.signalSequence = message.sequence
                    if (message.on != current.signalOn) {
                        current.signalOn = message.on
                        listener.onPeerSignalChanged(current.sessionId, message.on)
                    }
                }
                is Message.AudioFrame ->
                    listener.onPeerAudio(current.sessionId, message.sequence, message.startOfTransmission, message.pcm)
                is Message.Hello, is Message.Bye -> {}
            }
        }
    }

    // Call while holding `this`.
    private fun disconnect(state: PeerState) {
        state.connected = false
        listener.onPeerConnectedChanged(state.sessionId, false)
        if (state.signalOn) {
            state.signalOn = false
            listener.onPeerSignalChanged(state.sessionId, false)
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
