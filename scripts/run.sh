#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the target device ID.}"
cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug --no-daemon
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" shell am start -n dev.d3v.notificationsaver/.MainActivity
