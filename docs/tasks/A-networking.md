# Task A: Networking and Protocol

**Assigned to:** Travis
**Branch:** `a-networking`
**Read first:** [00-overview.md](00-overview.md) (shared contracts), [F-rtp-lite.md](F-rtp-lite.md) (audio packet fields), [DESIGN.md](../../DESIGN.md)

## Goal

Grow the message format from a single on/off message into the full protocol: audio frames, a handshake with a passkey, a heartbeat that doesn't depend on the Morse signal, and a clean disconnect. Other tasks depend on your first pull request, so do that one first.

This task covers **one-on-one** connections. **Task G follows it** and adds group calls (a playback buffer per sender, and mixing). A few choices here keep G easy; they're marked **(for G)**.

## What exists today

- `SignalMessage`: the only message type (10 bytes: magic, on/off, session ID, sequence).
- `Transport` with `UdpTransport` and `TcpTransport`. Both currently assume every message is 10 bytes.
- `Session`: sends the local on/off state every 100 ms, tracks one peer's state, and declares the peer gone after 1 s of silence. **The repeated signal is currently the only heartbeat**, so connection status depends on a feature that's only meant for testing.

## Part 1: First pull request (A-1). Do this first, within the first few days.

Others are waiting on this. Keep it to exactly these changes:

1. **Create `core/.../Message.kt`** exactly as specified in the overview, and delete `SignalMessage.kt`.
   - `AudioFrame` includes the `startOfTransmission` flag from Task F, stored as bit 0 of a flags byte. The other 7 bits are reserved: write them as 0, and ignore them when reading.
   - `decode` must return `null`, never throw, for anything invalid: too short, wrong magic byte, unknown type, leftover bytes, or a passkey or audio frame that's too long.
2. **Change `Transport`** to send and receive `Message`, as specified in the overview.
3. **Update `UdpTransport`:** grow the receive buffer to `Message.MAX_SIZE + 1` bytes, so an oversized packet shows up as too long and gets rejected.
4. **Update `TcpTransport`:**
   - Put a 2-byte length (`writeShort`) in front of every message. Write the length and the message in a single `write()` call.
   - When reading, use `readUnsignedShort()` then `readFully()`. If the length is over `MAX_SIZE` or the message doesn't decode, close the connection.
   - Make `send` `@Synchronized`, because audio and heartbeats will now send from different threads.
5. **Extend `Session`** to the signatures in the overview:
   - Add the `sessionId` constructor parameter.
   - **(for G) Every `Listener` callback gets a `peerId` parameter**, which is the peer's `sessionId`. With one peer it's always the same value, and B, C and F can ignore it. Adding it later would mean changing every task's code at once.
   - Add `sendAudio(pcm, startOfTransmission)`. Give audio its own sequence counter, and send it on the session's worker thread like signals.
   - Add `Listener.onPeerAudio(peerId, sequence, startOfTransmission, pcm)` with an empty default body.
   - **Any valid message from the peer proves it's alive**, not only a `Signal`. Update the last-heard time on every message.
   - Only forward audio whose `sessionId` matches the currently connected peer.
   - If any message arrives from a *new* session ID while connected (the peer restarted in under a second), report a disconnect for the old ID followed by a connect for the new one. F's buffer uses that to reset.
   - **(for G) Keep the peer's fields together** in one small private class, such as `PeerState` (connected, signal on, last sequence, last-heard time), held in a single field. G turns that field into a map keyed by `peerId`.
   - Keep sending the signal every 100 ms for now. Part 2 replaces it as the heartbeat.
   - `Hello` and `Bye` can be ignored for now.
6. **Update `desktop/.../SignalWindow.kt`** to the new `Listener` signatures, ignoring `peerId`. That's the only desktop change in A-1.
7. **Tests:**
   - Add a `MessageTest` that round-trips every type (including both values of `startOfTransmission`) and checks that junk returns `null`.
   - Extend `SessionTest` so audio frames arrive over both TCP and UDP, with the right `peerId` and flag.
8. **Update the "Message format" section** of DESIGN.md.

The desktop program must work exactly as before once this merges.

## Part 2: Handshake, passkey and heartbeat

**Why:**
- To stop you from accidentally connecting to another team's copy.
- To turn "teammates running different builds" into a clear error instead of a mystery.
- To give the connection a heartbeat of its own, so that an app that never uses the Morse signal still knows when its peer is there.

Add `core/.../ConnectionManager.kt`. It wraps a `Transport` and is itself a `Transport`, so `Session` doesn't change:

```kotlin
class ConnectionManager(
    private val transport: Transport,
    private val sessionId: Int,          // the same ID passed to Session
    private val passkey: String,         // "" means no passkey
    private val onProblem: (String) -> Unit,
) : Transport
```

Required behavior:

- **Hello is the heartbeat.** On `start()` and every **250 ms** afterwards, send `Hello(PROTOCOL_VERSION, sessionId, passkey)`. It's tiny, and repeating it covers lost UDP packets, restarted peers, and keeping `Session`'s 1 s timeout satisfied.
- **When a `Hello` arrives:**
  - If the version and passkey both match, accept that session ID and pass the `Hello` up to `Session`, which counts it as proof the peer is alive.
  - If the version differs, call `onProblem("The other computer runs protocol vX; you run vY. Pull the latest code on both.")`.
  - If the passkey differs, call `onProblem("Passkeys don't match.")`.
  - Report each problem once per peer session, not every 250 ms.
- **Pass up `Signal`, `AudioFrame` and `Bye` only from accepted session IDs.** Drop everything else silently.
- **(for G) Keep accepted session IDs in a set,** not a single field, so several peers work without changes.
- **Delegate `send`, `name` and `close`** to the wrapped transport.

Change `Session`'s signal sending:
- Send the signal **when it changes**.
- Repeat it every 100 ms **only while it's on, and for 1 s after it turns off**. That still corrects lost signal packets, but an idle connection sends only Hellos, and a connection that never uses the signal sends no signal traffic at all.

Desktop changes in `Main.kt`:

- Add a prompt after the protocol prompt: `Passkey (leave blank for none):`.
- Wire it up like this:
  ```kotlin
  val sessionId = Random.nextInt()
  val connection = ConnectionManager(transport, sessionId, passkey) { println(it) }
  val session = Session(connection, window, sessionId)
  ```

## Part 3: Clean disconnect (`Bye`)

- **`Session.close()`** sends `Bye(sessionId)` before shutting down.
- **When `Session` receives a `Bye`** from the connected peer, it disconnects that peer immediately: it calls `onPeerConnectedChanged(peerId, false)` and turns that peer's signal off. It doesn't wait for the 1 s timeout.
- **Desktop:** add a shutdown hook (`Runtime.getRuntime().addShutdownHook`) that calls `session.close()`. That covers both closing the window and pressing Ctrl+C.
- **A lost `Bye` over UDP is fine,** because the 1 s timeout still catches it.

## Looking ahead: Task G (group calls)

G comes after this task and Task F. It will:
- turn `Session`'s single `PeerState` into a map, so several peers can be connected at once
- give each sender their own `PlayoutBuffer`, and mix them through an `AudioMixer` that replaces `AudioPlayback`
- add a display name to `Hello` (with a `PROTOCOL_VERSION` bump), so the UI can show who's talking
- decide how packets reach several peers: send to each one, LAN multicast, or a relay server

Nothing in this task needs to support more than one peer. Just keep to the **(for G)** points above.

## Done when

- [ ] A-1 is merged, and the Morse program still works over TCP and UDP.
- [ ] Two copies with matching passkeys connect. With different passkeys, neither one's window ever turns white, and both print "Passkeys don't match." once.
- [ ] With the signal never used, the connection still shows as connected (the heartbeat works without it).
- [ ] Closing one copy turns the other's status to disconnected in well under a second.
- [ ] Unit tests cover:
  - round-trips and junk rejection
  - passkey match and mismatch, and version mismatch
  - the connection staying up with no signal traffic
  - `Bye` causing an immediate disconnect
  - audio passing through `ConnectionManager` with the right `peerId`
- [ ] DESIGN.md describes the handshake and heartbeat.

## Out of scope

- More than one peer (Task G)
- Sending control messages over TCP and audio over UDP at the same time
- Encryption
- A relay server
- Automatic discovery of computers on the LAN

These may become later tasks.
