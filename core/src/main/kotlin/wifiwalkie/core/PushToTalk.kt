package wifiwalkie.core

import java.io.Closeable

/**
 * Sends the microphone while talking. [send] is usually [Session.sendAudio].
 *
 * The first frame of each press is sent with `startOfTransmission = true`, so the
 * receiver knows a new transmission began.
 */
class PushToTalk(
    private val input: AudioInput,
    private val send: (pcm: ByteArray, startOfTransmission: Boolean) -> Unit,
) : Closeable {

    @Volatile var talking = false
        private set

    @Synchronized
    fun setTalking(on: Boolean) {
        if (on == talking) return
        talking = on
        if (on) {
            var first = true
            input.start { frame ->
                send(frame, first)
                first = false
            }
        } else {
            input.stop()
        }
    }

    override fun close() = input.close()
}
