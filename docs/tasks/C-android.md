# Task C: Android App

**Assigned to:** ______________
**Branch:** `c-android`
**Read first:** [00-overview.md](00-overview.md) (shared contracts), [DESIGN.md](../../DESIGN.md)

## Goal

An Android app that connects to the desktop program (or another phone) and does the same thing: hold a button to send, and the screen lights up and the phone vibrates while the other person sends. Then add voice, once task B's audio interfaces exist.

The key test for this whole task: **a phone and a laptop running the desktop program work together.**

## Part 1: First pull request (C-1). Do this first, within the first few days.

Make `core` usable from Android. Android only supports Java features up to roughly Java 17, but `core` is currently built for Java 25.

In `core/build.gradle.kts`, keep `jvmToolchain(25)` (everyone keeps one JDK), but make the *output* Java 17:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")   // compile error if core uses an API newer than Java 17
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}
```

This hasn't been tried yet, so verify it:
1. `./gradlew build` passes.
2. The desktop program still runs.
3. Running `javap -v` on a class in `core/build/classes/kotlin/main/...` shows `major version: 61` (which means Java 17).

Only touch `core/build.gradle.kts` in this PR.

## Part 2: Android project setup

- **Add an `android` module to the existing Gradle build.** Don't make a separate project, so the app can use `core` directly with `implementation(project(":core"))`.
- **Gradle settings:**
  - In `settings.gradle.kts`, add `include("android")`.
  - Add a `pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }` block.
  - Add `google()` to `dependencyResolutionManagement`.
- **Android Gradle Plugin:** use the latest stable version that supports our Gradle (9.8). Check Android's compatibility table rather than guessing.
- **Package:** `wifiwalkie.android`
- **Android versions:** `minSdk = 26` (Android 8.0), with `compileSdk` and `targetSdk` set to the latest stable version.
- **UI:** Jetpack Compose.
- **Opening it:** open the **repo root folder** in Android Studio, not the `android` folder.
- **Keep the desktop build working:** `./gradlew :desktop:run` and `./gradlew test` must still work, and a teammate without Android Studio must still be able to build the desktop app. If the Android SDK being required for every build becomes a problem, raise it with the team.

## Part 3: The app (on/off signal)

### Screens

1. **Connect screen:**
   - Shows the phone's network name and IP.
   - A text field for the other device's IP.
   - A TCP / UDP choice.
   - A **Connect** button.
2. **Signal screen:**
   - A large **Hold to send** area.
   - The whole background turns white while the peer is sending.
   - A status line: connected or waiting.
   - A **Disconnect** button that returns to the Connect screen.

### Classes (in `android/src/main/kotlin/wifiwalkie/android/`)

| Class | Job |
|-------|-----|
| `MainActivity` | Hosts the Compose UI. |
| `WalkieViewModel` | Owns the `Session` so it survives screen rotation. It implements `Session.Listener` and exposes `StateFlow`s for `peerConnected` and `peerOn`. It has `connect(ip, useTcp)`, `setLocalSignal(on)` and `disconnect()`, and closes the session in `onCleared()`. |
| `ConnectScreen`, `SignalScreen` | Composable functions. |
| `AndroidNetworkInfo` | `localIp()` comes from `ConnectivityManager.getLinkProperties(activeNetwork)`. For `networkName()`, the Wi-Fi name needs location permission on modern Android, so returning "unknown" is acceptable. |
| `VibrationOutput` | `setOn(true)` starts a long vibration and `setOn(false)` cancels it. Use `VibratorManager` on API 31+ and `Vibrator` below that. |

### Implementation notes

- **Detecting press and release:** use `Modifier.pointerInput` with `detectTapGestures(onPress = { setLocalSignal(true); tryAwaitRelease(); setLocalSignal(false) })`. That catches the finger going down and coming up.
- **Threading:** `Session` and the transports already run networking on background threads, which Android requires. `Listener` callbacks arrive on a background thread, and updating a `StateFlow` from there is fine.
- **Ports:** use `5000` for both the local and peer port, the same as desktop.
- **Permissions** in `AndroidManifest.xml`: `INTERNET` and `VIBRATE`.

### Testing

- **Use a real phone, not the emulator.** The emulator sits behind its own private network and can't receive connections from your LAN.
- **Phone and laptop must be on the same Wi-Fi.** A laptop's or phone's hotspot works if campus Wi-Fi blocks devices from reaching each other (see task E).
- **Install** from Android Studio with USB debugging, or by running `./gradlew :android:installDebug`.

## Part 4: Android audio (after B-1 is merged)

Implement task B's interfaces for Android:

- **`AndroidAudioInput : AudioInput`:**
  - Use `AudioRecord` with source `MediaRecorder.AudioSource.VOICE_COMMUNICATION`, 16000 Hz, `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`.
  - Read 640-byte frames on a background thread.
  - Android only *guarantees* 44.1 kHz, though 16 kHz works on nearly all phones. If the `AudioRecord` fails to initialize, record at 48000 Hz and keep every third sample.
- **`AndroidAudioOutput : AudioOutput`:** use `AudioTrack` with `USAGE_VOICE_COMMUNICATION`, 16000 Hz mono 16-bit, in streaming mode.
- **Connecting it:** reuse task B's `JitterBuffer` and `AudioPlayback` from `core`, connected the same way B does on desktop: hold to talk sends audio, `onPeerAudio` pushes into the buffer, and a connection change resets it.
- **Permission:** request `RECORD_AUDIO` at runtime the first time the user holds the button. If they refuse, keep working with the signal only.

## Done when

- [ ] C-1 is merged, and the desktop build is unaffected.
- [ ] The app installs on a real phone.
- [ ] Phone ↔ laptop: holding on either one lights up (and vibrates) the other, over both TCP and UDP.
- [ ] Phone ↔ phone works, if two Android phones are available.
- [ ] Rotating the phone doesn't drop the connection.
- [ ] Part 4: phone ↔ laptop voice works in both directions.
- [ ] README has a "Running on Android" section.

## Out of scope

Screen-off mode and the volume-key trigger (stretch goal), running in the background when the app isn't visible, and the iOS port.
