# Build and Run

Requirements: JDK 17 and Android SDK Platform 36. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`); set `JAVA_HOME` when JDK 17 is not the default.

```bash
bash scripts/build.sh
bash scripts/test.sh
ANDROID_SERIAL=<device-id> bash scripts/run.sh
bash scripts/benchmark.sh
```

`build.sh` produces `app/build/outputs/apk/debug/app-debug.apk`. `run.sh` installs that APK on the explicit `ANDROID_SERIAL` device and launches the app. `test.sh` runs deterministic JVM tests; add `ANDROID_SERIAL=<device-id> RUN_ANDROID_TESTS=1` to also run instrumentation tests. `benchmark.sh` records the debug APK package size only; it is not a runtime-performance measurement.
