package wifiwalkie.core

/** Decodes timed key transitions. All times are supplied by the caller in milliseconds. */
class MorseDecoder(initialUnitMs: Long = 120) {
    private var estimatedUnitMs = initialUnitMs.coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)
    private var signalOn = false
    private var stateChangedAtMs: Long? = null
    private var lastMarkEndedAtMs: Long? = null
    private var pattern = StringBuilder()
    private var letterEmitted = false
    private var wordSpaceEmitted = false
    private val readyText = StringBuilder()

    val currentPattern: String
        @Synchronized get() = pattern.toString()

    val unitMs: Long
        @Synchronized get() = estimatedUnitMs

    /** Records a key transition; repeated values are ignored. */
    @Synchronized
    fun onSignal(on: Boolean, timeMs: Long) {
        if (on == signalOn) return
        if (on) finishGaps(timeMs)

        val changedAt = stateChangedAtMs
        if (signalOn && changedAt != null) {
            val markLength = (timeMs - changedAt).coerceAtLeast(0)
            val isDot = markLength < estimatedUnitMs * 2
            pattern.append(if (isDot) '.' else '-')
            val observedUnit = if (isDot) markLength else markLength / 3
            estimatedUnitMs = (estimatedUnitMs * (1.0 - ADAPTATION_RATE) + observedUnit * ADAPTATION_RATE)
                .toLong().coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)
            lastMarkEndedAtMs = timeMs
            letterEmitted = false
            wordSpaceEmitted = false
        }

        signalOn = on
        stateChangedAtMs = timeMs
    }

    /** Emits a completed letter or word once its key-up gap reaches the required length. */
    @Synchronized
    fun poll(timeMs: Long): String {
        finishGaps(timeMs)
        val result = readyText.toString()
        readyText.setLength(0)
        return result
    }

    /** Clears decoder state, using [timeMs] as the new timing origin. */
    @Synchronized
    fun reset(timeMs: Long = 0) {
        signalOn = false
        stateChangedAtMs = timeMs
        lastMarkEndedAtMs = null
        pattern = StringBuilder()
        letterEmitted = false
        wordSpaceEmitted = false
        readyText.setLength(0)
    }

    private fun finishGaps(timeMs: Long) {
        if (signalOn) return
        val markEndedAt = lastMarkEndedAtMs ?: return
        val keyUpFor = (timeMs - markEndedAt).coerceAtLeast(0)
        if (pattern.isNotEmpty() && keyUpFor >= estimatedUnitMs * LETTER_GAP_UNITS && !letterEmitted) {
            readyText.append(MorseCode.charFor(pattern.toString()) ?: '*')
            pattern.setLength(0)
            letterEmitted = true
        }
        if (keyUpFor >= estimatedUnitMs * WORD_GAP_UNITS && !wordSpaceEmitted) {
            readyText.append(' ')
            wordSpaceEmitted = true
        }
    }

    companion object {
        private const val ADAPTATION_RATE = 0.4
        private const val LETTER_GAP_UNITS = 2
        private const val WORD_GAP_UNITS = 5
        private const val MIN_UNIT_MS = 40L
        private const val MAX_UNIT_MS = 400L
    }
}
