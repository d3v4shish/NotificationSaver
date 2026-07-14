# Data Handling

## Storage Areas

- Database: saved notifications and user edits
- Shared preferences: settings and pinned thread ids
- Logs: local runtime logs rotated in app storage
- Crash reports: local uncaught exception reports with retention limits
- Cache: transient app-managed files

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

- Notification retention follows the retention setting in the app
- Log files rotate locally
- Crash reports are capped by file count and total size

