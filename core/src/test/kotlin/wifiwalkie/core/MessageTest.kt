package wifiwalkie.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class MessageTest {

    @Test
    fun `hello round-trips`() {
        val hello = Message.Hello(Message.PROTOCOL_VERSION, -12345, "tacos 🌮")
        assertEquals(hello, Message.decode(hello.encode()))
    }

    @Test
    fun `hello with an empty passkey round-trips`() {
        val hello = Message.Hello(Message.PROTOCOL_VERSION, 7, "")
        assertEquals(hello, Message.decode(hello.encode()))
    }

    @Test
    fun `signal round-trips both ways`() {
        for (on in listOf(true, false)) {
            val signal = Message.Signal(42, Int.MAX_VALUE, on)
            assertEquals(signal, Message.decode(signal.encode()))
        }
    }

    @Test
    fun `audio frame round-trips with both start flags`() {
        val pcm = ByteArray(640) { it.toByte() }
        for (start in listOf(true, false)) {
            val decoded = assertIs<Message.AudioFrame>(Message.decode(Message.AudioFrame(9, 1000, start, pcm).encode()))
            assertEquals(9, decoded.sessionId)
            assertEquals(1000, decoded.sequence)
            assertEquals(start, decoded.startOfTransmission)
            assertContentEquals(pcm, decoded.pcm)
        }
    }

    @Test
    fun `largest audio frame fits in MAX_SIZE`() {
        val pcm = ByteArray(Message.MAX_SIZE - 11)
        val bytes = Message.AudioFrame(1, 1, false, pcm).encode()
        assertEquals(Message.MAX_SIZE, bytes.size)
        assertIs<Message.AudioFrame>(Message.decode(bytes))
        assertFailsWith<IllegalArgumentException> { Message.AudioFrame(1, 1, false, ByteArray(pcm.size + 1)) }
    }

    @Test
    fun `bye round-trips`() {
        val bye = Message.Bye(Int.MIN_VALUE)
        assertEquals(bye, Message.decode(bye.encode()))
    }

    @Test
    fun `wire format matches the shared contract`() {
        assertContentEquals(
            byteArrayOf('W'.code.toByte(), 'S'.code.toByte(), 0, 0, 1, 2, 0, 0, 0, 3, 1),
            Message.Signal(0x0102, 3, true).encode(),
        )
        // Reserved flag bits are written as 0 and ignored when read.
        val audio = Message.AudioFrame(0, 0, true, byteArrayOf(5)).encode()
        assertEquals(1, audio[10].toInt())
        audio[10] = 0b1111_1110.toByte()
        assertEquals(false, assertIs<Message.AudioFrame>(Message.decode(audio)).startOfTransmission)
    }

    @Test
    fun `decode uses only the first length bytes`() {
        val bytes = Message.Bye(5).encode() + ByteArray(20)
        assertEquals(Message.Bye(5), Message.decode(bytes, 6))
    }

    @Test
    fun `junk returns null`() {
        val signal = Message.Signal(1, 2, true).encode()
        val hello = Message.Hello(1, 2, "abc").encode()
        val junk = listOf(
            byteArrayOf(),
            byteArrayOf('W'.code.toByte()),
            signal.copyOf(signal.size - 1),                             // too short
            signal + 0,                                                 // leftover byte
            signal.copyOf().also { it[0] = 'X'.code.toByte() },          // wrong magic
            signal.copyOf().also { it[1] = 'Z'.code.toByte() },          // unknown type
            signal.copyOf().also { it[10] = 2 },                         // on isn't 0 or 1
            hello.copyOf().also { it[10] = 4 },                          // passkey length past the end
            hello.copyOf().also { it[10] = (Message.MAX_PASSKEY_BYTES + 1).toByte() },
            ByteArray(Message.MAX_SIZE + 1).also { it[0] = 'W'.code.toByte(); it[1] = 'A'.code.toByte() },
            ByteArray(500) { it.toByte() },
        )
        for (bytes in junk) assertNull(Message.decode(bytes), "decoded ${bytes.size} junk bytes")
        assertNull(Message.decode(signal, signal.size + 1), "length past the end of the array")
    }

    @Test
    fun `passkey bytes that are not UTF-8 never throw`() {
        val passkey = ByteArray(Message.MAX_PASSKEY_BYTES) { 0xFF.toByte() }
        val bytes = byteArrayOf('W'.code.toByte(), 'H'.code.toByte(), 0, 0, 0, 1, 0, 0, 0, 2, passkey.size.toByte()) + passkey
        Message.decode(bytes)   // null or a Hello; either is fine, as long as it doesn't throw
    }
}
