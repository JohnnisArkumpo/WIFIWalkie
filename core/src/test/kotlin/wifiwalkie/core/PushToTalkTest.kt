package wifiwalkie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushToTalkTest {

    /** A microphone the test drives by hand. */
    private class FakeInput : AudioInput {
        var onFrame: ((ByteArray) -> Unit)? = null
        var closed = false
        override fun start(onFrame: (ByteArray) -> Unit) { this.onFrame = onFrame }
        override fun stop() { onFrame = null }
        override fun close() { closed = true }
        fun speak(fill: Int) = onFrame?.invoke(ByteArray(AudioSpec.FRAME_BYTES) { fill.toByte() })
    }

    private val input = FakeInput()
    private val sent = mutableListOf<String>()
    private val talk = PushToTalk(input) { pcm, start -> sent += "${pcm[0]}${if (start) " start" else ""}" }

    @Test
    fun `only the first frame of each press is flagged`() {
        talk.setTalking(true)
        input.speak(1); input.speak(2); input.speak(3)
        talk.setTalking(false)
        input.speak(9)   // the microphone is off, so nothing is sent
        talk.setTalking(true)
        input.speak(4); input.speak(5)
        assertEquals(listOf("1 start", "2", "3", "4 start", "5"), sent)
    }

    @Test
    fun `repeated presses and releases are ignored`() {
        talk.setTalking(true)
        input.speak(1)
        talk.setTalking(true)
        input.speak(2)
        assertEquals(listOf("1 start", "2"), sent)
        assertTrue(talk.talking)
        talk.setTalking(false)
        talk.setTalking(false)
        assertFalse(talk.talking)
    }

    @Test
    fun `close releases the microphone`() {
        talk.close()
        assertTrue(input.closed)
    }
}
