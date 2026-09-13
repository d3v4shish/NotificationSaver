#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug --no-daemon
apk=app/build/outputs/apk/debug/app-debug.apk
printf 'debug-apk-bytes=%s\n' "$(wc -c < "$apk")"
