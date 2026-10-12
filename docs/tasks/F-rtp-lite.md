# Task F: RTP Lite (Audio Timing and Playback)

**Assigned to:** ______________
**Branch:** `f-rtp-lite`
**Read first:** [00-overview.md](00-overview.md) (shared contracts), [B-desktop-audio.md](B-desktop-audio.md)

## Goal

Make received audio play smoothly and on time over a network that delivers packets unevenly, late, out of order or not at all.

"RTP Lite" borrows the useful ideas from RTP, the standard protocol behind voice calls, and leaves out the rest. It has two parts:

1. **A few fields in each audio packet** that let the receiver rebuild the original timing.
2. **A playback buffer on the receiver** that keeps delay low, cuts silence, and plays slightly faster, up to a fixed limit, when it falls behind.

## Why this task exists

Testing the Morse program on campus Wi-Fi showed that the network delivers packets unevenly:
- **UDP lost dots.** The "on" and "off" packets of a short press arrived bunched together, so the window never had time to show white.
- **TCP stalled and then burst.** A lost packet held everything up, then several arrived at once.

Wi-Fi power saving makes this worse. Laptops receive packets in bursts about every 100 ms, which is about the length of a Morse dot.

Audio played the moment it arrives would suffer the same way: gaps, then a backlog. Without this task, that backlog becomes permanent extra delay that grows with every network hiccup.

## What we take from RTP, and what we skip

| RTP feature | RTP Lite | Purpose |
|-------------|----------|---------|
| SSRC (sender ID) | `sessionId` | Tells a restarted sender apart from late packets |
| Sequence number | `sequence` | Detects lost, duplicated and out-of-order frames |
| Timestamp | Not sent: frame time = position in the transmission × 20 ms | Every frame is exactly 20 ms, so the sequence is enough |
| Marker bit (start of a talk spurt) | `startOfTransmission` flag | Lets the receiver reset its buffer at each press of push-to-talk |
| Jitter estimate (RFC 3550, section 6.4.1) | Used to choose the buffer's delay | Measures how uneven arrivals are |
| Control reports, payload types, mixing, encryption | Skipped | Not needed for two devices running our own app |

## Part 1: The packet

`Message.AudioFrame` gains a flags byte. **Task A builds this in its first PR (A-1)**, so agree on it with Task A early. It's listed here because this task defines what it means.

```kotlin
class AudioFrame(
    val sessionId: Int,
    val sequence: Int,
    val startOfTransmission: Boolean,   // new: bit 0 of a flags byte; other 7 bits are 0 (reserved)
    val pcm: ByteArray,
) : Message()
```

Wire format: `W`, `A`, sessionId (4 bytes), sequence (4 bytes), flags (1 byte), PCM.

**Sender rules:**
- **The first frame of each push-to-talk press** has `startOfTransmission = true`. All other frames have `false`.
- **`sequence` keeps counting across presses** within a session. It never resets, so a late frame from the previous press can always be recognized.

**Receiver rule for lost start frames:** the flagged frame can be lost over UDP. So a frame also counts as the start of a new transmission if **no audio has arrived for 200 ms or more**.

## Part 2: The playback buffer (in `core`)

Put these in `core/src/main/kotlin/wifiwalkie/core/` so desktop and Android share them. This replaces the simple `JitterBuffer` and `AudioPlayback` that were planned in Task B.

```kotlin
data class PlayoutConfig(
    val minDelayMs: Int = 40,
    val maxDelayMs: Int = 300,
    val initialDelayMs: Int = 80,
    val maxSpeedup: Double = 1.25,        // never play faster than this to catch up
    val silenceThreshold: Int = 300,      // RMS level below which a frame counts as silent; tune by ear
    val talkingHangoverMs: Int = 150,     // how long the talking light stays on after audio stops
)

class PlayoutBuffer(
    private val config: PlayoutConfig = PlayoutConfig(),
    private val onTalkingChanged: (Boolean) -> Unit = {},
) {
    /** A frame arrived from the network. Called on the network thread. */
    fun push(sequence: Int, startOfTransmission: Boolean, frame: ByteArray, arrivalMs: Long)

    /** The next 20 ms to play. Called once per frame by AudioPlayback. Returns null for silence. */
    fun pop(nowMs: Long): ByteArray?

    /** Forget everything, for example when the peer disconnects. */
    fun reset()

    /** Numbers for debugging and for Task E's tests. */
    val stats: PlayoutStats
}

data class PlayoutStats(
    val targetDelayMs: Int,
    val currentDelayMs: Int,
    val jitterMs: Double,
    val framesLost: Int,
    val framesLate: Int,
    val framesDroppedToCatchUp: Int,
)

class AudioPlayback(private val output: AudioOutput, private val buffer: PlayoutBuffer) : Closeable {
    fun start()   // thread loop: output.write(buffer.pop(nowMs) ?: SILENCE)
    override fun close()
}
```

Times are always passed in as arguments, never read from the clock inside `PlayoutBuffer`. That lets tests replay exact, repeatable network conditions. `AudioPlayback` uses `System.nanoTime() / 1_000_000`.

### Required behavior

**1. Start of a transmission.**
- When a transmission starts (see the receiver rule in Part 1), discard anything left from the previous one.
- Wait for the current target delay before playing the first frame.

**2. Order, duplicates and late frames.**
- Play frames in `sequence` order.
- Drop duplicates.
- Drop frames whose turn has already passed.

**3. Lost frames.**
- When a frame's turn comes and it hasn't arrived, return `null` (silence) and move on.
- *Optional, and sounds better:* repeat the previous frame once at half volume before falling back to silence.

**4. Measuring jitter.** Use the interarrival jitter formula from RFC 3550, section 6.4.1:
- For each frame, `D` = (change in arrival time) − (change in sequence × 20 ms) compared with the previous frame.
- Then `J += (|D| − J) / 16`.
- Set the target delay to `clamp(4 × J, minDelayMs, maxDelayMs)` as a starting point, and tune it with real measurements.
- **Raise the delay immediately** when frames start arriving too late, but **lower it slowly** (or only at the start of the next transmission). Lowering it mid-speech causes skips.

**5. Catching up when behind.** When `currentDelayMs` exceeds the target by more than 40 ms, use these steps in order:
- **a. Drop silent frames first.** Frames below `silenceThreshold` (pauses between words) can be skipped without anyone noticing.
- **b. Then speed up, never past `maxSpeedup`.** At 1.25×, drop at most 1 frame in every 5. To avoid clicks, blend over the joins with a short crossfade of about 5 ms (80 samples).
- **c. Last resort:** if delay ever exceeds `maxDelayMs`, drop the oldest frames immediately to get back under it.

*Stretch goal:* in place of dropping whole frames in step b, use **WSOLA** (time-scale modification), which speeds speech up without changing pitch or clicking.

**6. Talking light.**
- Call `onTalkingChanged(true)` when a transmission's first frame plays.
- Call `onTalkingChanged(false)` once no real frame has played for `talkingHangoverMs`.
- Base this on *playback*, not arrival, so the light matches what you hear.
- It's called on the playback thread, so the UI must switch to its own thread.

**7. Thread safety.** `push` and `pop` run on different threads.

## Part 3: A network simulator (for testing)

Real campus conditions are hard to reproduce on demand, so build them in.

```kotlin
class SimulatedNetwork(
    private val inner: Transport,
    private val delayMs: Int = 20,
    private val jitterMs: Int = 0,         // random extra delay, 0..jitterMs
    private val lossRate: Double = 0.0,    // 0.02 = 2% of packets lost
    private val burstIntervalMs: Int = 0,  // >0: hold packets and release them in bursts (like Wi-Fi power saving)
    private val seed: Long = 1,
) : Transport
```

Put it in `core/src/main/` so the desktop program can use it too. A command-line option such as `--simulate campus` would let anyone hear campus conditions on one computer using `127.0.0.1`.

## Tests

`PlayoutBufferTest` feeds in simulated arrival times. It must cover:

- **Perfect timing:** the delay stays at the minimum, and nothing is dropped.
- **Random jitter of ±50 ms (fixed seed):** no gaps, and the delay settles near the target.
- **The campus pattern** (frames arrive in bursts every 100 ms): plays continuously.
- **5% loss:** the right number of silences, with no growing delay.
- **Duplicates and reordering:** each frame plays once, in order.
- **A 500 ms stall followed by a burst** (TCP-style): the delay returns to target within about 3 seconds, and catching up never exceeds 1.25× in any 1-second window.
- **Start flag:** a flagged frame resets the buffer, and a lost flag is handled by the 200 ms gap rule.
- **Talking light:** it turns on and off at the right times.

## How this connects to other tasks

| Task | What changes |
|------|--------------|
| A (networking) | `AudioFrame` gains `startOfTransmission` in A-1. `Session.sendAudio(pcm, startOfTransmission)` and `Listener.onPeerAudio(sequence, startOfTransmission, pcm)` pass it through. |
| B (desktop audio) | No longer builds `JitterBuffer` or `AudioPlayback`; uses this task's classes instead. Sends the first frame of each press with the flag. Drives the "they're talking" light from `onTalkingChanged`. Until this task lands, B can test with a stand-in that plays frames as they arrive. |
| C (Android) | Reuses `PlayoutBuffer` and `AudioPlayback` from `core` unchanged. |
| E (network testing) | Can record `PlayoutStats` on campus to tune the defaults. |

The overview's shared contracts and the A and B documents need updating to match this task. That isn't done yet.

## Order of work

You can start right away: Parts 2 and 3 and all the tests need nothing from other tasks. Connecting it to real audio waits for A-1 (the packet field) and B (microphone and speaker).

## Done when

- [ ] All the tests listed above pass.
- [ ] With `SimulatedNetwork` set to campus-like conditions (bursts every 100 ms, 2% loss), voice between two copies on one computer sounds continuous, and the delay doesn't grow over a 5-minute conversation.
- [ ] Between two computers on real Wi-Fi, voice sounds continuous, and printed `PlayoutStats` show the delay staying between `minDelayMs` and `maxDelayMs`.
- [ ] DESIGN.md has an "Audio timing (RTP Lite)" section.

## Out of scope

Opus compression (a later task, which is where "encoder/decoder" belongs), echo cancellation, control reports, and encryption.
