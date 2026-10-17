package wifiwalkie.core

/** Produces synthetic down/up transitions useful for deterministic Morse tests. */
object MorseEncoder {
    fun timings(text: String, unitMs: Long, startMs: Long = 0): List<Pair<Boolean, Long>> {
        require(unitMs > 0) { "unitMs must be positive" }
        val result = mutableListOf<Pair<Boolean, Long>>()
        var time = startMs
        val supported = text.uppercase().mapIndexedNotNull { index, char ->
            MorseCode.patternFor(char)?.let { index to (char to it) }
        }

        supported.forEachIndexed { position, (_, pair) ->
            val (_, code) = pair
            code.forEachIndexed { index, mark ->
                result += true to time
                time += if (mark == '.') unitMs else unitMs * 3
                result += false to time
                if (index != code.lastIndex) time += unitMs
            }
            if (position != supported.lastIndex) {
                val originalIndex = supported[position].first
                val nextIndex = supported[position + 1].first
                val hasWordGap = text.substring(originalIndex + 1, nextIndex).any(Char::isWhitespace)
                time += unitMs * if (hasWordGap) 7 else 3
            }
        }
        return result
    }
}
