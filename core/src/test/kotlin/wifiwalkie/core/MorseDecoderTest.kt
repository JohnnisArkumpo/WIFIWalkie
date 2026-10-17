package wifiwalkie.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MorseDecoderTest {
    @Test
    fun `decodes SOS and HELLO WORLD at 10 WPM`() {
        assertEquals("SOS", decode("SOS", 120))
        assertEquals("HELLO WORLD", decode("HELLO WORLD", 120))
    }

    @Test
    fun `decodes SOS and HELLO WORLD at 20 WPM`() {
        assertEquals("SOS", decode("SOS", 60))
        assertEquals("HELLO WORLD", decode("HELLO WORLD", 60))
    }

    @Test
    fun `decodes timings with deterministic plus or minus 20 percent variation`() {
        val random = Random(310)
        for (message in listOf("SOS", "HELLO WORLD")) {
            val ideal = MorseEncoder.timings(message, 120)
            val jittered = jitter(ideal, random)
            assertEquals(message, decodeEvents(jittered, 120))
        }
    }

    @Test
    fun `adapts when speed changes from 10 to 18 WPM`() {
        val first = MorseEncoder.timings("THE QUICK ", 120)
        val startSecond = first.last().second + 7 * 120
        val second = MorseEncoder.timings("BROWN FOX JUMPS", 67, startSecond)
        val result = decodeEvents(first + second, 120)
        assertTrue(result.endsWith("FOX JUMPS"), "decoded text was: $result")
    }

    @Test
    fun `unknown patterns decode as asterisk`() {
        val decoder = MorseDecoder(120)
        var time = 0L
        repeat(6) {
            decoder.onSignal(true, time)
            time += 120
            decoder.onSignal(false, time)
            time += 120
        }
        assertEquals("*", decoder.poll(time + 240))
    }

    @Test
    fun `poll emits each letter once`() {
        val decoder = MorseDecoder(120)
        decoder.onSignal(true, 0)
        decoder.onSignal(false, 120)
        assertEquals("E", decoder.poll(360))
        assertEquals("", decoder.poll(410))
        assertEquals("", decoder.poll(600))
    }

    private fun decode(text: String, unitMs: Long): String =
        decodeEvents(MorseEncoder.timings(text, unitMs), unitMs)

    private fun decodeEvents(events: List<Pair<Boolean, Long>>, initialUnitMs: Long): String {
        val decoder = MorseDecoder(initialUnitMs)
        val output = StringBuilder()
        events.forEach { (on, timeMs) ->
            decoder.onSignal(on, timeMs)
            output.append(decoder.poll(timeMs))
        }
        val lastTime = events.lastOrNull()?.second ?: 0
        output.append(decoder.poll(lastTime + decoder.unitMs * 3))
        return output.toString().trimEnd()
    }

    private fun jitter(events: List<Pair<Boolean, Long>>, random: Random): List<Pair<Boolean, Long>> {
        if (events.isEmpty()) return events
        val result = mutableListOf(events.first())
        var time = events.first().second
        for (index in 1 until events.size) {
            val idealGap = events[index].second - events[index - 1].second
            val variation = random.nextDouble(0.8, 1.200001)
            time += (idealGap * variation).toLong().coerceAtLeast(1)
            result += events[index].first to time
        }
        return result
    }
}
