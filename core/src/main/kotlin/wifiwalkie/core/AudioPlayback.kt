package wifiwalkie.core

import java.io.Closeable
import kotlin.concurrent.thread

/**
 * Plays [buffer] through [output] on its own thread: one frame every 20 ms, or
 * silence when the buffer has nothing. [AudioOutput.write] blocking is what
 * paces the loop.
 */
class AudioPlayback(private val output: AudioOutput, private val buffer: PlayoutBuffer) : Closeable {

    @Volatile private var running = false
    private var player: Thread? = null

    fun start() {
        output.start()
        running = true
        player = thread(name = "audio-playback", isDaemon = true) {
            while (running) {
                output.write(buffer.pop(System.nanoTime() / 1_000_000) ?: SILENCE)
            }
        }
    }

    override fun close() {
        running = false
        // Let the current write finish (at most one frame) before closing the speaker under it.
        player?.join(100)
        output.close()
    }

    private companion object {
        val SILENCE = ByteArray(AudioSpec.FRAME_BYTES)
    }
}
