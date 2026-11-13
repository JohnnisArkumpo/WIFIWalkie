package wifiwalkie.desktop

import java.io.Closeable
import wifiwalkie.core.AudioInput
import wifiwalkie.core.AudioPlayback
import wifiwalkie.core.PlayoutBuffer
import wifiwalkie.core.Session

/**
 * The desktop's microphone and speaker. Received audio goes through a
 * [PlayoutBuffer] to the speaker; [onPeerTalking] follows what's actually playing.
 *
 * Either device may be missing. Then a warning is printed and that half is off,
 * and everything else (including the Morse signal) still works.
 *
 * All playback setup lives here, because Task G will swap [AudioPlayback] for a mixer.
 */
class DesktopAudio(onPeerTalking: (Boolean) -> Unit) : Session.Listener, Closeable {

    /** Null if there's no usable microphone. */
    val microphone: AudioInput? = open("microphone") { JavaSoundInput() }

    private val buffer = PlayoutBuffer(onTalkingChanged = onPeerTalking)
    private val playback: AudioPlayback? = open("speaker") { JavaSoundOutput() }?.let { AudioPlayback(it, buffer) }

    fun start() {
        playback?.start()
    }

    override fun onPeerConnectedChanged(peerId: Int, connected: Boolean) = buffer.reset()

    override fun onPeerSignalChanged(peerId: Int, on: Boolean) {}

    override fun onPeerAudio(peerId: Int, sequence: Int, startOfTransmission: Boolean, pcm: ByteArray) {
        buffer.push(sequence, startOfTransmission, pcm, System.nanoTime() / 1_000_000)
    }

    override fun close() {
        microphone?.close()
        playback?.close()
    }

    private fun <T> open(name: String, create: () -> T): T? = try {
        create()
    } catch (e: Exception) {
        System.err.println("Warning: couldn't open the $name (${e.message}). Continuing without it.")
        null
    }
}
