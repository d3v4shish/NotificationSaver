# Contributing

## Ground Rules

- Keep changes small and explicit.
- Prefer direct code over new abstraction layers.
- Do not add hosted telemetry or remote analytics.
- Keep all notification data handling local-first and privacy-conscious.

## Development Setup

```bash
./gradlew clean testDebugUnitTest assembleDebug
```

For emulator/device UI checks:

```bash
./gradlew connectedDebugAndroidTest
```

## Before Opening A PR

- Run `./gradlew clean lint testDebugUnitTest assembleDebug`.
- If your change touches UI flows, also run `./gradlew connectedDebugAndroidTest`.
- Update `README.md`, `CHANGELOG.md`, or docs when user-facing behavior changes.
- Do not commit keystores, `keystore.properties`, or generated `dist/landing-page/` files.

## Pull Request Expectations

- Explain the user-visible outcome.
- Call out behavior changes, privacy impact, and test coverage.
- Include screenshots for UI changes when practical.

