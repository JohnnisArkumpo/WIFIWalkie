# Task E: Campus Network Testing

**Assigned to:** ______________
**Branch:** `e-network-testing`
**Read first:** [00-overview.md](00-overview.md), the "Running the Morse Test" section of the [README](../../README.md)

## Goal

Answer one question with real data: **Can two devices on the BYU-Idaho campus network connect to each other directly, and over which protocol?**

Our long-term goal is to talk anywhere on campus. If direct connections work, we're set. If they don't, we'll need a relay server that both devices connect to, which is a significant extra piece of work. Your results decide which way the project goes, so this is the most urgent of the small tasks.

No coding is needed. You use the existing Morse test program on `main`.

## What you need

- Two computers (ideally one Windows and one Linux or macOS, so we also learn about firewall differences), each able to run the program. Follow the README.
- A phone that can make a hotspot. This is the "known good" network to compare against.

## Before you start

- **Prove the program works on the hotspot first.** Put both computers on the phone's hotspot and get TCP and UDP working. If it fails there, the problem is the computers (usually the firewall), not campus, so fix that first.
- **Only test between our own devices.** Don't scan the network or try to reach anyone else's devices, and follow BYU-I's acceptable-use policy. All we're doing is running our app between two of our own computers.

## Test matrix

Run every combination you reasonably can.

**Networks**, using whichever ones you have access to:
- `BYUI`
- `eduroam`
- `BYUI_Visitor`
- `byui-devices`
- phone hotspot (control)
- home Wi-Fi or apartment Wi-Fi (control)

**Placement:**
- both computers in the same room
- different buildings
- one on campus Wi-Fi and one on a phone hotspot (this simulates someone on cellular data)

**Protocol:** TCP and UDP, for every combination.

## What to record for each test

| Field | Example |
|-------|---------|
| Date and time | 2026-10-14 13:05 |
| Network for each computer | BYUI / BYUI |
| Location for each computer | Smith 1st floor / Smith 1st floor |
| IP of each computer | 10.244.46.87 / 10.244.51.12 |
| Ping A→B and B→A | works, 4 ms / blocked |
| TCP connects? | yes / no |
| UDP connects? | yes / no |
| Feels instant? | yes / noticeable delay / unusable |
| 2-minute hold test | stays connected / dropped N times |
| Notes | anything odd |

**How to ping:** run `ping <other IP>` on both Linux and Windows (on Linux, press Ctrl+C to stop). Ping can be blocked even when the app works, so record both: they tell us different things.

**Compare the IP addresses:** if the first two or three numbers differ (like `10.244.46.x` vs `10.244.51.x`), the computers are on different parts of the network. Note whether that affects the results.

## Deliverable

Create `docs/network-tests.md` containing:

1. **The full results table.**
2. **A conclusion** of a few sentences, picking one:
   - Direct connections work across campus: we don't need a relay server.
   - Direct connections work only in some places: describe where.
   - Direct connections don't work: we need a relay server.
   - Only TCP, or only UDP, works: say which.
3. **Firewall fixes:** anything you had to change to make it work. Add these to the README's troubleshooting section too.

Post the conclusion in Discord when it's done, because it affects everyone's plans.

## Done when

- [ ] Hotspot test passes on both protocols.
- [ ] At least one campus network tested in the same room and across buildings, on both protocols.
- [ ] `docs/network-tests.md` merged, with a clear conclusion.
- [ ] README troubleshooting updated with any firewall steps you found.
