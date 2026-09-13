# Changelog

## 1.0.0

- Reworked the interface around the fixed technical-utility visual system, with compact controls, semantic color, and clearer navigation.
- Added local source-app launcher icons with bounded background loading and a fallback when an icon is unavailable.
- Made archive cleanup and retention refresh only affected conversation summaries within the same database transaction.
- Rebuilt capture storage around notification lifecycles, content-distinct revisions, reconnect reconciliation, and full-text search.
- Added generic threaded conversations for compatible messaging notifications from any app.
- Added paging-backed Inbox and Chats screens with adaptive phone/tablet layouts.
- Added Material 3 dynamic color plus light, dark, and follow-system modes.
- Added recents privacy, screenshot blocking, and optional biometric or device-credential lock.
- Added versioned AES-256-GCM backup/restore, readable JSON export, and scheduled retention.
- Added local operational health counters and local crash-report capture.
- Added diagnostics export, storage usage reporting, and in-app cleanup flows.
- Added release build hardening, CI workflows, and public-repo documentation.
