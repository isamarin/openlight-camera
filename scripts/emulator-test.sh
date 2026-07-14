#!/usr/bin/env bash
# Install APK, push emulator feature flags, launch app, stream logcat.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${APK:-$ROOT/build/openlight_camera.apk}"
PACKAGE="openlight.co.lightcamera"
ACTIVITY="openlight.co.camera.CameraActivity"
FEATURES="${FEATURES:-$ROOT/features.prop.emulator}"

die() { echo "Error: $*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || die "adb not found on PATH"

if ! adb get-state >/dev/null 2>&1; then
  die "No adb device or emulator connected"
fi

[[ -f "$APK" ]] || die "APK not found: $APK (run: make apk)"
[[ -f "$FEATURES" ]] || die "Features file not found: $FEATURES"

echo "==> Installing $APK"
adb install -r "$APK"

echo "==> Pushing feature flags to /sdcard/features.prop"
adb push "$FEATURES" /sdcard/features.prop

echo "==> Granting runtime permissions"
adb shell pm grant "$PACKAGE" android.permission.CAMERA 2>/dev/null || true
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
adb shell pm grant "$PACKAGE" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb shell pm grant "$PACKAGE" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

echo "==> Starting camera activity"
adb shell am start -n "$PACKAGE/$ACTIVITY"

echo "==> Logcat (Ctrl+C to stop)"
adb logcat -c
adb logcat | rg -i "openlight|CameraManager|CameraInfo|FeatureManager|AndroidRuntime|FATAL|BasePreviewFragment"