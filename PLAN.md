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

## Status (2026-11-15)
The released version is **v1.1**. Everything marked verified below was verified on v1.1. The commits after v1.1 (see "Unreleased changes" under the table) are built and tested by the automated tests only.

Legend: **verified** = confirmed by the user on real hardware (Android 15 phone, Windows 10/11 PC); **built** = implemented, builds, unit-tested where possible, not confirmed on hardware; **todo** = not done.

| Phase | State | Notes |
| --- | --- | --- |
| 0. Transport spike (steps 1-4) | **verified** | Phone exposes `Android USB Peripheral Port` (TYPE_USB, 1 input port); notes and mouse messages reach the Windows helper. Step 4 (500 msg/s stream test) **passed** on the user's Windows 10/11 PC (2026-11-15): 5000 messages in 10.00 s (500/s), none missing or repeated, longest gap 7.5 ms, no gap over 20 ms, measured by `cablemouse-legacy.exe --stream-check` (the gap includes Windows scheduling on the PC side). The app aborts a stream if its MIDI port closes, so two shorter runs before it ended early (they also lost nothing). |
| 1. App skeleton | **verified** | Plain Views, no Compose. The MIDI link is `link/MidiLink.kt`, behind the `link.Transport` interface; `MainActivity` only wires the parts. |
| 2. Protocol + scheduler | **verified** | `MouseProtocol.kt`, `FrameScheduler.kt`, JVM tests; shared vectors with the C decoder (`testvectors/protocol.txt`). |
| 3. Touchpad UI | **verified**, partly | Move, tap = left click, two-finger tap = right click, two-finger scroll, tap-then-touch drag (verified by the user), Left/Right buttons with selectable feedback (silent / audible click / vibration), pointer/scroll speed, invert scroll, dark/light/system theme. **Not built** from the original plan: pointer acceleration, an on-screen middle button (the protocol has it), keep-screen-on outside Gyro mode. |
| 4. Helper delivery over MTP | **built** | Exporter + setup guide; the file appears in the phone's `Download/CableMouse`. Copy to a PC over MTP not verified (blocked on the user's work PC; they use the GitHub release). Only the Windows exe is bundled. |
| 5. PC helper | Windows **verified**; Linux **built**; macOS/APE **todo** | Windows back end hardened for unplug/replug. Linux X11 back end never run with a phone (its read loop and `--stream-check` were run against a FIFO, see Verification). macOS is a stub; the Cosmopolitan APE (`cablemouse.com`) and its spike (step 18) were not started. |
| 6. Robustness | mostly **verified** | App auto-reconnects on device add/remove; heartbeat every 500 ms; helper releases buttons after 2 s without heartbeat; version mismatch warning exists. |
| 7. Extras | keyboard, gyro and absolute **verified** (Windows); rest **todo** | Header buttons **Mode** (Mouse default / Mouse + typing / Keyboard / Gyro / Absolute) and **Protocol** (Wired now; Bluetooth listed, disabled). *Mouse + typing* (system keyboard forwarded as Unicode/special keys, hardware-keyboard shortcuts) and *Keyboard* (full-screen QWERTY, physical keys + held modifiers, forced landscape, key repeat) verified on Windows by the user. Settings: Caps as Ctrl, sticky modifiers (both default off); every on/off mode option is also a toggle button under its mode in the Mode picker (session only; Settings hold the defaults). *Gyro*: round centre button, hold-to-aim setting (default on), speed slider; uses the gyroscope + gravity sensors (`proto/GyroMath.kt`, unit tested), no protocol change; verified by the user. *Absolute*: pad in the PC screen's shape (16:9 / 16:10 / 4:3 / 21:9 setting), forced landscape, primary monitor only, tap-to-click option; verified by the user. The Mode picker scrolls (landscape). Linux typing and absolute are built but untested; macOS none. **todo:** Bluetooth transport, Insert/PgUp/PgDn keys on the on-screen keyboard. |
| Release | **done** | Public repo https://github.com/Kariiem/cablemouse, MIT, releases `v1.0` and `v1.1` (latest, 2026-11-15) with signed APK, Windows exe and checksums. |

### Unreleased changes since v1.1 (built; not yet verified on hardware)
A review of the whole history (2026-11-15) fixed the following. Each needs one check on the phone and PC before it is called verified.
- **Windows helper:** it crashed when `midiInOpen` failed (for example "device busy", code 4) because the status message had mismatched format arguments. Check: start a second copy of the helper while the first holds the device; the second must print `midiInOpen failed (code 4)` and keep retrying.
- **Helper `--stream-check`:** injects nothing at all now (before, only horizontal moves were skipped). Status output is unbuffered.
- **App, stream test:** `Thread.onSpinWait()` (API 33) crashed the stream test on Android 6-12. Check on the Android 15 phone: the stream test still passes at 500 msg/s.
- **App, connection:** the USB port was opened twice at start-up (start + first resume). Check: cold start with USB already in MIDI mode connects, and the Diagnostics log has no `returned null (busy?)` line; replug still reconnects.
- **App, buttons:** a Left/Right button held while leaving its screen (mode switch, app paused) is released on the PC.
- **Linux helper:** prints why it cannot open the device, and treats end of file as "device lost".

### Open items, in suggested order
1. Verify the unreleased changes above on hardware, then release.
2. Run the Linux helper with a real phone on an X11 desktop (move, click, scroll, typing, absolute, unplug/replug).
3. Keyboard mode: Insert / PageUp / PageDown / Home / End keys (the protocol and both back ends already handle them).
4. An msvcrt-based MinGW build of the legacy exe for Windows XP/Vista/7 without the UCRT update (see Risks).
5. Bluetooth transport: a second `link.Transport` on the phone and a matching input source in the helper.
6. Cosmopolitan APE (`cablemouse.com`), starting with the spike in step 18, and the macOS back end.
7. Helper delivery over MTP to a PC is built but was never observed end to end (blocked on the user's work PC).

## Architecture
```
Android app (Kotlin)                          PC helper (C): cablemouse.com (APE, all modern OSes)
                                               + cablemouse-legacy.exe (32-bit Windows; needs the UCRT, see Risks)
 touchpad gestures                             MIDI input from "Android USB Peripheral"
   → mouse frames                                → decode messages
   → MidiInputPort (USB peripheral) ──USB-MIDI──→ → OS cursor/click/scroll injection
 bundled helpers exported to Download/CableMouse  (SendInput / CGEventPost / XTest)
```

### Code structure and boundaries
The Android app is split so that each layer depends only on the ones below it, and screens depend on interfaces, not on the activity:
```
MainActivity            wires everything; owns the window (insets, orientation, back), permissions, helper delivery
  ui/                   Ui (dp + widgets + Palette) · Mode / ModeScreen / ModeController / ModeHost ·
                        MouseScreen (Mouse, Mouse + typing) · KeysScreen · GyroScreen · AbsoluteScreen ·
                        Touchpad · MouseButtons · KeyCapture · SettingsScreen · Onboarding · Picker + ModeMenu · ClickFeedback
  settings/             Settings + Pref<T>: live value (this session) vs save() (start-up default)
  link/                 Transport (interface) · LinkListener · MidiLink (USB-cable implementation)
  proto/                MouseProtocol · FrameScheduler · GyroMath   (pure, unit-tested)
```
- The screens call only `Transport.send/addMove/addScroll`. A Bluetooth transport (the disabled Protocol entry) means writing a second `Transport`; no screen changes.
- A mode is a `ModeScreen` registered in `MainActivity`; options shown in the Mode menu live in `ModeMenu`; persisted defaults live in `Settings` and are edited in `SettingsScreen`.
- Helper (C): `common/protocol` (pure decoder) and `common/stream_check` (pure, tested) · `common/status` (console status lines without repeats) · `backend.h` (platform interface) with `backends/{win,linux,mac}.c` · `main.c` (policy only).
- Whoever holds input down on the PC releases it: each `ModeScreen` on `exit()`/`onPause()` (touchpad drag, Left/Right buttons, modifiers), and the helper on a 2 s heartbeat timeout or a lost device.

### Wire protocol (MIDI channel 1, 3-byte messages, 7-bit safe)
- `B0 01 v`: dx, where v = dx + 64 (range −64..63). Larger moves are split into several messages.
- `B0 02 v`: dy, same encoding.
- `B0 03 v`: scroll wheel, same encoding.
- `90 n 7F` / `80 n 00`: button down / up. n = 0 is left, 1 is right, 2 is middle.
- `B0 7F v`: heartbeat and protocol version, so the helper can show "connected".
- Absolute position (additive): `B0 16 xh`, `B0 17 xl`, `B0 18 yh`, `B0 19 yl` with x, y in 0..16383 as a fraction of the primary screen; the last message commits. Windows uses `SendInput` ABSOLUTE (no VIRTUALDESK, so primary monitor only), Linux `XTestFakeMotionEvent`.
- Keyboard (additive, version stays 1; unknown controllers are ignored by older helpers):
  - `B0 10 a`, `B0 11 b`, `B0 12 c`: one Unicode code point, `U = a<<14 | b<<7 | c`, committed by the third message. The phone writes the 9 bytes at once. Windows types it with `KEYEVENTF_UNICODE`, Linux by binding a spare keycode.
  - `B0 13 k`: special key tap (1 Backspace, 2 Enter, 3 Tab, 4 Esc, 5 Delete, 6 Left, 7 Right, 8 Up, 9 Down, 10 Home, 11 End, 12 PageUp, 13 PageDown).
  - `B0 14 m`: held modifiers, bits 1 Ctrl / 2 Shift / 4 Alt / 8 Win; helper presses/releases the difference and releases all on heartbeat timeout.
  - `B0 15 u`: tap the key with USB HID usage `u` (0x04..0x73) with the held modifiers. Windows maps it to a virtual key, Linux to a keysym and the real keycode.

## Small-step plan
These are the original steps. Where the build ended up different, the step says so in *italics*; the Status table above is the authority on what is done.

### Phase 0: Feasibility spikes (prove the transport before building anything)
1. On the phone, open Settings → Connected devices → USB and select **MIDI**. On a Linux PC run `amidi -l`, and on Windows look in Device Manager under "Sound, video and game controllers". Confirm a MIDI device appears.
2. Write a throwaway Android activity that opens the USB peripheral MIDI device through `MidiManager` and sends `90 3C 7F` when a button is tapped.
3. On the PC, run `amidi -p hw:X -d` (Linux) or a free MIDI monitor (Windows) and confirm the bytes arrive. **Go/no-go point.**
4. Measure the round trip: send 500 messages per second and check that none are dropped and none are delayed.

### Phase 1: Android app skeleton
5. Create the Android project (`com.cablemouse`): Kotlin, plain Views (Compose was dropped to keep dependencies minimal), minSdk 23 (needed for `android.media.midi`), with `<uses-feature android:name="android.software.midi">`.
6. MIDI link (`link/MidiLink.kt`, implementing `link.Transport`): watch for MIDI devices with `MidiManager.registerDeviceCallback`, pick the USB peripheral device, open its input port, and expose `send(bytes)` and a connected state.
7. Status screen that says "Not in MIDI mode, switch USB to MIDI" (with how-to steps) or "Connected".

### Phase 2: Protocol and mouse logic (shared, unit-tested)
8. `MouseProtocol.kt`: encode move/button/scroll messages and split deltas into ±63 chunks. JVM unit tests.
9. `FrameScheduler.kt`: collect touch deltas and flush at about 125 Hz, keeping fractional remainders (float accumulators) so slow moves aren't lost.

### Phase 3: Touchpad UI
10. Full-screen touchpad. One-finger drag moves the cursor.
11. Tap is a left click, two-finger tap is a right click, two-finger vertical drag scrolls, and tap-and-hold drags (button held down). *Built as tap-then-touch: tap, touch again within 0.3 s and drag.*
12. On-screen L/M/R buttons along the bottom. *Built as Left/Right above the pad; no middle button.*
13. Settings (DataStore): sensitivity, acceleration, scroll speed, invert scroll. Add keep-screen-on and haptic feedback. *Built on SharedPreferences (`settings/Settings.kt`, no extra dependency) with a live value and a saved default per setting; no acceleration; the screen is kept on in Gyro mode only.*

### Phase 4: Delivering the helper over MTP
14. Bundle the prebuilt helpers in `app/src/main/assets/helpers/` (currently only `cablemouse-legacy.exe` + README; `cablemouse.com` is not built yet):
    - `cablemouse.com`: the universal APE for any 64-bit Windows 8+, Linux or macOS machine.
    - `cablemouse-legacy.exe`: the 32-bit Windows helper. As built today it needs the Universal C Runtime: Windows 10/11, or 7/8.1 with KB2999226. XP/Vista need a different build (see Risks, not done).
    - `README.txt`, which explains which file to run.
15. On first launch, and whenever the app version changes, copy them through `MediaStore.Downloads` into `Download/CableMouse/` (API 23-28: direct write with the storage permission) so they show up over MTP.
16. In-app onboarding:
    1. Leave USB on File Transfer.
    2. On the PC, open Phone → Download → CableMouse and copy `cablemouse.com`. On a 32-bit PC or one that can't run the APE, copy `cablemouse-legacy.exe` instead (today it is the only helper; see Risks for the Windows versions it runs on).
    3. Switch USB to MIDI.
    4. Run the helper. On Linux/macOS: `sh ./cablemouse.com`, or `chmod +x` it first.

### Phase 5: PC helper (C, no installer, no admin)
One C codebase builds two outputs:
- **`cablemouse.com`**: built with Cosmopolitan `cosmocc` as an Actually Portable Executable. At runtime it picks a back end with `IsWindows()` / `IsXnu()` / `IsLinux()`. Windows DLLs, macOS frameworks and `libXtst` are loaded through `cosmo_dlopen`/`cosmo_dlsym`.
- **`cablemouse-legacy.exe`**: the Windows back end alone, built with MinGW i686 (`-D LEGACY_WIN32`) and linked directly against `winmm`/`user32`. It is the only helper built so far: verified on Windows 10/11; Windows 7/8.1 need KB2999226 (not tested); XP/Vista are not supported by the current build. Supporting them is a todo that needs an msvcrt-based MinGW (see Risks).

17. Repo folder `helper/`. Put the protocol decoder in `common/protocol.c` and add host unit tests for it. `main.c` picks a back end through a small interface: `backend_open()`, `backend_read_midi()`, `backend_move()`, `backend_button()`, `backend_scroll()`, and for the keyboard `backend_key_char()`, `backend_key()`, `backend_mods()`, `backend_key_hid()`.
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
26. Keyboard/text mode, with keys sent as note numbers or SysEx and injected through SendInput, CGEvent or XTest. *Done with control-change messages instead (see Wire protocol): Mouse + typing and Keyboard modes.*
27. Gyroscope "air mouse" mode (done: `GyroScreen.kt`, `GyroMath.kt`).
28. Absolute positioning mode (phone pad maps to the PC's primary screen) — done.

## Critical files
- `app/src/main/java/com/cablemouse/proto/` (protocol encoder, batching, gyro maths) and `link/` (`Transport`, `MidiLink`)
- `app/src/main/java/com/cablemouse/settings/Settings.kt` and `ui/` (`MainActivity` wiring, `ModeController`, the screens)
- `app/src/main/java/com/cablemouse/export/HelperExporter.kt`: MediaStore copy to Download/CableMouse
- `helper/main.c`, `helper/common/{protocol,stream_check,status}.c`, `helper/backends/{win,linux,mac}.c`, `helper/Makefile` (cosmocc and MinGW targets)
- `testvectors/protocol.txt`: shared by the C and Kotlin protocol tests

## Verification
- Phase 0 step 3 proves the transport: bytes from the app reach a PC MIDI monitor.
- Unit tests: Kotlin tests for encoding, chunking, the scheduler, gyro maths, the key layout and settings, and C tests for the decoder and the stream check, using the same test vectors in both.
- Android lint (`:app:lintDebug`) runs in CI and in the release script and must report no errors. It is what catches calls to APIs newer than minSdk 23.
- The Linux helper can be exercised without a phone: `mkfifo f; helper/build/cablemouse-linux "$PWD/f" --stream-check` and write protocol bytes into `f` (it needs an X display to start, and in this mode injects nothing). This covers `main.c` and the read path, not XTest injection.
- End-to-end matrix:
  - `cablemouse-legacy.exe` on Windows 10/11 (verified by the user) and on Windows 7/8.1 with KB2999226 (not tested), and later `cablemouse.com` on Windows 10/11, all as a standard (non-admin) user. Windows XP/Vista are not covered until an msvcrt build exists.
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
- **APE limits.** It needs a 64-bit OS and Windows 8+. That's why the legacy exe target exists (with the runtime caveat above). On macOS, APE starts through the shell, and Apple Silicon needs Cosmopolitan's small loader. The spike (Phase 5, step 18) checks this early.
- **Bluetooth (todo).** The Protocol button lists it but it is disabled. It needs a second `link.Transport` on the phone and a matching input source in the helper; the protocol bytes stay the same.
- **Phone as a webcam (decided: separate app).** Checked on the HONOR X9c (Android 15): the USB chooser offers Transfer photos, Transfer files / Android Auto, Charge only, Reverse charge and MIDI, with no Webcam entry, and an app cannot add a USB gadget function without root. A true USB camera is therefore impossible here. The feasible design is a separate app that streams camera video over a cable network link (USB tethering) to an OBS Media Source (HTTP MJPEG / RTSP / SRT). It does not belong in Cable Mouse, which should keep asking for almost no permissions.
