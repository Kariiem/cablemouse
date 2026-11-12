#!/usr/bin/env bash
# Builds everything and collects the release files in dist/:
#   app-release.apk, cablemouse-legacy.exe, SHA256SUMS.txt
# Needs: JDK 21, Android SDK (see local.properties), i686-w64-mingw32-gcc, make, and keystore.properties.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f keystore.properties ] || { echo "keystore.properties is missing: the release APK would be unsigned (see README, 'Signing')." >&2; exit 1; }
[ -f local.properties ] || [ -n "${ANDROID_HOME:-}" ] || { echo "Android SDK not found: create local.properties with sdk.dir=... or set ANDROID_HOME." >&2; exit 1; }

echo "==> Helper: tests, Windows exe, bundle into app assets"
make -C helper test
make -C helper assets

echo "==> Android: unit tests and signed release APK"
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease

echo "==> Collecting release files"
rm -rf dist && mkdir dist
cp app/build/outputs/apk/release/app-release.apk dist/
cp helper/build/cablemouse-legacy.exe dist/
(cd dist && sha256sum app-release.apk cablemouse-legacy.exe > SHA256SUMS.txt)

# The committed bundled exe must match what was just built, or the APK ships a stale helper.
if ! git diff --quiet -- app/src/main/assets/helpers; then
    echo "WARNING: app/src/main/assets/helpers changed; commit it and rebuild so the APK matches the source." >&2
fi

APKSIGNER=$(ls -d "$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || echo "${ANDROID_HOME:-}")"/build-tools/*/apksigner 2>/dev/null | tail -1 || true)
[ -n "$APKSIGNER" ] && "$APKSIGNER" verify dist/app-release.apk && echo "APK signature verified."
echo; cat dist/SHA256SUMS.txt
