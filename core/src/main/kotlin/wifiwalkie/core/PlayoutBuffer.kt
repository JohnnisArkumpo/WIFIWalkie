package wifiwalkie.core

import java.util.TreeMap

/**
 * Settings for [PlayoutBuffer]. The delays are higher than Task F's suggested
 * defaults because pings on campus Wi-Fi spiked to over 450 ms.
 */
data class PlayoutConfig(
    /** How much audio to collect before a transmission starts playing. */
    val initialDelayMs: Int = 200,
    /** If more than this much audio is waiting, skip ahead to get back to [initialDelayMs]. */
    val maxDelayMs: Int = 400,
    /** How long the talking light stays on after audio stops. */
    val talkingHangoverMs: Int = 150,
)

/**
 * Smooths out uneven network timing for one sender's audio.
 *
 * Each transmission waits [PlayoutConfig.initialDelayMs] before it starts
 * playing, then plays one frame per [pop] in sequence order. Duplicates and
 * frames whose turn has passed are dropped, and a frame that hasn't arrived
 * by its turn plays as silence. If audio piles up past
 * [PlayoutConfig.maxDelayMs] (after a network stall), it skips ahead.
 *
 * This is the simple fixed-delay version. Task F replaces the inside with an
 * adaptive delay; the public methods stay the same.
 *
 * Times are passed in rather than read from the clock, so tests can replay exact
 * network conditions. [push] and [pop] may run on different threads.
 */
class PlayoutBuffer(
    private val config: PlayoutConfig = PlayoutConfig(),
    private val onTalkingChanged: (Boolean) -> Unit = {},
) {
    private val initialFrames = config.initialDelayMs / AudioSpec.FRAME_MS
    private val maxFrames = config.maxDelayMs / AudioSpec.FRAME_MS

    // Guarded by `this`.
    private val waiting = TreeMap<Int, ByteArray>()
    private var nextSequence = 0
    private var lastArrivalMs: Long? = null
    /** When the current transmission starts playing, or null when there's nothing to play. */
    private var playFromMs: Long? = null
    private var playing = false
    private var talking = false
    private var lastPlayedMs = 0L

    /** A frame arrived from the network. */
    @Synchronized
    fun push(sequence: Int, startOfTransmission: Boolean, frame: ByteArray, arrivalMs: Long) {
        val previousArrival = lastArrivalMs
        lastArrivalMs = arrivalMs
        if (playing && sequence < nextSequence) return   // its turn has passed

        // The flagged first frame can be lost over UDP, so a long silence or an idle buffer also means a new transmission.
        val newTransmission = startOfTransmission || playFromMs == null ||
            (previousArrival != null && arrivalMs - previousArrival >= NEW_TRANSMISSION_GAP_MS)
        if (newTransmission) {
            // Keep any later frames of this transmission that overtook its first one.
            waiting.headMap(sequence).clear()
            nextSequence = sequence
            if (playFromMs == null || playing) {
                playFromMs = arrivalMs + config.initialDelayMs
                playing = false
            }
        } else if (sequence < nextSequence) {
            nextSequence = sequence   // not playing yet, and an earlier frame of this transmission turned up
        }
        waiting.putIfAbsent(sequence, frame)

        if (waiting.lastKey() - nextSequence + 1 > maxFrames) {
            nextSequence = waiting.lastKey() - initialFrames + 1
            waiting.headMap(nextSequence).clear()
        }
    }

    /** The next 20 ms to play, or null for silence. Called once per frame by [AudioPlayback]. */
    fun pop(nowMs: Long): ByteArray? {
        var talkingChanged: Boolean? = null
        val frame = synchronized(this) {
            val startAt = playFromMs
            // Start once the delay has passed, or straight away if a burst already brought more than that much audio.
            if (!playing && startAt != null && (nowMs >= startAt || waiting.size > initialFrames)) playing = true

            val frame = if (playing) waiting.remove(nextSequence++) else null
            if (frame != null) {
                lastPlayedMs = nowMs
                if (!talking) {
                    talking = true
                    talkingChanged = true
                }
            } else if (talking && nowMs - lastPlayedMs >= config.talkingHangoverMs) {
                talking = false
                talkingChanged = false
            }

            // The sender has gone quiet and everything has played: wait for the next transmission.
            val lastArrival = lastArrivalMs
            if (playing && waiting.isEmpty() && lastArrival != null && nowMs - lastArrival >= NEW_TRANSMISSION_GAP_MS) {
                playing = false
                playFromMs = null
            }
            frame
        }
        talkingChanged?.let(onTalkingChanged)
        return frame
    }

    /** Forget everything, for example when the peer disconnects. */
    fun reset() {
        val wasTalking = synchronized(this) {
            waiting.clear()
            lastArrivalMs = null
            playFromMs = null
            playing = false
            talking.also { talking = false }
        }
        if (wasTalking) onTalkingChanged(false)
    }

    private companion object {
        /** No audio for this long means the next frame starts a new transmission (Task F's rule). */
        const val NEW_TRANSMISSION_GAP_MS = 200
    }
}
