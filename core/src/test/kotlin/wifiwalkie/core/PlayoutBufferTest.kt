package wifiwalkie.core

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayoutBufferTest {

    /** One frame arriving from the network. */
    private data class Arrival(val atMs: Long, val sequence: Int, val start: Boolean = false)

    /** What one call to pop returned: the frame's sequence, or null for silence. */
    private data class Played(val atMs: Long, val sequence: Int?)

    private val talkingEvents = mutableListOf<Pair<Long, Boolean>>()
    private var clock = 0L
    private val buffer = PlayoutBuffer(PlayoutConfig(initialDelayMs = 200, maxDelayMs = 400, talkingHangoverMs = 150)) {
        talkingEvents += clock to it
    }

    /** Feeds [arrivals] in and pops every 20 ms from time 0 to [untilMs], like AudioPlayback does. */
    private fun run(arrivals: List<Arrival>, untilMs: Long): List<Played> {
        val pending = arrivals.sortedBy { it.atMs }.toMutableList()
        val played = mutableListOf<Played>()
        for (t in 0..untilMs step 20) {
            clock = t
            while (pending.isNotEmpty() && pending.first().atMs <= t) {
                val a = pending.removeAt(0)
                buffer.push(a.sequence, a.start, frameOf(a.sequence), a.atMs)
            }
            played += Played(t, buffer.pop(t)?.let(::sequenceOf))
        }
        return played
    }

    /** A transmission of [count] frames from [firstSequence], arriving every 20 ms from [startMs]. */
    private fun transmission(firstSequence: Int, count: Int, startMs: Long = 0) =
        (0 until count).map { Arrival(startMs + it * 20L, firstSequence + it, start = it == 0) }

    private fun List<Played>.sequences() = mapNotNull { it.sequence }

    private fun List<Played>.firstPlayOf(sequence: Int) = first { it.sequence == sequence }.atMs

    @Test
    fun `perfect timing plays every frame in order after the initial delay`() {
        val played = run(transmission(1, 50), untilMs = 2000)
        assertEquals((1..50).toList(), played.sequences())
        assertEquals(200, played.firstPlayOf(1))
        // No gaps once playing.
        assertEquals(980, played.firstPlayOf(50) - played.firstPlayOf(1))
    }

    @Test
    fun `reordered and duplicated frames play once each in order`() {
        val arrivals = listOf(
            Arrival(0, 1, start = true), Arrival(20, 3), Arrival(25, 2), Arrival(30, 2),
            Arrival(60, 4), Arrival(80, 5), Arrival(85, 5), Arrival(100, 6),
        )
        assertEquals((1..6).toList(), run(arrivals, untilMs = 1000).sequences())
    }

    @Test
    fun `a lost frame plays as one silence and timing stays the same`() {
        val played = run(transmission(1, 20).filter { it.sequence != 5 }, untilMs = 1000)
        assertEquals((1..20).filter { it != 5 }, played.sequences())
        assertEquals(null, played.first { it.atMs == played.firstPlayOf(4) + 20 }.sequence)
        assertEquals(played.firstPlayOf(4) + 40, played.firstPlayOf(6))
    }

    @Test
    fun `a stall shorter than the delay is absorbed without gaps`() {
        // Frames 10 to 14 are held up and arrive together, 150 ms late.
        val arrivals = transmission(1, 30).map { if (it.sequence in 10..14) it.copy(atMs = 14 * 20L + 10) else it }
        val played = run(arrivals, untilMs = 1500)
        assertEquals((1..30).toList(), played.sequences())
        assertEquals(29 * 20L, played.firstPlayOf(30) - played.firstPlayOf(1))
    }

    @Test
    fun `a long stall never leaves more than the maximum delay behind`() {
        // 600 ms of frames held up, then delivered in one burst, then normal again.
        val arrivals = transmission(1, 150).map { if (it.atMs in 1000L..1600L) it.copy(atMs = 1610) else it }
        val played = run(arrivals, untilMs = 4000)
        val arrivalOf = arrivals.associate { it.sequence to it.atMs }
        // From a second after the burst, every frame plays within the maximum delay of arriving.
        for (p in played) {
            val sequence = p.sequence ?: continue
            if (arrivalOf.getValue(sequence) >= 2600) assertTrue(p.atMs - arrivalOf.getValue(sequence) <= 400, "frame $sequence was late")
        }
        assertTrue(150 in played.sequences())
    }

    @Test
    fun `a new transmission waits for the delay again`() {
        val arrivals = transmission(1, 10) + transmission(500, 10, startMs = 1000)
        val played = run(arrivals, untilMs = 2000)
        assertEquals((1..10) + (500..509), played.sequences())
        assertEquals(1200, played.firstPlayOf(500))
    }

    @Test
    fun `a lost start flag is covered by the silence before it`() {
        val arrivals = transmission(1, 10) + transmission(500, 10, startMs = 1000).drop(1)
        val played = run(arrivals, untilMs = 2000)
        assertEquals((1..10) + (501..509), played.sequences())
        assertEquals(1220, played.firstPlayOf(501))
    }

    @Test
    fun `a late copy of the first frame does not restart playback`() {
        val arrivals = transmission(1, 30) + Arrival(400, 1, start = true)
        assertEquals((1..30).toList(), run(arrivals, untilMs = 1500).sequences())
    }

    @Test
    fun `talking light follows what is played`() {
        val played = run(transmission(1, 10), untilMs = 1000)
        val lastPlayed = played.firstPlayOf(10)
        assertEquals(listOf(200L to true, lastPlayed + 160 to false), talkingEvents)
    }

    @Test
    fun `reset forgets everything and turns the light off`() {
        run(transmission(1, 12), untilMs = 240)
        buffer.reset()
        assertEquals(listOf(200L to true, 240L to false), talkingEvents)
        assertEquals(null, buffer.pop(260))
    }

    private fun frameOf(sequence: Int) = ByteBuffer.allocate(4).putInt(sequence).array()

    private fun sequenceOf(frame: ByteArray) = ByteBuffer.wrap(frame).getInt()
}
