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

## Running the Morse Test
A desktop program that links two computers on the same network. Holding the space bar on one turns the other's window white.

You only need Java 17 or newer installed. The first run downloads JDK 25 and Kotlin automatically, so it takes a few minutes.

```
./gradlew -q --console=plain :desktop:run        (Linux / macOS)
gradlew.bat -q --console=plain :desktop:run      (Windows)
```

1. The program prints this computer's network name and IP.
2. Enter the other computer's IP, then choose TCP or UDP. Both computers must choose the same one.
3. Click the window and hold space. Close the window or press Ctrl+C to stop.

Both computers use port 5000. If they can't connect:
* Make sure your firewall allows port 5000 for both TCP and UDP. Windows will ask the first time.
* Campus Wi-Fi may block computers from reaching each other. Try a phone hotspot.

To try it on one computer, run two copies with swapped ports and enter `127.0.0.1` as the IP:

```
./gradlew -q --console=plain :desktop:run --args="--port 5000 --peer-port 5001"
./gradlew -q --console=plain :desktop:run --args="--port 5001 --peer-port 5000"
```

To run the tests: `./gradlew test`

## Software Features

* [ ] LAN/CAN connection
* [ ] Audio transmission
* [ ] Screen-off mode

## Team Communication
Discord server

## Team Responsibility

|Responsibility                      |Team Member(s)              |
|------------------------------------|----------------------------|
|Conducting Meetings                 |                            |
|Maintaining Team Assignment List    |                            |
|Ensuring GitHub is Working          | John |
|Maintaining Documentation           |                            |
|Create & Display Presentations      | Travis |
|Submit Team Assignments             | John |

## Reflections
