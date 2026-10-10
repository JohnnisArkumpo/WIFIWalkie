# Wi-fi Walkie: Parallel Tasks Overview

The Morse test program on `main` works: two computers on a LAN, hold space on one and the other's window turns white, over TCP or UDP. The next stage is split into five tasks that can be worked on at the same time.

| Task | Document | Summary | Size |
|------|----------|---------|------|
| A | [A-networking.md](A-networking.md) | Message types for audio and handshake, passkey check, clean disconnect | Large |
| B | [B-desktop-audio.md](B-desktop-audio.md) | Push-to-talk voice between two computers | Large |
| C | [C-android.md](C-android.md) | Android app that talks to the desktop program | Large |
| D | [D-morse-decoder.md](D-morse-decoder.md) | Turn on/off timings into letters on screen | Small |
| E | [E-network-testing.md](E-network-testing.md) | Find out what works on the campus network | Small, no coding |

A, B and C are one person each. D and E go to whoever finishes first, or whoever has a lighter week. E is the most urgent of the small tasks, because its results decide whether we need a relay server.

## Order of work

Each large task starts with a **small first pull request** that adds a shared interface other tasks depend on. Get these merged in the first few days, before the rest of your task.

| First PR | Adds | Unblocks |
|----------|------|----------|
| A-1 | `Message`, new `Transport`, audio in `Session` | B connecting audio to the network |
| B-1 | `AudioSpec`, `AudioInput`, `AudioOutput` | C's Android audio |
| C-1 | Java 17 compatibility for `core` | The Android app using `core` at all |

Until those land, code against the signatures below. They're agreed, so you don't need to wait.

```
A-1, B-1, C-1 merged ──┬──▶ A: handshake, passkey, Bye
                       ├──▶ B: capture, playback, push-to-talk  ──▶ desktop voice works
                       └──▶ C: Android app, then Android audio   ──▶ phone ↔ computer voice works
D and E: no dependencies, start any time
```

## Shared contracts

These are the interfaces more than one task depends on. **Don't change them without telling the team first**, because someone else is coding against them.

### `Message` (core, created in A-1, replaces `SignalMessage`)

```kotlin
sealed class Message {
    data class Hello(val protocolVersion: Int, val sessionId: Int, val passkey: String) : Message()
    data class Signal(val sessionId: Int, val sequence: Int, val on: Boolean) : Message()
    class AudioFrame(val sessionId: Int, val sequence: Int, val pcm: ByteArray) : Message()
    data class Bye(val sessionId: Int) : Message()

    fun encode(): ByteArray

    companion object {
        const val PROTOCOL_VERSION = 1
        const val MAX_SIZE = 1400          // largest encoded message; fits in one UDP packet
        const val MAX_PASSKEY_BYTES = 64
        fun decode(bytes: ByteArray, length: Int = bytes.size): Message?   // null if invalid
    }
}
```

Wire format: the magic byte `W`, a type byte (`H`, `S`, `A` or `B`), then the fields in declared order. Ints are 4 bytes, big-endian. For `Hello`, the passkey is one length byte followed by UTF-8 bytes. `AudioFrame`'s audio fills the rest of the message.

### `Transport` (core, changed in A-1)

```kotlin
interface Transport : Closeable {
    val name: String                          // "TCP" or "UDP"
    fun start(onMessage: (Message) -> Unit)   // onMessage runs on a background thread
    fun send(message: Message)                // any thread; never blocks for long; drops if unreachable
}
```

UDP sends one message per packet. TCP sends a 2-byte length before each message.

### `Session` (core, extended in A-1)

```kotlin
class Session(transport: Transport, listener: Listener, val sessionId: Int = Random.nextInt()) : Closeable {
    interface Listener {                      // called on background threads; return quickly
        fun onPeerConnectedChanged(connected: Boolean)
        fun onPeerSignalChanged(on: Boolean)
        fun onPeerAudio(sequence: Int, pcm: ByteArray) {}   // new; default does nothing
    }
    fun start()
    fun setLocalSignal(on: Boolean)
    fun sendAudio(pcm: ByteArray)             // new; one frame, any thread
    override fun close()
    companion object { const val HEARTBEAT_MS = 100L; const val TIMEOUT_MS = 1000L }
}
```

### Audio (core, created in B-1)

The one audio format everywhere: **16 kHz, mono, 16-bit signed little-endian PCM, in 20 ms frames of 640 bytes.**

```kotlin
object AudioSpec {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BYTES_PER_SAMPLE = 2
    const val FRAME_MS = 20
    const val SAMPLES_PER_FRAME = 320
    const val FRAME_BYTES = 640
}

interface AudioInput : Closeable {           // a microphone
    fun start(onFrame: (ByteArray) -> Unit)  // one FRAME_BYTES frame every 20 ms, background thread
    fun stop()                               // start() may be called again later
}

interface AudioOutput : Closeable {          // a speaker
    fun start()
    fun write(frame: ByteArray)              // blocks until the speaker has room; paces playback
    fun stop()                               // discards anything unplayed
}
```

### Rules for the `core` module

- **`core` must run on Android.** Only use `java.net`, `java.nio`, `java.io`, `java.util.concurrent` and the Kotlin standard library. Never use Swing, `java.awt`, `javax.sound` or Android classes in `core`.
- **`core` code needs unit tests** in `core/src/test`. Run them with `./gradlew test`.

## Files more than one task touches

| File | Touched by | How to avoid trouble |
|------|------------|----------------------|
| `core/.../Session.kt` | A (A-1 and handshake), B (reads only) | Only A edits it. Ask A if you need a change. |
| `desktop/.../Main.kt` | A (passkey prompt), B (start mic and speaker) | Keep your additions in separate functions. |
| `desktop/.../SignalWindow.kt` | B (audio routing), D (decoded text) | Small, separate edits. Pull `main` often. |
| `README.md`, `DESIGN.md` | Everyone | Edit only your own section. |

## How we work

- **One branch per task**, named like `a-networking`, `b-desktop-audio`, `c-android`, `d-morse-decoder`, `e-network-testing`.
- **Merge into `main` only through a pull request** reviewed by one other team member. Don't push straight to `main`.
- **Before opening a pull request**, run `./gradlew build`. It must pass, including tests.
- **Pull `main` into your branch** at least every couple of days so conflicts stay small.
- **Post in Discord** when one of the first PRs (A-1, B-1, C-1) merges, since others are waiting on it.
- **Update DESIGN.md** in the same pull request if your work changes the design.
