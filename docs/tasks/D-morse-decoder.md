# Task D: Morse Decoder

**Assigned to:** Christopher
**Branch:** `d-morse-decoder`
**Read first:** [00-overview.md](00-overview.md), [DESIGN.md](../../DESIGN.md)

## Goal

Turn the on/off signal into letters. When the other person taps out `.... ..` with the space bar, the window shows **HI**.

This task has **no dependencies**: you can start immediately, and almost all of it is pure logic you can unit test without a network.

## Morse timing rules

Everything is measured in **units**. One unit is the length of a dot.

| Element | Length |
|---------|--------|
| Dot (key down) | 1 unit |
| Dash (key down) | 3 units |
| Gap between dots and dashes inside a letter | 1 unit |
| Gap between letters | 3 units |
| Gap between words | 7 units |

A unit is `1200 / WPM` milliseconds. At 10 words per minute that's 120 ms, and at 20 WPM it's 60 ms. Humans are sloppy, so decide using the midpoints between these lengths, not exact values:

- Key down for **under 2 units** is a dot; otherwise it's a dash.
- Key up for **2 units or more** ends the letter.
- Key up for **5 units or more** ends the word (adds a space).

## Part 1: `core` classes (in `core/src/main/kotlin/wifiwalkie/core/`)

### `MorseCode`

```kotlin
object MorseCode {
    fun patternFor(char: Char): String?     // 'A' -> ".-", null if unknown
    fun charFor(pattern: String): Char?     // ".-" -> 'A', null if unknown
}
```

Cover A–Z, 0–9, and the punctuation `. , ? / = -`.

### `MorseDecoder`

```kotlin
class MorseDecoder(initialUnitMs: Long = 120) {
    /** The key went down (on = true) or up (on = false) at timeMs. */
    fun onSignal(on: Boolean, timeMs: Long)

    /**
     * Call regularly (every ~50 ms). Finishes the current letter or word if
     * the key has been up long enough, and returns any newly decoded text
     * ("" if none). A word gap is returned as a single space.
     */
    fun poll(timeMs: Long): String

    /** Dots and dashes of the letter currently being keyed, like ".-", for display. */
    val currentPattern: String

    /** Current estimate of one unit, in ms. */
    val unitMs: Long
}
```

Required behavior:

- **Unknown patterns** decode to `*`.
- **Adapting to speed:** people key at different speeds, so update `unitMs` as you go. For example, after each dot, move `unitMs` part of the way toward that dot's length; after each dash, move it toward the dash length ÷ 3. Keep `unitMs` between 40 and 400 ms. Someone switching from 10 WPM to 18 WPM partway through should still decode correctly within a few letters.
- **Times are passed in,** never read from the clock inside the class. That keeps it testable with exact numbers.
- **Repeated calls** with the same `on` value (no actual change) are ignored.

### Optional: `MorseEncoder`

```kotlin
object MorseEncoder {
    /** Key-down and key-up events (on, timeMs) that send [text] at [unitMs]. */
    fun timings(text: String, unitMs: Long, startMs: Long = 0): List<Pair<Boolean, Long>>
}
```

This mainly makes the tests easier to write: encode "HELLO WORLD", feed the result to the decoder, and check that you get "HELLO WORLD" back.

## Part 2: Tests (in `core/src/test/kotlin/wifiwalkie/core/`)

`MorseDecoderTest` must cover:
- "SOS" and "HELLO WORLD" at exactly 10 WPM
- the same text at 20 WPM
- every element's length randomly off by ±20% (use a fixed random seed)
- a speed change from 10 to 18 WPM in the middle of a sentence
- an unknown pattern, giving `*`
- `poll` returning each letter once only, not repeatedly

## Part 3: Showing it on desktop

In `desktop/.../SignalWindow.kt`:

- **Add a text line above the status bar** showing the peer's decoded text: the last ~40 characters, plus `currentPattern` in progress, like `HELLO WO .--`.
- **Feed the decoder** from `onPeerSignalChanged`, using `System.nanoTime() / 1_000_000` as the time. Use that rather than `currentTimeMillis`, which can jump when the clock is adjusted.
- **Call `poll` regularly** with a `javax.swing.Timer` firing every 50 ms.
- **Clear the text** when the peer disconnects.
- **Optional:** also decode your own space-bar presses on a second line, so you can see what you're sending.

Task B also edits `SignalWindow.kt`. Keep your changes together, and pull `main` before opening your PR.

## Done when

- [ ] All the tests listed above pass.
- [ ] Tapping "SOS" on one computer shows **SOS** on the other.
- [ ] A teammate unfamiliar with Morse can send their name using a chart, at their own pace, and it decodes correctly.
- [ ] README has a short Morse chart or a link to one.

## Out of scope

Decoding on Android (task C can reuse your `core` class later), and sound tones for Morse.
