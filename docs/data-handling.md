# Data Handling

## Storage Areas

- Room database: lifecycle records, content-distinct revisions, conversations, and extracted text messages
- Preferences DataStore: user settings, capture exclusions, category rules, and privacy preferences
- Logs: local runtime logs rotated in app storage
- Crash reports: local uncaught exception reports with retention limits
- Cache: transient app-managed files
- Package visibility: source app package names and launcher icons are resolved locally to label archive rows; this inventory is not stored separately or sent off-device

## Diagnostics Export

Diagnostics bundles include:

- app version and build metadata
- device metadata
- storage bucket summary
- local operational counters
- log files
- crash reports

Diagnostics bundles exclude:

- notification titles
- notification bodies
- notification big text
- database exports

## Retention

- New installs keep notification history until the user deletes it
- Optional retention removes only ended records, runs at startup, after capture batches, and periodically
- Log files rotate locally
- Crash reports are capped by file count and total size

## Backups

- Portable `.nsbackup` files use AES-256-GCM authenticated encryption and a password-derived key
- Readable JSON exports are explicitly unencrypted
- Non-sensitive settings can optionally be restored; authentication and screenshot settings remain device-local
- Android system backup and device-transfer extraction are disabled for app-private notification data
