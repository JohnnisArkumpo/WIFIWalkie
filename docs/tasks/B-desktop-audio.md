# Task B: Desktop Audio

**Assigned to:** ______________
**Branch:** `b-desktop-audio`
**Read first:** [00-overview.md](00-overview.md) (shared contracts), [DESIGN.md](../../DESIGN.md)

## Goal

Push-to-talk voice between two computers. Holding space already turns the other computer's window white. After this task it also sends your voice, and the other computer plays it.

## Part 1: First pull request (B-1). Do this first, within the first few days.

Task C (Android) codes against these interfaces, so merge them early. Create them exactly as specified in the overview, in `core/src/main/kotlin/wifiwalkie/core/`:

- `AudioSpec.kt`: the format constants
- `AudioInput.kt`: the microphone interface
- `AudioOutput.kt`: the speaker interface

These are interfaces and constants only, with no implementations. It's a small PR.

## Part 2: Shared audio logic (in `core`, so Android reuses it)

### `JitterBuffer`

Network packets arrive unevenly, late, out of order or not at all. The jitter buffer smooths that out. (This is the "pop" from the original design document.)

```kotlin
class JitterBuffer(private val startFrames: Int = 3, private val maxFrames: Int = 10) {
    fun push(sequence: Int, frame: ByteArray)   // called from the network thread
    fun pop(): ByteArray?                       // called every 20 ms by playback; null means "play silence"
    fun reset()                                 // forget everything (peer disconnected or restarted)
}
```

Required behavior:

- **Waiting to start:** `pop()` returns `null` until `startFrames` frames are buffered (60 ms). That gives later frames time to arrive.
- **Ordering:** frames come out in `sequence` order, whatever order they arrived in.
- **Duplicates and late frames:** drop any frame whose sequence was already played or is already buffered.
- **Gaps:** if the next sequence is missing but later frames are waiting, return `null` for the missing frame (silence) and move on. Don't wait forever.
- **Capping delay:** if more than `maxFrames` frames are buffered (200 ms), drop the oldest so delay can't keep growing.
- **Draining:** once the buffer runs dry (the peer stopped talking), go back to waiting for `startFrames` frames before playing again.
- **Thread safety:** `push` and `pop` are called from different threads.

### `AudioPlayback`

```kotlin
class AudioPlayback(private val output: AudioOutput, private val buffer: JitterBuffer) : Closeable {
    fun start()   // starts a thread that loops: output.write(buffer.pop() ?: SILENCE)
    override fun close()
}
```

`SILENCE` is a `ByteArray(AudioSpec.FRAME_BYTES)` of zeros. `output.write` blocks until the speaker has room, so the loop runs at exactly real time without any sleeping.

### Tests (in `core/src/test`)

`JitterBufferTest` must cover:
- in-order frames
- out-of-order frames
- a duplicate
- a frame that arrives after its slot was already played
- a gap
- overflow past `maxFrames`
- `reset()`
- going back to waiting after the buffer drains

## Part 3: Desktop implementations (in `desktop`)

Use Java Sound (`javax.sound.sampled`), which is built into the JDK. The format is:

```kotlin
val format = AudioFormat(16_000f, 16, 1, true, false)   // 16 kHz, 16-bit, mono, signed, little-endian
```

- **`JavaSoundInput : AudioInput`:** open a `TargetDataLine` with `AudioSystem.getTargetDataLine(format)`. On a background thread, loop `line.read(buffer, 0, AudioSpec.FRAME_BYTES)` and pass a *copy* of each full frame to `onFrame`.
- **`JavaSoundOutput : AudioOutput`:** open a `SourceDataLine` with `AudioSystem.getSourceDataLine(format)`. Keep its buffer small (about 4 frames), or it adds delay.

**Try this before connecting to the network:** a tiny test `main` that feeds `JavaSoundInput` straight into `JitterBuffer` → `AudioPlayback` → `JavaSoundOutput` on one computer. Use headphones, or you'll get feedback squeal. If you hear yourself clearly with little delay, the audio half is done.

## Part 4: Connecting it to the network

Needs A-1 merged, for `Session.sendAudio` and `Listener.onPeerAudio`.

- **Holding space:** `session.setLocalSignal(true)` (as now), and `audioInput.start { session.sendAudio(it) }`.
- **Releasing space:** `setLocalSignal(false)` and `audioInput.stop()`.
- **`onPeerAudio(sequence, pcm)`:** `jitterBuffer.push(sequence, pcm)`.
- **`onPeerConnectedChanged(...)`:** `jitterBuffer.reset()`.
- **Start `AudioPlayback`** when the program starts, and keep it running.
- **If there's no microphone or speaker,** or the line won't open, print a warning and keep running with the on/off signal only. The Morse test must still work on a computer without audio.
- **The window turns white while the other person talks.** That's the existing signal and doubles as a "they're talking" light, so leave it in.

Keep your changes to `Main.kt` and `SignalWindow.kt` in their own functions or small classes, because tasks A and D edit those files too.

## Optional: Beeps

A short beep when you start and stop talking, and when the other person does, like a real walkie-talkie. Add a `Beeper` class in `desktop` that generates a sine tone (for example 880 Hz, 80 ms) as PCM frames and plays it through the same output.

## Done when

- [ ] B-1 is merged.
- [ ] `JitterBuffer` passes the tests listed above.
- [ ] Two computers on the same LAN can talk to each other with push-to-talk over both UDP and TCP, with no noticeable delay.
- [ ] A computer without a microphone still runs the Morse test.
- [ ] README explains push-to-talk, plus a note to use headphones when testing two computers in the same room.
- [ ] Note in your PR description how TCP and UDP sounded compared to each other. That's useful data for the project.

## Out of scope

Opus compression (later), echo cancellation, and Android audio (task C).
