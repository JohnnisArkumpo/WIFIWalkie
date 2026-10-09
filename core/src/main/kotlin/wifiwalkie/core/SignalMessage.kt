package wifiwalkie.core

import java.nio.ByteBuffer

/**
 * One update of the sender's on/off state.
 *
 * Each message carries the full state rather than a "pressed" or "released"
 * event, so if one is lost (possible over UDP) the next one corrects it.
 *
 * Wire format, 10 bytes: magic `W`, on (0 or 1), session id (int), sequence (int).
 */
data class SignalMessage(val on: Boolean, val sessionId: Int, val sequence: Int) {

    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(MAGIC)
        .put((if (on) 1 else 0).toByte())
        .putInt(sessionId)
        .putInt(sequence)
        .array()

    companion object {
        const val SIZE = 10
        private const val MAGIC = 'W'.code.toByte()

        /** Reads the first [length] bytes of [bytes], or returns null if they aren't a valid message. */
        fun decode(bytes: ByteArray, length: Int = bytes.size): SignalMessage? {
            if (length != SIZE || bytes[0] != MAGIC) return null
            val on = when (bytes[1].toInt()) {
                0 -> false
                1 -> true
                else -> return null
            }
            val buffer = ByteBuffer.wrap(bytes, 2, 8)
            return SignalMessage(on, buffer.getInt(), buffer.getInt())
        }
    }
}
