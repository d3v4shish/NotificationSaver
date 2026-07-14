# Releasing

## Versioning

- Use semver tags: `vX.Y.Z`.
- Keep `versionName` in `app/build.gradle.kts` aligned with the release tag.
- `versionCode` is derived from `major * 10000 + minor * 100 + patch`.

## Local Checklist

1. Update `CHANGELOG.md`.
2. Run `./gradlew clean lint testDebugUnitTest connectedDebugAndroidTest assembleRelease`.
3. Confirm screenshots and docs still match the shipped UI.
4. Confirm `keystore.properties` is present locally if you want a signed local APK.

## CI Release Secrets

Configure these GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Tag Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The release workflow will:

- verify the tag matches `versionName`
- decode the keystore
- build a signed release APK
- publish the APK, `mapping.txt`, and a SHA-256 checksum to GitHub Releases

