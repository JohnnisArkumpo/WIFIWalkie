package wifiwalkie.core

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * Everything sent between two copies of the program.
 *
 * Wire format: magic `W`, a type byte (`H`, `S`, `A` or `B`), then the fields
 * in declared order. Ints are 4 bytes, big-endian. Details are on each type.
 */
sealed class Message {
    /** The sender's random ID for this run of the program. */
    abstract val sessionId: Int

    /** Handshake and heartbeat. The passkey is one length byte followed by its UTF-8 bytes. */
    data class Hello(val protocolVersion: Int, override val sessionId: Int, val passkey: String) : Message() {
        init {
            require(passkey.toByteArray(Charsets.UTF_8).size <= MAX_PASSKEY_BYTES) { "Passkey is over $MAX_PASSKEY_BYTES bytes" }
        }
    }

    /**
     * The sender's on/off state. Each one carries the full state rather than a
     * "pressed" or "released" event, so if one is lost (possible over UDP) the
     * next one corrects it. `on` is one byte, 0 or 1.
     */
    data class Signal(override val sessionId: Int, val sequence: Int, val on: Boolean) : Message()

    /**
     * One frame of audio in [AudioSpec] format. [startOfTransmission] is bit 0 of
     * a flags byte after [sequence]; the other bits are reserved and written as 0.
     * The audio fills the rest of the message.
     */
    class AudioFrame(
        override val sessionId: Int,
        val sequence: Int,
        val startOfTransmission: Boolean,
        val pcm: ByteArray,
    ) : Message() {
        init {
            require(pcm.size <= MAX_PCM_BYTES) { "Audio frame is over $MAX_PCM_BYTES bytes" }
        }
    }

    /** The sender is shutting down. */
    data class Bye(override val sessionId: Int) : Message()

    fun encode(): ByteArray = when (this) {
        is Hello -> {
            val passkeyBytes = passkey.toByteArray(Charsets.UTF_8)
            header(HELLO, 4 + 4 + 1 + passkeyBytes.size)
                .putInt(protocolVersion)
                .putInt(sessionId)
                .put(passkeyBytes.size.toByte())
                .put(passkeyBytes)
        }
        is Signal -> header(SIGNAL, 4 + 4 + 1)
            .putInt(sessionId)
            .putInt(sequence)
            .put((if (on) 1 else 0).toByte())
        is AudioFrame -> header(AUDIO, AUDIO_HEADER_SIZE - HEADER_SIZE + pcm.size)
            .putInt(sessionId)
            .putInt(sequence)
            .put((if (startOfTransmission) 1 else 0).toByte())
            .put(pcm)
        is Bye -> header(BYE, 4)
            .putInt(sessionId)
    }.array()

    companion object {
        const val PROTOCOL_VERSION = 1
        /** Largest encoded message in bytes; fits in one UDP packet. */
        const val MAX_SIZE = 1400
        const val MAX_PASSKEY_BYTES = 64

        private const val MAGIC = 'W'.code.toByte()
        private const val HELLO = 'H'.code.toByte()
        private const val SIGNAL = 'S'.code.toByte()
        private const val AUDIO = 'A'.code.toByte()
        private const val BYE = 'B'.code.toByte()

        private const val HEADER_SIZE = 2
        private const val AUDIO_HEADER_SIZE = HEADER_SIZE + 4 + 4 + 1
        private const val MAX_PCM_BYTES = MAX_SIZE - AUDIO_HEADER_SIZE

        private fun header(type: Byte, bodySize: Int): ByteBuffer =
            ByteBuffer.allocate(HEADER_SIZE + bodySize).put(MAGIC).put(type)

        /**
         * Reads the first [length] bytes of [bytes], or returns null if they aren't
         * exactly one valid message. Never throws.
         */
        fun decode(bytes: ByteArray, length: Int = bytes.size): Message? {
            if (length !in HEADER_SIZE..minOf(bytes.size, MAX_SIZE) || bytes[0] != MAGIC) return null
            val buffer = ByteBuffer.wrap(bytes, HEADER_SIZE, length - HEADER_SIZE)
            return try {
                val message = when (bytes[1]) {
                    HELLO -> Hello(buffer.getInt(), buffer.getInt(), readPasskey(buffer) ?: return null)
                    SIGNAL -> Signal(buffer.getInt(), buffer.getInt(), readBoolean(buffer) ?: return null)
                    AUDIO -> AudioFrame(
                        sessionId = buffer.getInt(),
                        sequence = buffer.getInt(),
                        startOfTransmission = buffer.get().toInt() and 1 == 1,
                        pcm = ByteArray(buffer.remaining()).also { buffer.get(it) },
                    )
                    BYE -> Bye(buffer.getInt())
                    else -> return null
                }
                if (buffer.hasRemaining()) null else message
            } catch (e: BufferUnderflowException) {
                null
            } catch (e: IllegalArgumentException) {
                // A passkey whose bytes aren't valid UTF-8 can grow past the limit once decoded.
                null
            }
        }

        private fun readBoolean(buffer: ByteBuffer): Boolean? = when (buffer.get().toInt()) {
            0 -> false
            1 -> true
            else -> null
        }

        private fun readPasskey(buffer: ByteBuffer): String? {
            val size = buffer.get().toInt() and 0xFF
            if (size > MAX_PASSKEY_BYTES) return null
            val bytes = ByteArray(size).also { buffer.get(it) }
            return String(bytes, Charsets.UTF_8)
        }
    }
}
