# Task A: Networking and Protocol

**Assigned to:** Travis
**Branch:** `a-networking`
**Read first:** [00-overview.md](00-overview.md) (shared contracts), [DESIGN.md](../../DESIGN.md)

## Goal

Grow the message format from a single on/off message into the full protocol: audio frames, a handshake with a passkey, and a clean disconnect. Other tasks depend on your first pull request, so do that one first.

## What exists today

- `SignalMessage`: the only message type (10 bytes: magic, on/off, session ID, sequence).
- `Transport` with `UdpTransport` and `TcpTransport`. Both currently assume every message is 10 bytes.
- `Session`: sends the local on/off state every 100 ms, tracks the peer's state, and declares the peer gone after 1 s of silence.

## Part 1: First pull request (A-1). Do this first, within the first few days.

Others are waiting on this. Keep it to exactly these changes:

1. **Create `core/.../Message.kt`** exactly as specified in the overview, and delete `SignalMessage.kt`. `decode` must return `null`, never throw, for anything invalid: too short, wrong magic byte, unknown type, leftover bytes, or a passkey or audio frame that's too long.
2. **Change `Transport`** to send and receive `Message`, as specified in the overview.
3. **Update `UdpTransport`:** grow the receive buffer to `Message.MAX_SIZE + 1` bytes, so an oversized packet shows up as too long and gets rejected.
4. **Update `TcpTransport`:**
   - Put a 2-byte length (`writeShort`) in front of every message. Write the length and the message in a single `write()` call.
   - When reading, use `readUnsignedShort()` then `readFully()`. If the length is over `MAX_SIZE` or the message doesn't decode, close the connection.
   - Make `send` `@Synchronized`, because audio and heartbeats will now send from different threads.
5. **Extend `Session`:**
   - Add the `sessionId` constructor parameter.
   - Add `sendAudio(pcm)`. Give audio its own sequence counter, and send it on the session's worker thread like signals.
   - Add `Listener.onPeerAudio(sequence, pcm)` with an empty default body.
   - Only forward audio whose `sessionId` matches the currently connected peer.
   - If a `Signal` arrives from a *new* session ID while connected (the peer restarted in under a second), report a disconnect followed by a connect. Task B uses that to reset its audio buffer.
   - `Hello` and `Bye` can be ignored for now.
6. **Tests:** add a `MessageTest` that round-trips every type and checks that junk returns `null`. Extend `SessionTest` so audio frames arrive over both TCP and UDP.
7. **Update the "Message format" section** of DESIGN.md.

The desktop program must work exactly as before once this merges.

## Part 2: Handshake and passkey

**Why:** to stop you from accidentally connecting to another team's copy, and to turn "teammates running different builds" into a clear error instead of a mystery.

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

- **On `start()` and every 1 s afterwards,** send `Hello(PROTOCOL_VERSION, sessionId, passkey)`. Sending it forever is fine: it's tiny, and it covers lost UDP packets and peers that restart.
- **When a `Hello` arrives:**
  - If the version and passkey both match, remember that session ID as accepted.
  - If the version differs, call `onProblem("The other computer runs protocol vX; you run vY. Pull the latest code on both.")`.
  - If the passkey differs, call `onProblem("Passkeys don't match.")`.
  - Report each problem once per peer session, not every second.
- **Pass up `Signal`, `AudioFrame` and `Bye` only from accepted session IDs.** Drop everything else silently.
- **Delegate `send`, `name` and `close`** to the wrapped transport.

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
- **When `Session` receives a `Bye`** from the connected peer, it disconnects immediately: it calls `onPeerConnectedChanged(false)` and turns the peer's signal off. It doesn't wait for the 1 s timeout.
- **Desktop:** add a shutdown hook (`Runtime.getRuntime().addShutdownHook`) that calls `session.close()`. That covers both closing the window and pressing Ctrl+C.
- **A lost `Bye` over UDP is fine,** because the 1 s timeout still catches it.

## Done when

- [ ] A-1 is merged, and the Morse program still works over TCP and UDP.
- [ ] Two copies with matching passkeys connect. With different passkeys, neither one's window ever turns white, and both print "Passkeys don't match." once.
- [ ] Closing one copy turns the other's status to disconnected in well under a second.
- [ ] Unit tests cover: round-trips, junk rejection, passkey match and mismatch, version mismatch, `Bye` causing an immediate disconnect, and audio passing through `ConnectionManager`.
- [ ] DESIGN.md describes the handshake.

## Out of scope

Sending control messages over TCP and audio over UDP at the same time, encryption, a relay server, and automatic discovery of computers on the LAN. These may become later tasks.
