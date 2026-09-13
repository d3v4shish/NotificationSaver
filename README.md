# Notification Saver

[![Android CI](https://github.com/d3v4shish/NotificationSaver/actions/workflows/android-ci.yml/badge.svg)](https://github.com/d3v4shish/NotificationSaver/actions/workflows/android-ci.yml)
[![Release Build](https://github.com/d3v4shish/NotificationSaver/actions/workflows/release-build.yml/badge.svg)](https://github.com/d3v4shish/NotificationSaver/actions/workflows/release-build.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache%202.0-173D2F)](LICENSE)

Notification Saver is a local-first Android app that records notification lifecycles and meaningful content updates in a searchable archive. Compatible messaging notifications are also reconstructed as conversations, regardless of source app.

## Why It Exists

- Notification history stays on your device instead of being sent to a hosted backend.
- MessagingStyle, shortcut, locus, sender, and conversation metadata are used to build best-effort chat threads.
- Repeated callbacks and noisy progress changes are deduplicated while content-distinct revisions are retained.
- Cleanup, encrypted backup and restore, diagnostics, and storage usage are controlled in the app.

## Highlights

- Paging-backed Inbox with full-text search, app, category, and date filters
- Generic Chats view with app filtering, pinning, renaming, and extracted message history
- Adaptive phone/tablet navigation using Material 3, edge-to-edge layouts, and a fixed technical utility theme
- Privacy controls for list previews, recents previews, screenshots, and device authentication
- Keep-until-deleted default plus optional scheduled retention and per-app category rules
- Password-encrypted, versioned backup/restore and readable JSON export through the Android document picker
- Local log rotation, local crash reports, and visible operational counters
- Clear first-run disclosure and notification-listener permission guidance

## Install

### Option 1: Download A Release

Use the latest signed APK from [GitHub Releases](https://github.com/d3v4shish/NotificationSaver/releases/latest).

### Option 2: Build Locally

Debug build:

```bash
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release build:

```bash
cp keystore.properties.example keystore.properties
./gradlew clean assembleRelease
```

If signing values are configured through `keystore.properties` or the `ANDROID_KEYSTORE_*` environment variables, the release build will use them automatically.

## Privacy And Data Handling

- Captured text and metadata are stored locally in the app database; notification images and attachments are not copied.
- The app declares no internet permission and has no upload path for notification contents.
- Android may redact or withhold some notifications, and history cannot be recovered for periods when listener access was unavailable.
- Diagnostics bundles include app health metadata, logs, crash reports, and storage summaries.
- Diagnostics bundles intentionally exclude notification titles, bodies, big text, and database exports.

More detail:

- [Privacy Policy](docs/privacy.md)
- [Data Handling Notes](docs/data-handling.md)

## Verification

Core checks:

```bash
./gradlew clean lint testDebugUnitTest assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest
```

GitHub Actions also runs:

- Android CI on pushes and pull requests
- Dependency review for pull requests
- CodeQL analysis
- Release packaging and publishing for version tags

For reproducible local commands, see [Build and Run](BUILD.md).

## Project Docs

- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)
- [Changelog](CHANGELOG.md)
- [Releasing](RELEASING.md)
- [Screenshot Capture Checklist](docs/screenshot-capture.md)
- [Architecture](ARCHITECTURE.md)
- [Benchmarks](BENCHMARKS.md)
- [Hotspots](HOTSPOTS.md)

## Notes

- Thread grouping is best-effort and depends on notification metadata exposed by each source app.
- The committed screenshots use the built-in debug demo dataset.
- `dist/landing-page/` is intentionally generated locally and ignored by Git.
