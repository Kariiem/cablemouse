# Plan: Cable Mouse: phone + cable as a mouse, with a portable PC helper

## Context
The phone should act as a mouse for the PC it's plugged into over its normal USB cable. Old PCs often have no Bluetooth. Root and extra hardware are both ruled out.

Without root, Android can't present itself as a USB HID mouse. So the design is split in two:
1. The phone first shows up as **File Transfer (MTP)**. The user copies a small **portable helper executable** from the phone's storage to the PC. Nothing is installed: no admin rights, no drivers.
2. The user switches the phone's USB mode to **MIDI**. The helper reads the phone's messages through the OS's built-in MIDI support and moves the cursor with the OS's own input APIs.

**What MIDI is:** the standard for connecting music instruments to computers. Devices exchange tiny 3-byte messages such as "key pressed", "key released" or "knob turned to value N". USB-MIDI is a standard USB class with built-in drivers on every OS. Most Android phones list "MIDI" in the USB options next to "File transfer". We use those messages to carry mouse data instead of music. For example, "knob 1 = 70" means "move right 6 px".

**Why MIDI as the transport:**
- Every OS has a built-in class driver for USB-MIDI: Windows XP+ through winmm, macOS through CoreMIDI, Linux through ALSA `snd-usb-audio`. No admin rights and no driver install.
- An app can use it without root. In MIDI mode, Android exposes a "USB Peripheral" MIDI port through `MidiManager`.
- Latency is low (about 1 ms frames) and it doesn't touch the PC's network.
- The alternatives fall short:
  - USB tethering (RNDIS/NCM): macOS has no RNDIS support, it can redirect the PC's internet through the phone, and some carriers block it.
  - Android Open Accessory (AOA): needs a WinUSB driver on Windows.
  - ADB: needs developer mode, plus a driver on Windows.

## Status (2026-11-13)
Legend: **verified** = confirmed by the user on real hardware (Android 15 phone, Windows 10/11 PC); **built** = implemented, builds, unit-tested where possible, not confirmed on hardware; **todo** = not done.

| Phase | State | Notes |
| --- | --- | --- |
| 0. Transport spike (steps 1-3) | **verified** | Phone exposes `Android USB Peripheral Port` (TYPE_USB, 1 input port); notes and mouse messages reach the Windows helper. Step 4 (500 msg/s stream test) exists in the app's Diagnostics but its result was never reported. |
| 1. App skeleton | **verified** | Plain Views, no Compose. MIDI link lives in `ui/MainActivity.kt` (a separate `MidiLink.kt` was written first and later deleted as dead code). |
| 2. Protocol + scheduler | **verified** | `MouseProtocol.kt`, `FrameScheduler.kt`, JVM tests; shared vectors with the C decoder. |
| 3. Touchpad UI | **verified**, partly | Move, tap = left click, two-finger tap = right click, two-finger scroll, Left/Right buttons with click sound, pointer/scroll speed, invert scroll, dark/light/system theme. **todo:** tap-and-hold drag, haptics, keep-screen-on. |
| 4. Helper delivery over MTP | **built** | Exporter + setup guide; the file appears in the phone's `Download/CableMouse`. Copy to a PC over MTP not verified (blocked on the user's work PC; they use the GitHub release). Only the Windows exe is bundled. |
| 5. PC helper | Windows **verified**; Linux **built**; macOS/APE **todo** | Windows back end hardened for unplug/replug. Linux X11 back end untested. macOS is a stub; the Cosmopolitan APE (`cablemouse.com`) and its spike (step 18) were not started. |
| 6. Robustness | mostly **verified** | App auto-reconnects on device add/remove; heartbeat every 500 ms; helper releases buttons after 2 s without heartbeat; version mismatch warning exists. |
| 7. Extras | **todo** | Keyboard mode, gyro air-mouse, absolute positioning. |
| Release | **done** | Public repo https://github.com/Kariiem/cablemouse, MIT, release `v1.0` with signed APK, Windows exe and checksums. |

## Architecture
```
Android app (Kotlin)                          PC helper (C): cablemouse.com (APE, all modern OSes)
                                               + cablemouse-legacy.exe (32-bit Windows XP/7)
 touchpad gestures                             MIDI input from "Android USB Peripheral"
   → mouse frames                                → decode messages
   → MidiInputPort (USB peripheral) ──USB-MIDI──→ → OS cursor/click/scroll injection
 bundled helpers exported to Download/CableMouse  (SendInput / CGEventPost / XTest)
```

### Wire protocol (MIDI channel 1, 3-byte messages, 7-bit safe)
- `B0 01 v`: dx, where v = dx + 64 (range −64..63). Larger moves are split into several messages.
- `B0 02 v`: dy, same encoding.
- `B0 03 v`: scroll wheel, same encoding.
- `90 n 7F` / `80 n 00`: button down / up. n = 0 is left, 1 is right, 2 is middle.
- `B0 7F v`: heartbeat and protocol version, so the helper can show "connected".

## Small-step plan

### Phase 0: Feasibility spikes (prove the transport before building anything)
1. On the phone, open Settings → Connected devices → USB and select **MIDI**. On a Linux PC run `amidi -l`, and on Windows look in Device Manager under "Sound, video and game controllers". Confirm a MIDI device appears.
2. Write a throwaway Android activity that opens the USB peripheral MIDI device through `MidiManager` and sends `90 3C 7F` when a button is tapped.
3. On the PC, run `amidi -p hw:X -d` (Linux) or a free MIDI monitor (Windows) and confirm the bytes arrive. **Go/no-go point.**
4. Measure the round trip: send 500 messages per second and check that none are dropped and none are delayed.

### Phase 1: Android app skeleton
5. Create the Android project (`com.cablemouse`): Kotlin, plain Views (Compose was dropped to keep dependencies minimal), minSdk 23 (needed for `android.media.midi`), with `<uses-feature android:name="android.software.midi">`.
6. MIDI link (implemented inside `ui/MainActivity.kt`): watch for MIDI devices with `MidiManager.registerDeviceCallback`, pick the USB peripheral device, open its input port, and expose `send(bytes)` and a connected state.
7. Status screen that says "Not in MIDI mode, switch USB to MIDI" (with how-to steps) or "Connected".

### Phase 2: Protocol and mouse logic (shared, unit-tested)
8. `MouseProtocol.kt`: encode move/button/scroll messages and split deltas into ±63 chunks. JVM unit tests.
9. `FrameScheduler.kt`: collect touch deltas and flush at about 125 Hz, keeping fractional remainders (float accumulators) so slow moves aren't lost.

### Phase 3: Touchpad UI
10. Full-screen touchpad. One-finger drag moves the cursor.
11. Tap is a left click, two-finger tap is a right click, two-finger vertical drag scrolls, and tap-and-hold drags (button held down).
12. On-screen L/M/R buttons along the bottom.
13. Settings (DataStore): sensitivity, acceleration, scroll speed, invert scroll. Add keep-screen-on and haptic feedback.

### Phase 4: Delivering the helper over MTP
14. Bundle the prebuilt helpers in `app/src/main/assets/helpers/` (currently only `cablemouse-legacy.exe` + README; `cablemouse.com` is not built yet):
    - `cablemouse.com`: the universal APE for any 64-bit Windows 8+, Linux or macOS machine.
    - `cablemouse-legacy.exe`: for 32-bit Windows and Windows XP/Vista/7.
    - `README.txt`, which explains which file to run.
15. On first launch, and whenever the app version changes, copy them through `MediaStore.Downloads` into `Download/CableMouse/` (API 23-28: direct write with the storage permission) so they show up over MTP.
16. In-app onboarding:
    1. Leave USB on File Transfer.
    2. On the PC, open Phone → Download → CableMouse and copy `cablemouse.com`. On an old 32-bit or XP/7 PC, copy `cablemouse-legacy.exe` instead.
    3. Switch USB to MIDI.
    4. Run the helper. On Linux/macOS: `sh ./cablemouse.com`, or `chmod +x` it first.

### Phase 5: PC helper (C, no installer, no admin)
One C codebase builds two outputs:
- **`cablemouse.com`**: built with Cosmopolitan `cosmocc` as an Actually Portable Executable. At runtime it picks a back end with `IsWindows()` / `IsXnu()` / `IsLinux()`. Windows DLLs, macOS frameworks and `libXtst` are loaded through `cosmo_dlopen`/`cosmo_dlsym`.
- **`cablemouse-legacy.exe`**: the Windows back end alone, built with MinGW i686 (`-D LEGACY_WIN32`) and linked directly against `winmm`/`user32`. It runs on 32-bit Windows and on XP/Vista/7.

17. Repo folder `helper/`. Put the protocol decoder in `common/protocol.c` and add host unit tests for it. `main.c` picks a back end through a small interface: `backend_open()`, `backend_read_midi()`, `backend_move()`, `backend_button()`, `backend_scroll()`.
18. **Spike, before the back ends:** build a "hello" APE with `cosmocc` that calls `cosmo_dlopen("user32.dll")` → `SetCursorPos` on Windows, and does the macOS and Linux equivalents. Confirm it runs on all three. If dlopen doesn't work on some OS, fall back to shipping a native binary for that OS only.
19. **Windows back end** (`backends/win.c`, shared by the APE and the legacy exe):
    - Use `midiInGetNumDevs`/`midiInGetDevCaps` to find the device whose name contains "Android" (or let the user pick).
    - Open it with `midiInOpen` and a callback, and turn messages into `SendInput` mouse events (`MOUSEEVENTF_MOVE`, `LEFTDOWN`, `WHEEL`, …).
    - Show a tray or console status, and reconnect automatically when the device is unplugged and plugged back in.
20. **Linux back end** (`backends/linux.c`):
    - Read raw MIDI straight from `/dev/snd/midiC*D0`, which needs no libasound. Most desktop users already have access to it.
    - Inject input through XTest (`dlopen("libXtst.so.6")`) on X11. Fall back to `/dev/uinput` if it's writable.
    - On Wayland, print a clear message instead (see Risks).
21. **macOS back end** (`backends/mac.c`): load CoreMIDI and CoreGraphics through `cosmo_dlopen`. Read input with `MIDIInputPortCreate` and inject with `CGEventCreateMouseEvent`/`CGEventPost`. Ask for Accessibility permission on first run.
22. A `helper/Makefile` with two targets: `cosmocc` → `cablemouse.com`, and `i686-w64-mingw32-gcc` → `cablemouse-legacy.exe`. A third target copies both into the Android assets (Phase 4, step 14).

### Phase 6: Robustness and polish
23. Android: reconnect when the MIDI device disappears or comes back (cable replug, USB mode change). Send heartbeats every 500 ms.
24. Helper: if no heartbeat arrives for 2 s, show "disconnected" and release any held buttons so the mouse doesn't stay stuck in a drag.
25. Add version negotiation through the heartbeat value. The helper warns when versions don't match.

### Phase 7 (optional extras)
26. Keyboard/text mode, with keys sent as note numbers or SysEx and injected through SendInput, CGEvent or XTest.
27. Gyroscope "air mouse" mode.
28. Absolute positioning mode (phone screen maps to PC screen).

## Critical files
- `app/src/main/java/com/cablemouse/proto/MouseProtocol.kt` and `FrameScheduler.kt`
- `app/src/main/java/com/cablemouse/ui/MainActivity.kt` (touchpad, settings, MIDI port), `Onboarding.kt`, `Palette.kt`, `ClickSound.kt`
- `app/src/main/java/com/cablemouse/export/HelperExporter.kt`: MediaStore copy to Download/CableMouse
- `helper/main.c`, `helper/common/protocol.c`, `helper/backends/{win,linux,mac}.c`, `helper/Makefile` (cosmocc and MinGW targets)

## Verification
- Phase 0 step 3 proves the transport: bytes from the app reach a PC MIDI monitor.
- Unit tests: Kotlin tests for encoding, chunking and the scheduler, and C tests for the decoder, using the same test vectors in both.
- End-to-end matrix:
  - Windows XP/7 with `cablemouse-legacy.exe`, and Windows 10/11 with `cablemouse.com`, both as a standard (non-admin) user.
  - A Linux X11 desktop and macOS (if available), both with the same `cablemouse.com` file.
  - Each one tested for move, click, drag, scroll and unplug/replug.
- First-run test on a fresh PC: copy the helper over MTP, switch to MIDI, run it, and confirm the cursor moves. Nothing else is installed.

## Known limitations and risks
- **Mode switch is manual.** Apps can't change the USB mode without privileges, so the user switches between File Transfer and MIDI from the USB notification or Settings. The app guides them through it.
- **Phone support.** Some phones hide the MIDI option, or only show it under Developer options → "Default USB configuration". Phase 0 checks this.
- **macOS can't browse MTP natively**, so mac users would get the helper some other way, such as a download link shown in the app. Gatekeeper also needs right-click → Open, plus Accessibility permission.
- **Windows SmartScreen** may warn about an unsigned exe ("More info → Run anyway"). Code signing can come later.
- **Linux Wayland** blocks injecting input through XTest. It needs uinput access, so that case is documented.
- **Some antivirus tools** flag programs that inject input, and polyglot APE files get flagged more often. Keep the helper small and its source open.
- **Legacy exe and the C runtime.** The `cablemouse-legacy.exe` built on the current dev machine links the Universal C Runtime (`api-ms-win-crt-*`), so it needs Windows 10/11, or Windows 7/8.1 with the UCRT update (KB2999226). Windows XP/Vista need a build from an msvcrt-based MinGW (for example MSYS2 `mingw32`); this has not been done or tested.
- **APE limits.** It needs a 64-bit OS and Windows 8+. That's why the legacy exe exists. On macOS, APE starts through the shell, and Apple Silicon needs Cosmopolitan's small loader. The spike (Phase 5, step 18) checks this early.
