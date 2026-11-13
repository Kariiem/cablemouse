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

### Gestures and buttons

| Action | Result |
| --- | --- |
| Drag one finger on the pad | Move the cursor |
| Tap the pad | Left click |
| Two-finger tap | Right click |
| Drag two fingers up/down | Scroll |
| **Left** / **Right** buttons | Mouse buttons (hold to drag; use a second finger on the pad to move) |

Taps and gestures are silent. The Left/Right buttons play a mouse-click sound (can be turned off).

### Settings

Tap the **Cable Mouse** pill at the top. You can set pointer speed, scroll speed and direction, the click sound, and the theme (System, Light or Dark). The Diagnostics card has a test note, a stream test and a clearable log.

### Troubleshooting

The helper prints what it is doing:

| Helper message | Meaning |
| --- | --- |
| `No MIDI input devices found` | The PC doesn't see the phone as a MIDI device. Set USB to MIDI and replug. |
| `MIDI inputs found: [...] - none matched` | The device name doesn't contain "Android". Run `cablemouse-legacy.exe "part of the name"`. |
| `midiInOpen failed (code 4…)` | Another program (a MIDI monitor, a music app) is holding the device. Close it. |
| `MIDI device opened; waiting for phone...` | Ready. Open the app on the phone. |
| `disconnected (no heartbeat)` | The app was closed or the cable unplugged. Held buttons are released. |

The helper reconnects by itself after an unplug/replug. If the cursor feels too slow or fast, change the pointer speed in the app's settings.

## Status and limitations

- **Phone:** needs an Android phone that offers **MIDI** in its USB options (some hide it under Developer options → Default USB configuration).
- **PC:** the prebuilt helper is **Windows only**, and needs Windows 10/11 (Windows 7/8.1 also need the Universal C Runtime update, KB2999226; XP/Vista are not supported by the current build).
- **Linux:** a helper exists (X11 only, via XTest) but is untested on real hardware and isn't prebuilt. Build it with `make -C helper linux`. Wayland is not supported.
- **macOS:** not implemented yet (a stub exists).
- **Single-file universal helper (`cablemouse.com`, Cosmopolitan APE):** planned in [`PLAN.md`](PLAN.md), not built yet.
- **Unsigned helper:** SmartScreen and some antivirus/EDR products flag unsigned programs that inject input. The source is small (`helper/`) and the build is reproducible, so you can build it yourself and compare checksums.
- Not implemented: tap-and-hold drag, keyboard input, gyro air-mouse.

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

### Tests

```sh
make -C helper test            # C: protocol decoder tests (host, no hardware needed)
./gradlew :app:testDebugUnitTest   # Kotlin: protocol encoder and frame scheduler
```

Both read the same vectors from [`testvectors/protocol.txt`](testvectors/protocol.txt), so the C decoder and the Kotlin encoder stay in agreement. The UI, MIDI link, helper back ends and USB behaviour need real hardware and are not covered by automated tests: check them by hand on a phone and a PC (move, tap, right-click, scroll, unplug/replug).

### Project layout

```
app/                 Android app (Kotlin, plain Views, minSdk 23)
  …/ui/              touchpad screen, settings, setup guide, theme, click sound; MainActivity also owns the USB-peripheral MIDI port
  …/proto/           MouseProtocol (encoder) and FrameScheduler (≈125 Hz batching)
  …/export/          HelperExporter: copies the helper to Download/CableMouse
  src/main/assets/helpers/   bundled helper + README (generated by `make -C helper assets`)
helper/              C helper: common/protocol.c decoder, backends/{win,linux,mac}.c, main.c
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

A protocol change must update `PLAN.md`, `MouseProtocol.kt`, `helper/common/protocol.c` and `testvectors/protocol.txt` together.

### Publishing a release

```sh
./scripts/build-release.sh
git tag v1.0
gh release create v1.0 dist/app-release.apk dist/cablemouse-legacy.exe dist/SHA256SUMS.txt \
  --title "Cable Mouse 1.0" --generate-notes
```

The asset names are unversioned on purpose, so the "latest release" download links at the top of this README keep working.

## License

[MIT](LICENSE).
