# Task B: Desktop Audio

**Assigned to:** John
**Branch:** `b-desktop-audio`
**Read first:** [00-overview.md](00-overview.md) (shared contracts), [F-rtp-lite.md](F-rtp-lite.md) (playback buffer), [DESIGN.md](../../DESIGN.md)

## Goal

Push-to-talk voice between two computers on the desktop: hold space to talk, and the other computer plays your voice and shows that you're talking.

This task is the desktop's **microphone, speaker and push-to-talk**. The smart part of playback (the buffer that smooths out network timing) is **Task F**, shared with Android. This task connects to it.

The work splits into a **sending side** and a **receiving side**, which meet only at the `AudioFrame` message. Build and test each one on its own.

## Part 1: First pull request (B-1). Do this first, within the first few days.

Task C (Android) codes against these interfaces, so merge them early. Create them exactly as specified in the overview, in `core/src/main/kotlin/wifiwalkie/core/`:

- `AudioSpec.kt`: the format constants
- `AudioInput.kt`: the microphone interface
- `AudioOutput.kt`: the speaker interface

These are interfaces and constants only, with no implementations. It's a small PR.

## Part 2: Desktop microphone and speaker (in `desktop`)

Use Java Sound (`javax.sound.sampled`), which is built into the JDK. The format is:

```kotlin
val format = AudioFormat(16_000f, 16, 1, true, false)   // 16 kHz, 16-bit, mono, signed, little-endian
```

- **`JavaSoundInput : AudioInput`:** open a `TargetDataLine` with `AudioSystem.getTargetDataLine(format)`. On a background thread, loop `line.read(buffer, 0, AudioSpec.FRAME_BYTES)` and pass a *copy* of each full frame to `onFrame`.
- **`JavaSoundOutput : AudioOutput`:** open a `SourceDataLine` with `AudioSystem.getSourceDataLine(format)`. Keep its buffer small (about 4 frames), or it adds delay.

**Try this before connecting to the network:** a tiny test `main` that feeds `JavaSoundInput` straight into `JavaSoundOutput` on one computer. Use headphones, or you'll get feedback squeal. If you hear yourself clearly with little delay, the hardware half is done.

## Part 3: Sending side

Needs A-1 merged, for `Session.sendAudio`.

- **Push-to-talk on space.** Holding space starts the microphone, and releasing it stops it.
- **Mark the first frame of each press:**
  ```kotlin
  var first = true
  audioInput.start { frame ->
      session.sendAudio(frame, startOfTransmission = first)
      first = false
  }
  ```
- **Move the Morse signal to a different key** (suggested: `M`), so voice and Morse can be tested separately. Space currently sends the signal. Turn `SpaceBarInput` into a general `KeyHoldInput(window, keyCode, onChange)` and use it for both keys. Tell Task D, since its decoder reads the signal.
- **If there's no microphone,** or the line won't open, print a warning and keep running. The Morse signal must still work on a computer without audio.

## Part 4: Receiving side

Uses Task F's `PlayoutBuffer` and `AudioPlayback` from `core`. **Until F lands,** test with a stand-in: a loop that writes each received frame straight to `JavaSoundOutput` as it arrives. It will sound choppy on bad Wi-Fi, which is expected and exactly what F fixes.

With F's classes:

- **When the program starts,** create one `PlayoutBuffer` and an `AudioPlayback(JavaSoundOutput, buffer)`, and start the playback.
- **`onPeerAudio(peerId, sequence, startOfTransmission, pcm)`:** `buffer.push(sequence, startOfTransmission, pcm, nowMs)`, where `nowMs` is `System.nanoTime() / 1_000_000`.
- **`onPeerConnectedChanged(peerId, connected)`:** `buffer.reset()`.
- **"They're talking" light:** drive it from the buffer's `onTalkingChanged` callback, not from the Morse signal. It's based on what's actually playing, so it matches what you hear. Show it differently from the Morse signal's white window, for example a green border or "TALKING" in the status line. The callback runs on the playback thread, so switch to the Swing thread with `SwingUtilities.invokeLater`.
- **If there's no speaker,** print a warning and keep running without playback.

**Keep all the playback setup in one function or small class** (for example `DesktopAudio`). That's where Task G will make its change later.

Keep your other changes to `Main.kt` and `SignalWindow.kt` in their own functions or small classes, because tasks A and D edit those files too.

## Note: Task G (group calls) comes later

G adds group calls: one `PlayoutBuffer` per sender, mixed into one stream by an `AudioMixer` that replaces `AudioPlayback`. The mixer itself lives in `core` (Task G), not here. G's effect on this task is small:
- **Swapping `AudioPlayback` for `AudioMixer`** in your playback setup. That's why it should live in one place.
- **Replacing the single talking light** with a "who's talking" list, using `peerId` and the display names G adds.

For now, ignore `peerId` and assume one peer.

## Optional: Beeps

A short beep when you start and stop talking, and when the other person does, like a real walkie-talkie. Add a `Beeper` class in `desktop` that generates a sine tone (for example 880 Hz, 80 ms) as PCM frames and plays it through the same output.

## Done when

- [ ] B-1 is merged.
- [ ] The local microphone-to-speaker test sounds clear.
- [ ] Two computers on the same LAN can talk with push-to-talk over both UDP and TCP.
  - First with the stand-in.
  - Then, once Task F lands, with F's buffer.
- [ ] The talking light comes on while the other person's voice plays and goes off shortly after.
- [ ] The Morse signal still works on its own key, including on a computer without a microphone.
- [ ] README explains push-to-talk and the Morse key, plus a note to use headphones when testing two computers in the same room.
- [ ] Note in your PR description how TCP and UDP sounded compared to each other. That's useful data for the project.

## Out of scope

- The playback buffer (Task F)
- Group calls and mixing (Task G)
- Opus compression (later)
- Echo cancellation
- Android audio (Task C)
