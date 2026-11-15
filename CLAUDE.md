# Cable Mouse

Phone + USB cable = mouse for a PC. The phone sends mouse data as USB-MIDI messages (Android "MIDI" USB mode, no root). A small portable PC helper reads the MIDI input and injects cursor/click/scroll events.

- Repo: https://github.com/Kariiem/cablemouse (public, MIT). Latest release is `v1.1` (published 2026-11-15; `v1.0` is the first). Each release holds `app-release.apk`, `cablemouse-legacy.exe`, `SHA256SUMS.txt` (unversioned names on purpose, so `releases/latest/download/<name>` links in the README keep working).
- Docs: `README.md` (users + build/dev guide), `PLAN.md` (design, phases, status table, risks; keep it updated when the design changes).

## Status (2026-11-15)
Everything below is verified by the user on real hardware (Android 15 HONOR phone, Windows 10/11 PC) unless marked otherwise.
- Done: USB-MIDI transport (500 msg/s stream test PASS via `cablemouse-legacy.exe --stream-check`: none lost, longest gap 7.5 ms), Windows helper, touchpad and gestures (incl. tap-then-touch drag), click feedback (silent / audible / vibration), keyboard input, gyro mode, absolute mode, Mode / Protocol buttons, per-mode option toggles (session) over Settings defaults, tappable GitHub link in the setup guide, helper copied into the phone's Download folder.
- Header buttons: Cable Mouse (settings), Mode (Mouse default / Mouse + typing / Keyboard / Gyro / Absolute; the picker scrolls in landscape), Protocol (Wired; Bluetooth is a disabled placeholder).
- Not verified / not built:
  - Helper delivery over MTP *to a PC* (file transfer is blocked on the user's work PC, who uses the GitHub release instead).
  - Linux helper (X11/XTest, written incl. typing and absolute, never run on hardware), macOS helper (stub only), universal `cablemouse.com` Cosmopolitan APE (never built; `make ape` is a TODO).
  - `.github/workflows/ci.yml` (tests + helper builds + debug APK) passes on GitHub (first run 2026-11-15, ~2 min). Its actions are on Node 20 and `setup-java@v4` is flagged deprecated; bump to v5 when convenient.
  - (Resolved) The layered refactor of 2026-11-15 was first smoke-tested on the phone by automation, then verified by the user on the PC: all modes work with the v1.1 exe and APK.
- Windows XP/Vista/7: the exe imports `api-ms-win-crt-*` (UCRT; this MinGW is UCRT-based), so it runs on Win 10/11 (verified), or 7/8.1 + KB2999226 (untested); XP/Vista are NOT supported. PLAN.md's original "legacy = XP/7" idea needs an msvcrt-based MinGW build, not done. Check with `objdump -p helper/build/cablemouse-legacy.exe | grep DLL`.
- On-screen keyboard lacks Insert/PgUp/PgDn/Home/End (they exist in the protocol).
- Camera idea (phone as a webcam for OBS) was investigated and **decided to be a separate app**: this HONOR's USB chooser (Transfer photos, Transfer files / Android Auto, Charge only, Reverse charge, MIDI) has no Webcam/UVC option and an app cannot add a USB gadget function without root, so a true USB camera is impossible. The feasible route is video over a cable network link (USB tethering) to an OBS Media Source. Cable Mouse stays input-only (it asks for almost no permissions).
- Backlog: Bluetooth transport (a second `link.Transport`; the Protocol button already lists it, disabled). Releases are published only when the user asks; v1.1 was, with notes that include a "which mode for what" table.

## Architecture (Kotlin app; keep these boundaries)
Dependencies point downwards; screens never reach into the activity.
- `proto/` pure and unit-tested: `MouseProtocol` (encoder), `FrameScheduler` (fractional-delta batching), `GyroMath`.
- `link/` the only code that knows about USB/MIDI: `Transport` (interface the UI uses: `send`, `addMove`, `addScroll`), `LinkListener` (state/devices/log events, main thread), `MidiLink` (USB-cable implementation: device lifecycle, 125 Hz flush, heartbeat, diagnostics). A Bluetooth link would implement `Transport`.
- `settings/` `Settings` + `Pref<T>`: every setting has a live `value` (this session) and `save()` (start-up default). Nothing else touches SharedPreferences.
- `ui/` `Ui` (dp + widgets + `Palette`), `ButtonFeedback`/`ClickFeedback`, `Mode` enum, `ModeScreen` interface + `ModeController` + `ModeHost` (window needs: landscape, keep-screen-on), the screens (`MouseScreen` for Mouse and Mouse + typing, `KeysScreen` + `KeyLayout`, `GyroScreen`, `AbsoluteScreen`), `Touchpad` (gestures), `MouseButtons`, `KeyCapture` (phone keyboard to `KeySink`), `SettingsScreen`, `Onboarding`, `Picker` + `ModeMenu`, `Gesture` constants, `CrashReport`.
- `MainActivity` only wires these and owns the window (insets, orientation, back, permissions) and helper delivery.
- To add a mode: add a `Mode` value, implement `ModeScreen`, register it in `MainActivity`'s map, add its options in `ModeMenu`. To add a setting: add a `Pref` in `Settings`, a switch/slider in `SettingsScreen`.
- Helper (C): `common/protocol.[ch]` (pure decoder), `common/stream_check.[ch]` (pure, tested), `backend.h` (platform interface) with `backends/{win,linux,mac}.c`, `main.c` (policy: reconnect, heartbeat timeout, release held input).

## Layout
- `app/` — Android app (Kotlin, plain Views built in code, **no Compose**, minSdk 23, targetSdk 35, package `com.cablemouse`; versionName 1.1 / code 2)
  - source packages: `proto/`, `link/`, `settings/`, `ui/`, `export/HelperExporter.kt` (copies the bundled helper to `Download/CableMouse`: MediaStore on API 29+, file write + permission below); `ui/CrashReport.kt` shows the last crash stack trace on next launch (debug aid; there is no logcat access from the app, but `adb logcat` works from this machine).
  - `src/main/assets/helpers/` bundled `cablemouse-legacy.exe` + `README.txt`; generated by `make -C helper assets` and **committed** so the APK builds without MinGW. Rerun and commit whenever helper code changes.
- `helper/` — C helper (see Architecture), `tests/`, `dist/README.txt`, `Makefile`
- `testvectors/protocol.txt` — wire-protocol vectors shared by the C and Kotlin tests
- `scripts/build-release.sh` — tests + helper + signed APK + `dist/` with checksums (`GRADLE=gradle` to use the system Gradle)
- `.github/workflows/ci.yml` — CI (tests, helper builds, debug APK); signs and publishes nothing
- Launcher icon: adaptive vector (`res/drawable/ic_launcher_foreground.xml`) + PNG fallbacks; PNGs were rendered from an SVG with `rsvg-convert`.

## Wire protocol (keep Kotlin and C in sync)
MIDI channel 1, 3-byte messages, all data bytes 7-bit:
- `B0 01 v` dx, `B0 02 v` dy, `B0 03 v` scroll; v = delta + 64 (range -64..63); larger deltas are split into chunks
- `90 n 7F` / `80 n 00` button down/up (0 left, 1 right, 2 middle)
- `B0 7F v` heartbeat / protocol version
- Absolute position: `B0 16 xh`, `B0 17 xl`, `B0 18 yh`, `B0 19 yl` (14-bit x,y as a fraction of the primary screen; last commits).
- Keyboard (additive, version stays 1): `B0 10/11/12` Unicode code point in 7-bit groups (third commits), `B0 13 k` special key, `B0 14 m` held modifiers mask (1 Ctrl, 2 Shift, 4 Alt, 8 Win), `B0 15 u` tap USB HID usage `u`. Details in README.
Any protocol change must update: `PLAN.md`, `proto/MouseProtocol.kt`, `helper/common/protocol.c`, `testvectors/protocol.txt`, and `README.md`.

## Commands and toolchain (this machine: WSL2 Ubuntu-like, user `karim`)
- Env: `export JAVA_HOME=~/opt/temurin-21 PATH=~/opt/temurin-21/bin:$PATH` (JDK is not on PATH by default). Android SDK in `~/opt/android-sdk` (cmdline-tools, platform 35, build-tools 35.0.0, platform-tools), referenced by git-ignored `local.properties`.
- Use the system `gradle` (9.3.1) rather than `./gradlew` here: the wrapper would download 130 MB. Both are the same version. The release script takes `GRADLE=gradle`.
- Tests: `make -C helper test` (35 protocol tests + stream-check tests) and `gradle :app:testDebugUnitTest` (15 Kotlin tests: shared vectors, gyro maths, layout, settings). UI/MIDI/USB behaviour has no automated tests; it needs a phone + PC.
- Helper builds: `make -C helper legacy` (Windows exe, needs `i686-w64-mingw32-gcc`), `make -C helper linux`, `make -C helper assets`.
- Release: `GRADLE=gradle ./scripts/build-release.sh` (needs git-ignored `keystore.properties`; keystore at `~/.cablemouse/release.jks`, passwords only in `keystore.properties`; back it up, losing it blocks updates). Publish with `gh release create v<x> dist/app-release.apk dist/cablemouse-legacy.exe dist/SHA256SUMS.txt` (see README). The Windows exe build is reproducible (`--no-insert-timestamp`); the APK is not.
- No Kotlin/Native shortcut: `kotlinc-native` also needs the JDK.

## Working agreements (from the user)
- **Use git; commit in functional blocks. After every commit the tools must build and the tests must pass.** Verify the *committed* state in a clean `git worktree` (copy `local.properties` and `keystore.properties` into it), not just the dirty tree. Commit trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Outward-facing actions (push, tag, release, overwriting a published release) only when the user asks; they have explicitly authorized each one so far. Commit author email is public in the repo history.
- Files for the user to test on Windows go in `C:\Users\Public\CableMouse\` (`/mnt/c/Users/Public/CableMouse/`). Keep **only the newest build there, with unversioned names** (`app-release.apk`, `cablemouse-legacy.exe`). A running exe is locked by Windows: ask the user to close it before overwriting/deleting.
- The user's work PC runs Cortex XDR that reports to an admin; never suggest evading it. The exe is unsigned input-injection software and may be flagged.
- Don't claim hardware behaviour without the user's confirmation; mark things verified in `PLAN.md` only when they actually are.
- No root, no admin, no drivers, no installer: don't add features that require them.

## Conventions and gotchas
- Helper: plain C99, OS libs only (dlopen/LoadLibrary for optional ones). Backends implement `backend_open/read_midi/move/button/scroll` plus keyboard `key_char/key/mods/key_hid` (`helper/backend.h`). The decoder is pure so it's host-testable. Release held buttons on disconnect (heartbeat timeout 2 s).
- Windows winmm often does not report an unplug and leaves a dead handle; `win.c` polls device presence, closes handles on a timeout thread, `main.c` reopens after 6 s idle and re-execs itself after 10 s of the device being unavailable.
- Windows helper picks the MIDI input by name substring (`Android`, or the first CLI argument), or the only device if there is exactly one.
- Android: on API 30+ call `window.insetsController` only after `setContentView` (earlier causes a start-up crash). Targeting SDK 35 is edge-to-edge, so the root view applies system-bar insets. Don't write `/*` inside KDoc (Kotlin nests block comments), e.g. `assets/helpers/*`.
- `MidiManager` exposes the phone's USB side as `type=1 (TYPE_USB) "Android USB Peripheral Port"` with one input port; the app writes to that input port to send to the PC.
- The in-app text mentions the GitHub Releases page but has no URL link yet.
