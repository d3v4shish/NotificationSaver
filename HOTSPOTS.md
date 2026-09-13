# Hotspots

- Notification capture is serialized before database writes to preserve lifecycle ordering and prevent concurrent callback contention.
- Archive and conversation lists use Paging; search text is trimmed, debounced, and deduplicated before a new paging source is created.
- Backup/export and storage accounting perform file and database I/O off the main thread.
- FTS query normalization runs on each debounced search update. Its whitespace and unsupported-character regular expressions are shared rather than recreated per query.
- The notification channel intentionally remains unbounded so capture events are not dropped during short bursts. Its single consumer persists batches of at most 100 events; the queue-depth metric now increments before an event becomes observable to that consumer, avoiding undercounting from a producer/consumer race.
- Source-app icons are decoded on `Dispatchers.IO`, outside list composition. The two 48-entry in-memory caches hold only resolved bitmaps and unavailable package names for the current process.
- Filtered deletion and retention first identify affected conversation IDs, then delete and refresh only those summaries within one Room transaction. This avoids an unrelated full-conversation scan while preserving summary consistency.

The current quality pass does not introduce a measured CPU, memory, I/O, or network improvement claim. The deterministic package-size benchmark is unchanged before and after the changes.
