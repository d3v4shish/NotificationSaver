#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./gradlew :app:testDebugUnitTest --no-daemon

if [[ "${RUN_ANDROID_TESTS:-0}" == "1" ]]; then
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL when RUN_ANDROID_TESTS=1.}"
    ./gradlew :app:connectedDebugAndroidTest --no-daemon
fi
