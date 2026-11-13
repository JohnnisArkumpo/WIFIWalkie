# Wi-fi Walkie
CSE 310 Team 6

## Team Members
Travis Newbry

Christopher Rubio

John Arthur

## Software Description
An Android app that transmits audio over WAN.
## Architecture
App will be programmed in Kotlin.

Kotlin 2.4,
JDK 25,
Gradle 9

See [DESIGN.md](DESIGN.md) for the full design.

## Running the Desktop Program
A desktop program that links two computers on the same network. Hold space to talk (push-to-talk), or hold M (or the button) to send a Morse signal, which turns the other window white and decodes into letters.

You only need Java 17 or newer installed. The first run downloads JDK 25 and Kotlin automatically, so it takes a few minutes.

```
./gradlew -q --console=plain :desktop:run        (Linux / macOS)
./gradlew.bat -q --console=plain :desktop:run      (Windows)
```

1. The program prints this computer's network name and IP.
2. Enter the other computer's IP, then choose TCP or UDP. Both computers must choose the same one.
3. Click the window, then hold space to talk or M for Morse. Close the window or press Ctrl+C to stop.

**Use headphones** when both computers are in the same room, or one computer's speaker feeds back into the other's microphone. A computer without a microphone or speaker still runs: it prints a warning, and Morse still works.

Both computers use port 5000. If they can't connect:
* Make sure your firewall allows port 5000 for both TCP and UDP. Windows will ask the first time.
* Campus Wi-Fi may block computers from reaching each other. Try a phone hotspot.

To try it on one computer, run two copies with swapped ports and enter `127.0.0.1` as the IP:

```
./gradlew -q --console=plain :desktop:run --args="--port 5000 --peer-port 5001"
./gradlew -q --console=plain :desktop:run --args="--port 5001 --peer-port 5000"
```

To run the tests: `./gradlew test`

## Running on Android
The Android app needs Android 13 or newer. It connects to the desktop program (or another phone) the same way: same Wi-Fi, port 5000, and both sides choosing the same protocol. For now it sends and shows the Morse signal: hold the big button to send, and the screen turns white and the phone vibrates while the other side sends.

Building it needs the Android SDK (installed with Android Studio). Gradle finds it through `sdk.dir` in `local.properties` or the `ANDROID_HOME` variable. Without it, the build skips the app and still builds the desktop program.

To install on a phone plugged in by USB with USB debugging on:

```
./gradlew :android:installDebug
```

Open **Wi-fi Walkie** on the phone, enter the other device's IP, and tap Connect. The emulator can't receive connections from other computers, so use a real phone.

## Software Features

* [ ] LAN/CAN connection
* [ ] Audio transmission
* [ ] Screen-off mode

## Stretch Goals

* [ ] iOS port
* [ ] Half-duplex mode
* [ ] Screen-off mode, where transmission is driven by the volume keys
* [ ] Peer-2-peer
* [ ] Vibration transmission for morse code
* [ ] Encode communications for security
* [ ] "Repeat that" feature
* [ ] Morse signal decoder
* [ ] Desktop app and UI

## Team Communication
Discord server

## Team Responsibility

|Responsibility                      |Team Member(s)              |
|------------------------------------|----------------------------|
|Conducting Meetings                 | Travis                     |
|Maintaining Team Assignment List    | Travis                     |
|Ensuring GitHub is Working          | John                       |
|Maintaining README and Documentation| Travis                     |
|Create & Display Presentations      | Travis                     |
|Submit Team Assignments             | John                       |
|Task A: Networking                  | Travis                     |
|Task B: Desktop Audio               | John                       |
|Task D: Morse Decoder               | Christopher                |
|iOS port                            | Christopher                |

## Reflections
