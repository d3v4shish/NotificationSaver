# TODO

## Root-tab chrome reduction

- [x] Remove redundant top app bars from the primary Home, Chats, and Settings destinations.
  - Contract: primary navigation remains available through the bottom bar or navigation rail; detail and nested Settings pages retain their Back affordance.
  - Validation: lint, deterministic JVM tests, debug APK build, and an on-device launch pass. A device screenshot was intentionally not captured because it could expose private notification content.

## App-icon performance and correctness pass

- [x] Establish a package-size baseline and audit source-app icon loading, capture, and retention paths.
  - Contract: icon resolution must not block list composition; cleanup must keep records and conversation summaries consistent.
  - Validation: before/after benchmark records 25,505,875 bytes; lint, 10 JVM tests, and debug/release/Android-test builds pass.
- [x] Move app-icon loading off the UI thread and cache only bounded, local visual data.
  - Contract: installed launcher icons render when available; unavailable packages retain the initial fallback.
  - Validation: the updated APK launches on the attached phone without clearing saved notifications; launcher resolution remains local and its Android permission is granted.
- [x] Make destructive record cleanup atomic with conversation-summary maintenance.
  - Contract: no observable state can contain removed records paired with stale empty conversations.
  - Validation: affected summaries are selected, deleted records are committed, and only those summaries are refreshed in one Room transaction; lint, 10 JVM tests, and debug/release builds pass.

## Correctness, UI/UX, and performance pass

- [x] Establish a reproducible baseline and audit the existing test, lint, and release-build results.
  - Contract: report only measurements produced by the documented local scripts or Gradle tasks.
  - Validation: `:app:lintDebug` has 0 errors; 10 JVM tests pass; debug/release and Android-test APKs compile. Home, Chats, and Settings were inspected on the attached Android phone.
- [x] Fix correctness and interaction issues found in the Home, Chats, and Settings flows.
  - Contract: active records are not confused with selections; navigation and filter controls remain accessible and stateful.
  - Validation: deterministic date-window and FTS-query tests pass; Settings shortcuts have a directional affordance; on-device dark-mode screenshots confirm readable Home and Settings status treatment.
- [x] Make only measured or demonstrably allocation-free performance changes, then record the baseline and final result.
  - Contract: no performance improvement is claimed without a comparable measurement.
  - Validation: `scripts/benchmark.sh` records 25,505,875 bytes both before and after; static hotspots are documented in `HOTSPOTS.md`.

## Technical utility visual system

- [x] Replace dynamic Material colors and oversized rounded shapes with the documented warm-neutral palette, compact geometry, visible borders, and semantic status colors.
  - Contract: neutral UI chrome; blue is interactive, green healthy, amber pending, red destructive/error, purple derived/AI, grey inactive.
  - Validation: `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebugAndroidTest` pass.
- [x] Apply the shared card/control treatment to Home, archive/chats rows, detail screens, settings, and feedback states.
  - Contract: unselected cards, including healthy Home status and active-notification rows, use restrained 2dp hard shadows without an outline; selections have a 2dp outline (blue only in light mode), while semantic status is conveyed by icons and labels.
- [ ] Install on a non-personal emulator and inspect Home and Settings visually.
  - Contract: verify the built APK's warm-neutral surfaces, sharp bordered controls, and semantic success/error colors at runtime without altering personal-device data.
  - Validation: `adb install -r app/build/outputs/apk/debug/app-debug.apk` followed by a screenshot/accessibility-tree review.
- [x] Remove non-essential explanatory UI copy while keeping consent, privacy, destructive-action, and permission disclosures clear.
  - Contract: no behavior or data-handling disclosure is removed.
  - Validation: Android test APK compiles; runtime execution awaits a non-personal emulator.
- [x] Document the actual build/run/test/benchmark workflow and architecture.
  - Contract: a clean checkout has explicit commands and no undocumented performance claim.
  - Validation: `scripts/build.sh`, `scripts/test.sh`, and `scripts/benchmark.sh` pass with JDK 17 and SDK Platform 36.
