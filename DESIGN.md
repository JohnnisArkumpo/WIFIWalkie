# Wi-fi Walkie Design

Wi-fi Walkie is push-to-talk communication between two devices, written in Kotlin. The end goal is an Android app that sends voice. The first milestone is smaller: two computers on the same local network (LAN), where holding the space bar on one turns the other's screen white. That on/off signal is enough to send Morse code, and it lets us build and test the networking before taking on audio.

## Milestones

| # | Milestone | Done when |
|---|-----------|-----------|
| 1 | **On/off prototype** (desktop, LAN) | Holding space on computer A turns B's window white, and releasing it turns it black. Works in both directions at the same time. |
| 2 | **Make it solid** | Passkey check, a dropped connection is detected and shown, and the window shows connection status. Optional: decode Morse into letters on screen. |
| 3 | **Audio on desktop** | Push-to-talk voice between two computers, using the same connection code. |
| 4 | **Android app** | Same core code on phones. A push-to-talk button, with vibration or a screen flash as the Morse output. |
| 5 | **Beyond the LAN** | Test across campus. If direct connections are blocked, add a relay server. |

Stretch goals, in no fixed order: half-duplex mode, screen-off mode using the volume keys, encryption, "repeat that", LAN auto-discovery, and an iOS port.

## Decisions for milestone 1

**Kotlin on the JVM.** The connection and message code is plain Kotlin, so it can move to Android later without changes.

**Use a small window, not only the terminal.** A terminal can't tell when a key is *released*. When you hold space in a terminal it sends one press, pauses about half a second, then sends repeated presses, and it never sends a "released" event. That makes "hold space" impossible to measure reliably in a terminal. A small window can do it: Swing is built into Java and reports real key-pressed and key-released events, and the window can simply fill white or black. We still start the program from the terminal and print logs there.

**Pick TCP or UDP at startup.** The test program supports both so we can compare them on real networks. TCP resends lost data and keeps it in order, but a lost packet holds up everything behind it. UDP sends each packet once, so packets can go missing or arrive out of order. For TCP we turn off Nagle's algorithm (`TCP_NODELAY`) so each message goes out immediately instead of waiting to be batched. The long-term plan is TCP for control messages and UDP for audio.

**No host or joiner.** Each person enters the other computer's IP, and both sides start the same way. Over UDP, each side just sends packets to the other. Over TCP, each side listens *and* connects, giving one connection in each direction. If a connection drops, it reconnects automatically.

**Where to test.** Campus Wi-Fi often blocks devices from connecting to each other, even on the same network (this is called client isolation). For milestone 1, use a home network, a phone hotspot, or Ethernet. Both computers' firewalls must allow port 5000. To develop on one computer, run two copies with swapped ports and enter `127.0.0.1` (see the README).

## Message format (milestone 1, built)

There's only one kind of message, and it carries the sender's *full current state* ("my key is down") rather than an event ("I pressed"). Each computer sends its state when it changes and again every 100 ms. That gives three guarantees, over UDP as well as TCP:

- **A lost message is corrected within 100 ms** by the next one, so the light can't get stuck on.
- **The repeats act as a heartbeat.** If nothing arrives for 1 s, the peer counts as disconnected and its light turns off.
- **Old packets are ignored.** A sequence number lets the receiver drop late or out-of-order packets. A random session ID tells a restarted program apart from late packets.

| Byte(s) | Field |
|---------|-------|
| 0 | Magic byte `W`, so stray packets are ignored |
| 1 | 1 = key down, 0 = key up |
| 2–5 | Session ID (random per run) |
| 6–9 | Sequence number |

## Message protocol (milestone 2 and later, planned)

Later milestones add more message types. Each message will start with a 1-byte type, followed by any data that type carries.

| Type | Data | When it's sent |
|------|------|----------------|
| `HELLO` | protocol version, passkey | First message from each side. The connection closes if the passkey doesn't match. |
| `SIGNAL` | on/off, session ID, sequence | The milestone 1 message above |
| `BYE` | none | Clean disconnect, so the other side doesn't wait for the timeout |
| `AUDIO_FRAME` | sequence, length, bytes | Milestone 3 |

## Classes

```
  Computer A                                          Computer B

  SpaceBarInput                                       SignalWindow
       │ press / release                                   ▲ on / off
       ▼                                                   │
    Session ──────▶ ConnectionManager ═ TCP/UDP ═ ConnectionManager ──────▶ Session
```

Everything is symmetric: B's input reaches A's window along the same path in reverse.

The milestone 1 test program builds a simpler version of this: `SignalMessage`, `Session`, `SpaceBarInput` and `SignalWindow` as described here, with `TcpTransport` and `UdpTransport` filling the role of `ConnectionManager` (both implement a `Transport` interface). The list below is the full design for later milestones.

### Core (shared by desktop and Android, no UI code)

- **`Message`**: a sealed class with one subclass per message type, plus `writeTo(stream)` and `readFrom(stream)`.
- **`ConnectionManager`**: wraps a TCP or UDP `Transport`. It adds the `HELLO` and passkey check and the clean `BYE`, and it reports `onConnected`, `onMessage` and `onDisconnected(reason)` to a listener.
- **`Session`**: connects input, the connection and output. It tracks state (idle, connecting, connected) and whether each side is currently transmitting. It sends local presses out, and it routes each incoming message to the right output. Half-duplex rules will go here later.
- **`SignalInput`** (interface): reports press and release events. Desktop uses `SpaceBarInput`. Android will use a button or the volume keys.
- **`SignalOutput`** (interface): `setOn(Boolean)`. Desktop uses `SignalWindow`. Android will use vibration or a screen flash.
- **`MorseDecoder`** (milestone 2, optional): turns on/off timings into dots and dashes, then into letters.

### Audio (milestone 3)

- **`AudioCapture`**: starts and stops the microphone, and produces audio frames for `Session` to send.
- **`AudioPlayer`**: plays received frames. It keeps a small buffer so uneven network timing doesn't cause choppy playback.
- **`Beeper`**: the transmission beeps, played when you start or stop talking and when the other person does.

## Changes from the original plan

| Original | Change | Why |
|----------|--------|-----|
| Connection manager | Kept, with a clearer job | Also handles the message format, heartbeat and disconnect detection. |
| Audio stream (start, stop, pop) | Split into `AudioCapture` and the buffer inside `AudioPlayer` | Start and stop belong to the microphone. "Pop" means taking the next frame from the playback buffer. |
| Audio sender, Audio receiver | Removed and replaced by `Session` | Both only passed data to or from the connection manager. `Session` routes all message types, so on/off signals and audio share one path. |
| Transmission beep (listed twice) | One `Beeper` class | It was the same feature in two places. |
| *(missing)* | `Message` | Both sides need an agreed format for what's sent over the connection. |
| *(missing)* | `SignalInput`, `SignalOutput` | Desktop and Android use different keys and outputs, so the same core code needs a common interface for both. |
| *(missing)* | Heartbeat and connection status | Without them, a dropped connection can leave the other side's light stuck on. |

## Project layout

A Gradle project with one module per platform. The `core` module never imports Swing or Android code.

```
core/       Message, ConnectionManager, Session, SignalInput, SignalOutput, MorseDecoder
desktop/    Main.kt (reads the command-line arguments), SpaceBarInput, SignalWindow
android/    added in milestone 4
```

To run milestone 1, use `./gradlew -q --console=plain :desktop:run` (more detail in the README).

## Known limitations

- The passkey is sent as plain text. That's fine for a LAN prototype, but it isn't real security (encryption is a stretch goal).
- Network delay can stretch or shrink dots and dashes slightly. On a LAN the difference is a few milliseconds, which is too small to matter.
- Half-duplex mode needs a rule for what happens when both people press at the same moment. We'll decide that when we build it.
