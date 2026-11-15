# Cable Mouse

Use your Android phone as a mouse for a PC, over the phone's normal USB cable. No Bluetooth, no root, no drivers and no installer on the PC.

Without root, Android can't pretend to be a USB mouse. So Cable Mouse uses **USB-MIDI** instead: in "MIDI" USB mode every PC already has a built-in driver for the phone. The app sends mouse movements as tiny MIDI messages, and a small **helper program** on the PC turns them into cursor moves, clicks and scrolling.

```
Phone app ──USB-MIDI──▶ helper on the PC ──▶ OS cursor / click / scroll
```

## Download

Prebuilt files are on the **[latest release page](../../releases/latest)**:

| File | What it is |
| --- | --- |
| [`app-release.apk`](../../releases/latest/download/app-release.apk) | The Android app (Android 6.0+, signed) |
| [`cablemouse-legacy.exe`](../../releases/latest/download/cablemouse-legacy.exe) | The helper for Windows 10/11 |
| [`SHA256SUMS.txt`](../../releases/latest/download/SHA256SUMS.txt) | Checksums for both files |

Verify the downloads with `sha256sum -c SHA256SUMS.txt` (Linux/macOS) or `certutil -hashfile <file> SHA256` (Windows).

## Usage guide

You need the app on the phone and the helper on the PC.

1. **Install the app.** Copy `app-release.apk` to the phone and open it (allow "install unknown apps" if asked).
2. **Get the helper onto the PC.** Either:
   - **Over USB (no internet needed):** plug the phone in with USB set to **File transfer**. The app copies the helper to `Download/CableMouse` on the phone the first time it runs. On the PC open the phone → Download → CableMouse and copy `cablemouse-legacy.exe` anywhere. If the files don't show up, unplug and replug the cable. The app's **Setup guide** (Settings → Connection) walks through this.
   - **From GitHub:** if file transfer is blocked or unavailable on that PC, download `cablemouse-legacy.exe` from the release page above.
3. **Switch the phone to MIDI.** Tap the USB notification (or Settings → Connected devices → USB) and choose **MIDI**.
4. **Run the helper.** Double-click `cablemouse-legacy.exe`. Windows SmartScreen may warn because the file is unsigned: choose **More info → Run anyway**. The console prints `connected` once the app is open on the phone.
5. **Open Cable Mouse on the phone** and use the touchpad. The dot in the title pill turns green when the USB MIDI port is open.

### Modes and protocol

The header has three buttons: **Cable Mouse** (settings), **Mode** and **Protocol**.

- **Mode** picks what the phone acts as. **Mouse** is the default and the way back from the others (also the Back gesture, or the **Mouse** button on the full-screen modes).
  - **Mouse**: touchpad with Left/Right buttons (below).
  - **Mouse + typing**: the touchpad plus the phone's own keyboard (see Typing).
  - **Keyboard**: a full-screen PC keyboard (see Typing).
  - **Gyro**: the cursor follows the phone's rotation (see Gyro mode).
  - **Absolute**: touch a spot on a pad shaped like the PC screen and the cursor jumps there (see Absolute mode).
- Under each mode the picker shows that mode's on/off options as toggle buttons (filled = on): *Invert scroll* (Mouse), *Caps = Ctrl* (Mouse + typing, Keyboard), *Sticky modifiers* (Keyboard), *Hold to aim* (Gyro), *Tap to click* (Absolute). Tapping one changes it **for the current session only**. The same switches in Settings are the **defaults** loaded at every start.
- **Protocol** picks how the phone talks to the PC: **Wired** (USB cable, MIDI) today; **Bluetooth** is listed but disabled until it is built.

### Gestures and buttons

| Action | Result |
| --- | --- |
| Drag one finger on the pad | Move the cursor |
| Tap the pad | Left click |
| Tap, then touch again and drag (within 0.3 s) | Drag: hold the left button until you lift (a quick second tap is a double click) |
| Two-finger tap | Right click |
| Drag two fingers up/down | Scroll |
| **Left** / **Right** buttons | Mouse buttons (hold to drag; use a second finger on the pad to move) |

Taps and gestures on the pad are silent. The Left/Right buttons give feedback you choose in Settings → **Click feedback**: silent, an audible mouse click, or a short vibration (a firmer pulse on press, a light one on release).

### Typing

Two ways to type on the PC. Both need a helper from this version or newer (older helpers silently ignore keys).

- **Mouse + typing** mode: opens the phone's own keyboard. Letters, any Unicode character (é, €, emoji), Backspace and Enter are typed on the PC, and a row of Esc / Tab / Del / Home / End / arrow keys appears above the pad. A hardware keyboard plugged into the phone also works, including Ctrl/Alt/Win shortcuts.
- **Keyboard** mode: the whole screen becomes a PC QWERTY keyboard (Esc, F1-F12, number row, Tab, Caps, Shift, Ctrl, Win, Alt, Space, arrows). It switches the phone to landscape while open; **Mouse** or Back returns. Keys are sent as physical keys, so shortcuts work: hold Ctrl with one finger and tap C with another. Modifiers behave like on a real keyboard (down while touched), so a lone tap on Win opens the Start menu and a lone tap on Alt focuses the menu bar. Held keys repeat. Keys are physical positions, so on a non-US PC layout the symbol keys type what that layout puts there, not the label.

Settings → **Keyboard**:
- *Caps Lock acts as Ctrl*: the Caps key in keyboard mode becomes Ctrl, and Caps on a hardware keyboard acts as Ctrl in Mouse + typing mode.
- *Sticky modifiers*: a short tap on Shift / Ctrl / Alt / Win keeps it on for the next key; tap it again to cancel. Off by default.

### Gyro mode

Pick **Gyro** in Mode. A large circle sits in the middle, with Left/Right buttons above it. Turn the phone left/right and tilt it up/down to move the cursor (it works with the phone lying flat like a remote or held upright). The screen stays on while this mode is open.

- *Hold to aim* (default on; Mode picker button or Settings → **Gyro mode**): the cursor follows the phone only while you hold the circle; use Left/Right to click. With it off, the cursor always follows the phone and the circle becomes a left-click button (hold it while turning to drag).
- *Gyro speed* sets how many pixels the cursor moves per 10° of rotation.

### Absolute mode

Pick **Absolute** in Mode (it switches the phone to landscape). The pad is drawn in the shape of your PC screen, and where a finger touches the pad is where the cursor goes on the PC's **primary** screen, like a drawing tablet. Dragging moves it; Left/Right sit below the pad, and a quick tap clicks (the *Tap to click* option). The phone can't know the PC's screen shape, so choose 16:9, 16:10, 4:3 or 21:9 in Settings → **Absolute mode** (16:9 by default). On a multi-monitor PC only the primary screen is used. Positions are sent as fractions of the screen, so the PC's resolution doesn't matter.

### Settings

The setup guide (Settings → Connection → Setup guide) has a tappable link to the GitHub Releases page and a Copy link button, for PCs where file transfer is blocked.

Tap the **Cable Mouse** pill at the top. You can set pointer speed, scroll speed and direction, the click feedback, the keyboard options, and the theme (System, Light or Dark). The Diagnostics card has a test note, a stream test and a clearable log.

### Troubleshooting

The helper prints what it is doing:

| Helper message | Meaning |
| --- | --- |
| `No MIDI input devices found` | The PC doesn't see the phone as a MIDI device. Set USB to MIDI and replug. |
| `MIDI inputs found: [...] - none matched` | The device name doesn't contain "Android". Run `cablemouse-legacy.exe "part of the name"`. |
| `midiInOpen failed (code 4…)` | Another program (a MIDI monitor, a music app) is holding the device. Close it. |
| `MIDI device opened; waiting for phone...` | Ready. Open the app on the phone. |
| `disconnected (no heartbeat)` | The app was closed or the cable unplugged. Held buttons are released. |

To check the link itself, run the helper from a terminal as `cablemouse-legacy.exe --stream-check`, then tap **Settings → Diagnostics → Stream test** on the phone. The phone sends 5000 messages at 500 per second and the helper prints how many arrived, how many were missing or repeated, and the longest gap, ending in `PASS` or `FAIL`. Nothing is injected on the PC during the check (no cursor movement, clicks or keys).

The helper reconnects by itself after an unplug/replug. If the cursor feels too slow or fast, change the pointer speed in the app's settings.

## Status and limitations

- **Phone:** needs an Android phone that offers **MIDI** in its USB options (some hide it under Developer options → Default USB configuration).
- **PC:** the prebuilt helper is **Windows only**, and needs Windows 10/11 (Windows 7/8.1 also need the Universal C Runtime update, KB2999226; XP/Vista are not supported by the current build).
- **Linux:** a helper exists (X11 only, via XTest) but is untested on real hardware and isn't prebuilt. Build it with `make -C helper linux`. Wayland is not supported.
- **macOS:** not implemented yet (a stub exists).
- **Single-file universal helper (`cablemouse.com`, Cosmopolitan APE):** planned in [`PLAN.md`](PLAN.md), not built yet.
- **Unsigned helper:** SmartScreen and some antivirus/EDR products flag unsigned programs that inject input. The source is small (`helper/`) and the build is reproducible, so you can build it yourself and compare checksums.
- Not implemented: Bluetooth. Absolute mode maps to the primary screen only (Windows verified; the Linux helper's version is untested). Gyro mode is verified on hardware. Keyboard input exists but is only verified on the Windows helper; the Linux helper's typing is untested and macOS has none.
- Keyboard mode has no Insert/PageUp/PageDown/Home/End keys (they exist in the protocol; Mouse + typing mode's key row has Home and End).

## Development

### Needed tools

| Tool | Used for | Notes |
| --- | --- | --- |
| JDK 21 | Gradle, Kotlin, signing | e.g. Temurin 21 |
| Android SDK | Building the app | cmdline-tools, **platform 35**, **build-tools 35.0.0**; `platform-tools` (adb) is handy for installing |
| `make`, a C compiler (`gcc`/`cc`) | Helper tests, Linux helper | |
| MinGW-w64 i686 (`i686-w64-mingw32-gcc`) | Windows helper (`cablemouse-legacy.exe`) | e.g. `apt install gcc-mingw-w64-i686` |
| `keytool` (comes with the JDK) | Creating a release signing key | Only for release builds |
| `cosmocc` (Cosmopolitan) | Universal helper | Optional; not used yet |
| `rsvg-convert` | Regenerating launcher icon PNGs | Optional |

Gradle itself comes from the included wrapper (`./gradlew`, Gradle 9.3.1), so you don't need to install it.

Point the build at your SDK once, in `local.properties` (git-ignored):

```properties
sdk.dir=/path/to/android-sdk
```

(or set `ANDROID_HOME`). Make sure `JAVA_HOME` points to JDK 21.

### Build

```sh
# 1. Helper: tests, then the Windows exe, copied into the app's assets (bundled into the APK)
make -C helper test
make -C helper assets          # also: make -C helper linux

# 2. Android app
./gradlew :app:assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
```

The bundled helper files in `app/src/main/assets/helpers/` are committed, so the app builds even without MinGW. Re-run `make -C helper assets` and commit the result whenever the helper changes.

Install a debug build with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### Signing and release build

Release APKs are signed with a key you create once and keep private:

```sh
mkdir -p ~/.cablemouse
keytool -genkeypair -keystore ~/.cablemouse/release.jks -alias cablemouse \
  -keyalg RSA -keysize 3072 -validity 10000
```

Then create `keystore.properties` in the repo root (git-ignored):

```properties
storeFile=/home/you/.cablemouse/release.jks
storePassword=...
keyAlias=cablemouse
keyPassword=...
```

Back up the keystore: without it you can't publish updates that install over existing installs. Without `keystore.properties` the release APK is built **unsigned**.

Build everything in one go:

```sh
./scripts/build-release.sh
```

This runs the helper tests and Kotlin unit tests, builds the helper and the signed APK, verifies the signature and writes `dist/app-release.apk`, `dist/cablemouse-legacy.exe` and `dist/SHA256SUMS.txt`. The Windows exe is built reproducibly (no embedded timestamp), so the same source gives the same checksum.

### Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request: the C tests, the Linux and Windows (MinGW) helper builds, the Kotlin unit tests and a debug APK (uploaded as an artifact). It signs and publishes nothing; releases stay manual. It passes on GitHub (first run 2026-11-15, about 2 minutes).

### Tests

```sh
make -C helper test            # C: protocol decoder and stream-check tests (host, no hardware needed)
./gradlew :app:testDebugUnitTest   # Kotlin: protocol encoder, frame scheduler, gyro maths, settings
```

Both read the same vectors from [`testvectors/protocol.txt`](testvectors/protocol.txt), so the C decoder and the Kotlin encoder stay in agreement. The UI, MIDI link, helper back ends and USB behaviour need real hardware and are not covered by automated tests: check them by hand on a phone and a PC (move, tap, right-click, scroll, unplug/replug).

### Project layout

```
app/                 Android app (Kotlin, plain Views, minSdk 23)
  …/proto/           MouseProtocol (encoder), FrameScheduler (≈125 Hz batching), GyroMath: pure and unit-tested
  …/link/            Transport (what the screens use) and MidiLink (the USB-cable implementation); a Bluetooth transport would go here
  …/settings/        Settings: every setting, with a live value and a saved default
  …/ui/              MainActivity (wiring only), one screen per mode, settings, setup guide, pickers, feedback
  …/export/          HelperExporter: copies the helper to Download/CableMouse
  src/main/assets/helpers/   bundled helper + README (generated by `make -C helper assets`)
helper/              C helper: common/ (protocol decoder, stream check), backends/{win,linux,mac}.c, main.c
testvectors/         wire-protocol vectors shared by the C and Kotlin tests
scripts/             build-release.sh
PLAN.md              design, phases and known risks
```

### Wire protocol

MIDI channel 1, 3-byte messages, all data bytes 7-bit:

| Message | Meaning |
| --- | --- |
| `B0 01 v`, `B0 02 v` | cursor dx, dy; `v = delta + 64` (−64…63); larger moves are split into several messages |
| `B0 03 v` | scroll wheel, same encoding |
| `90 n 7F` / `80 n 00` | button `n` down / up (0 left, 1 right, 2 middle) |
| `B0 7F v` | heartbeat (every 500 ms) carrying the protocol version |
| `B0 10 a`, `B0 11 b`, `B0 12 c` | type one Unicode code point `U = a<<14 \| b<<7 \| c`; the third message commits it (sent as one 9-byte write) |
| `B0 13 k` | tap a special key: 1 Backspace, 2 Enter, 3 Tab, 4 Esc, 5 Delete, 6 Left, 7 Right, 8 Up, 9 Down, 10 Home, 11 End, 12 PageUp, 13 PageDown |
| `B0 14 m` | modifiers held right now, bit mask 1 Ctrl, 2 Shift, 4 Alt, 8 Win; the helper presses/releases the difference. 0 releases all (also done on a 2 s heartbeat timeout) |
| `B0 15 u` | tap the physical key with USB HID keyboard usage `u` (0x04..0x73) with the modifiers currently held |
| `B0 16 xh`, `B0 17 xl`, `B0 18 yh`, `B0 19 yl` | put the cursor at an absolute position: `x = xh<<7 \| xl`, `y = yh<<7 \| yl`, each 0..16383 as a fraction of the primary screen; the last message commits it (sent as one 12-byte write) |

New messages are additive: a helper that doesn't know a controller number ignores it, so the protocol version stays 1.

A protocol change must update `PLAN.md`, `MouseProtocol.kt`, `helper/common/protocol.c` and `testvectors/protocol.txt` together.

### Publishing a release

```sh
./scripts/build-release.sh
git tag v1.1
gh release create v1.1 dist/app-release.apk dist/cablemouse-legacy.exe dist/SHA256SUMS.txt \
  --title "Cable Mouse 1.1" --generate-notes
```

The asset names are unversioned on purpose, so the "latest release" download links at the top of this README keep working.

## License

[MIT](LICENSE).
