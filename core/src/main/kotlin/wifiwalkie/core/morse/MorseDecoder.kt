package wifiwalkie.core.morse

/** Decodes on/off transitions using standard Morse timing and a fixed dit length. */
class MorseDecoder(private val unitMs: Long = DEFAULT_UNIT_MS) {
    private var on = false
    private var changedAt = 0L
    private var lastMarkAt = 0L
    private var pattern = StringBuilder()
    private var letterEmitted = false
    private var wordEmitted = false

    /** Returns newly decoded characters, including a space when a word gap completes. */
    @Synchronized
    fun update(signalOn: Boolean, nowMs: Long = System.currentTimeMillis()): String {
        val result = StringBuilder()
        if (signalOn != on) {
            val duration = (nowMs - changedAt).coerceAtLeast(0)
            if (on) {
                pattern.append(if (duration < unitMs * 2) '.' else '-')
                lastMarkAt = nowMs
                letterEmitted = false
                wordEmitted = false
            } else {
                flushGaps(nowMs, result)
            }
            on = signalOn
            changedAt = nowMs
        } else if (!on) {
            flushGaps(nowMs, result)
        }
        return result.toString()
    }

    @Synchronized
    fun reset() {
        on = false
        changedAt = System.currentTimeMillis()
        lastMarkAt = changedAt
        pattern = StringBuilder()
        letterEmitted = false
        wordEmitted = false
    }

    private fun flushGaps(nowMs: Long, output: StringBuilder) {
        val quiet = nowMs - lastMarkAt
        if (pattern.isNotEmpty() && quiet >= unitMs * 3 && !letterEmitted) {
            output.append(decode(pattern.toString()) ?: '�')
            pattern = StringBuilder()
            letterEmitted = true
        }
        if (quiet >= unitMs * 7 && !wordEmitted) {
            output.append(' ')
            wordEmitted = true
        }
    }

    private fun decode(code: String): Char? = MORSE[code]

    companion object {
        const val DEFAULT_UNIT_MS = 180L
        private val MORSE = mapOf(
            ".-" to 'A', "-..." to 'B', "-.-." to 'C', "-.." to 'D', "." to 'E', "..-." to 'F',
            "--." to 'G', "...." to 'H', ".." to 'I', ".---" to 'J', "-.-" to 'K', ".-.." to 'L',
            "--" to 'M', "-." to 'N', "---" to 'O', ".--." to 'P', "--.-" to 'Q', ".-." to 'R',
            "..." to 'S', "-" to 'T', "..-" to 'U', "...-" to 'V', ".--" to 'W', "-..-" to 'X',
            "-.--" to 'Y', "--.." to 'Z', "-----" to '0', ".----" to '1', "..---" to '2',
            "...--" to '3', "....-" to '4', "....." to '5', "-...." to '6', "--..." to '7',
            "---.." to '8', "----." to '9'
        )
    }
}
