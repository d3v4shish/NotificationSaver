# Architecture

Notification Saver is a single-module, local-first Android app built with Kotlin and Jetpack Compose.

- `MainActivity` hosts Compose navigation for Home, Chats, record/conversation details, onboarding, and focused settings pages.
- `MainViewModel` combines UI filters and settings flows, dispatches user actions, and owns paging/lifecycle state.
- `NotificationCaptureService` receives Android notification callbacks; `NotificationIngestor` serializes capture work before `NotificationRepository` persists it.
- Room stores records, revisions, conversations, messages, active notification state, and full-text search. DataStore stores app settings.
- Compose resolves source-app launcher icons locally through Android's package manager on an I/O dispatcher. A bounded in-memory cache retains at most 48 decoded icons; package visibility is used only to present captured notification sources.
- Repository and backup work run on coroutines/IO dispatchers. Record cleanup selects and refreshes only affected conversation summaries in the same Room transaction as deletion. The app has no network data path; backup/export uses Android document URIs selected by the user.

The Compose theme is intentionally fixed rather than dynamically colored so the shared technical visual language is consistent across devices.
