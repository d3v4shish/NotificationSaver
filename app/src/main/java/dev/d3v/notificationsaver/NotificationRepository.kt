package dev.d3v.notificationsaver

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class NotificationFilter(
    val searchText: String = "",
    val packageName: String? = null,
    val category: String? = null,
    val dateWindow: DateWindow = DateWindow.AllTime,
)

data class StorageBucket(
    val label: String,
    val path: String,
    val sizeBytes: Long,
)

class NotificationRepository(
    private val context: Context,
    private val database: NotificationDatabase,
    private val notificationDao: NotificationDao,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger,
    private val operationalMetrics: OperationalMetricsStore,
    private val parser: NotificationParser,
) {
    @Volatile
    private var lastRetentionCleanupAt: Long = 0L

    fun pageRecords(filter: NotificationFilter): Flow<PagingData<NotificationRecordEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                notificationDao.pageRecords(
                    ftsQuery = filter.searchText.toFtsQuery(),
                    packageName = filter.packageName?.trimmedOrNull(),
                    category = filter.category?.trimmedOrNull(),
                    fromTimestamp = filter.dateWindow.fromTimestamp(),
                )
            },
        ).flow
    }

    fun pageConversations(searchText: String = "", packageName: String? = null): Flow<PagingData<ConversationEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                notificationDao.pageConversations(searchText.trim(), packageName?.trimmedOrNull())
            },
        ).flow
    }

    fun pageConversationEntries(conversationId: Long): Flow<PagingData<ConversationEntryRow>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { notificationDao.pageConversationEntries(conversationId) },
        ).flow
    }

    fun observeRecord(id: Long): Flow<NotificationRecordEntity?> = notificationDao.observeRecord(id)

    fun observeRevisions(recordId: Long): Flow<List<NotificationRevisionEntity>> =
        notificationDao.observeRevisions(recordId)

    fun observeConversation(id: Long): Flow<ConversationEntity?> = notificationDao.observeConversation(id)

    fun observeAppSources(): Flow<List<AppSource>> = notificationDao.observeAppSources()

    fun observeCategories(): Flow<List<String>> = notificationDao.observeCategories()

    fun observeHomeSummary(dayStart: Long): Flow<HomeSummary> =
        notificationDao.observeHomeSummary(dayStart)

    suspend fun recordCaptureEvents(events: List<CaptureEvent>) = withContext(Dispatchers.IO) {
        val prepared = events.mapNotNull(::prepareEvent)
        if (prepared.isEmpty()) return@withContext
        val startedAt = System.nanoTime()
        database.withTransaction {
            prepared.forEach { event ->
                when (event) {
                    is PreparedEvent.Posted -> applyPosted(event.parsed, event.recovered)
                    is PreparedEvent.Removed -> applyRemoved(event.parsed, event.reason)
                    is PreparedEvent.Reconcile -> applyReconcile(event.notifications, event.capturedAt)
                }
            }
        }
        operationalMetrics.recordBatchPersisted(
            eventCount = prepared.size,
            latencyMillis = (System.nanoTime() - startedAt) / 1_000_000L,
        )
        maybeRunRetentionCleanup(System.currentTimeMillis())
    }

    suspend fun updateRecordMetadata(id: Long, category: String, tags: List<String>) = withContext(Dispatchers.IO) {
        notificationDao.updateRecordMetadata(
            recordId = id,
            category = category.trim().ifEmpty { DEFAULT_FALLBACK_CATEGORY },
            tags = tags.mapNotNull(String::trimmedOrNull).distinct(),
        )
    }

    suspend fun renameConversation(id: Long, manualTitle: String?) = withContext(Dispatchers.IO) {
        notificationDao.renameConversation(id, manualTitle.trimmedOrNull())
    }

    suspend fun setConversationPinned(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        notificationDao.setConversationPinned(id, pinned)
    }

    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val conversationId = notificationDao.getRecord(id)?.conversationId
            notificationDao.deleteRecord(id)
            conversationId?.let { refreshConversation(it) }
        }
        logger.info("NotificationRepository", "Deleted notification record $id")
    }

    suspend fun deleteFiltered(packageName: String?, dateWindow: DateWindow) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val normalizedPackageName = packageName?.trimmedOrNull()
            val fromTimestamp = dateWindow.fromTimestamp()
            val affectedConversationIds = notificationDao.conversationIdsForFilteredRecords(
                packageName = normalizedPackageName,
                fromTimestamp = fromTimestamp,
            )
            val deleted = notificationDao.deleteFiltered(
                packageName = normalizedPackageName,
                fromTimestamp = fromTimestamp,
            )
            if (deleted > 0) {
                for (conversationId in affectedConversationIds) {
                    refreshConversation(conversationId)
                }
            }
        }
        logger.info("NotificationRepository", "Deleted filtered notification records")
    }

    suspend fun clearAllUserData() = withContext(Dispatchers.IO) {
        database.withTransaction {
            notificationDao.clearRecords()
            notificationDao.clearConversations()
            notificationDao.clearFts()
        }
        clearDirectoryContents(context.cacheDir)
        settingsStore.reset(logAction = false)
        clearDirectoryContents(logger.logDirectory())
        operationalMetrics.clearAll(logAction = false)
        lastRetentionCleanupAt = 0L
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        clearDirectoryContents(logger.logDirectory())
    }

    suspend fun clearCrashReports() = withContext(Dispatchers.IO) {
        operationalMetrics.clearCrashReports(logAction = false)
    }

    suspend fun computeStorageBuckets(): List<StorageBucket> = withContext(Dispatchers.IO) {
        val databaseFile = context.getDatabasePath(NotificationDatabase.FILE_NAME)
        val dataStoreDir = File(context.filesDir, "datastore")
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val logDir = logger.logDirectory()
        val crashReportDir = operationalMetrics.crashReportDirectory()
        listOf(
            StorageBucket(
                label = "Database",
                path = databaseFile.absolutePath,
                sizeBytes = sizeOf(databaseFile) +
                    sizeOf(File(databaseFile.absolutePath + "-shm")) +
                    sizeOf(File(databaseFile.absolutePath + "-wal")),
            ),
            StorageBucket(
                label = "Settings",
                path = dataStoreDir.absolutePath,
                sizeBytes = sizeOf(dataStoreDir) + sizeOf(sharedPrefsDir),
            ),
            StorageBucket(
                label = "Logs",
                path = logDir?.absolutePath ?: "(not created yet)",
                sizeBytes = sizeOf(logDir),
            ),
            StorageBucket(
                label = "Crash reports",
                path = crashReportDir.absolutePath,
                sizeBytes = sizeOf(crashReportDir),
            ),
            StorageBucket(
                label = "Cache",
                path = context.cacheDir.absolutePath,
                sizeBytes = sizeOf(context.cacheDir),
            ),
        )
    }

    suspend fun exportDiagnosticsBundle(uri: Uri) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("exportedAt", Instant.now().toString())
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("buildType", BuildConfig.BUILD_TYPE)
            .put("packageName", context.packageName)
            .put("savedNotificationCount", notificationDao.recordCount())
            .put("savedMessageCount", notificationDao.messageCount())
            .put("operationalMetrics", operationalMetrics.current().toJson())
        context.contentResolver.openOutputStream(uri, "w")?.use { rawStream ->
            ZipOutputStream(rawStream.buffered()).use { zipStream ->
                writeTextEntry(zipStream, "summary.json", payload.toString(2))
                copyDirectoryFilesIntoZip(zipStream, logger.logDirectory(), "logs/")
                copyDirectoryFilesIntoZip(zipStream, operationalMetrics.crashReportDirectory(), "crash-reports/")
            }
        } ?: error("Could not open diagnostics destination")
    }

    suspend fun runRetentionCleanupNow() = withContext(Dispatchers.IO) {
        applyRetentionPolicy()
    }

    suspend fun migrateLegacyPins() = withContext(Dispatchers.IO) {
        val legacyPins = settingsStore.current().legacyPinnedThreadIds
        if (legacyPins.isEmpty()) return@withContext
        database.withTransaction {
            legacyPins.forEach { stableId ->
                val separator = stableId.indexOf('\u001f')
                if (separator <= 0 || separator >= stableId.lastIndex) return@forEach
                val packageName = stableId.substring(0, separator)
                val resolvedId = stableId.substring(separator + 1)
                notificationDao.getConversation(packageName, "legacy:$resolvedId")?.let { conversation ->
                    notificationDao.setConversationPinned(conversation.id, true)
                }
            }
        }
        settingsStore.clearLegacyPinnedThreadIds()
    }

    suspend fun replaceAllForImport(
        conversations: List<ConversationEntity>,
        records: List<NotificationRecordEntity>,
        revisions: List<NotificationRevisionEntity>,
        messages: List<ChatMessageEntity>,
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            notificationDao.clearRecords()
            notificationDao.clearConversations()
            notificationDao.clearFts()
            importRows(conversations, records, revisions, messages)
        }
    }

    suspend fun mergeImportedRows(
        conversations: List<ConversationEntity>,
        records: List<NotificationRecordEntity>,
        revisions: List<NotificationRevisionEntity>,
        messages: List<ChatMessageEntity>,
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            importRows(conversations, records, revisions, messages)
        }
    }

    suspend fun backupSnapshot(): BackupSnapshot = withContext(Dispatchers.IO) {
        BackupSnapshot(
            conversations = notificationDao.getAllConversationsForExport(),
            records = notificationDao.getAllRecordsForExport(),
            revisions = notificationDao.getAllRevisionsForExport(),
            messages = notificationDao.getAllMessagesForExport(),
            settings = settingsStore.current(),
        )
    }

    private fun prepareEvent(event: CaptureEvent): PreparedEvent? {
        return runCatching {
            when (event) {
                is CaptureEvent.Posted -> PreparedEvent.Posted(
                    parsed = parser.parse(event.notification, event.capturedAt, event.rankingImportance),
                    recovered = event.recovered,
                )
                is CaptureEvent.Removed -> PreparedEvent.Removed(
                    parsed = parser.parse(event.notification, event.capturedAt, event.rankingImportance),
                    reason = event.reason,
                )
                is CaptureEvent.Reconcile -> PreparedEvent.Reconcile(
                    notifications = event.activeNotifications.mapNotNull { posted ->
                        runCatching {
                            parser.parse(posted.notification, posted.capturedAt, posted.rankingImportance)
                        }.onFailure { error ->
                            operationalMetrics.recordParseFailure()
                            logger.error("NotificationRepository", "Failed to parse a reconciled notification", error)
                        }.getOrNull()
                    },
                    capturedAt = event.capturedAt,
                )
            }
        }.onFailure { error ->
            operationalMetrics.recordParseFailure()
            logger.error("NotificationRepository", "Failed to parse a notification", error)
        }.getOrNull()
    }

    private suspend fun applyPosted(parsed: ParsedNotification, recovered: Boolean) {
        if (parsed.packageName == context.packageName || parsed.packageName in settingsStore.current().excludedPackages) {
            operationalMetrics.recordExcludedEvent()
            return
        }
        val conversation = parsed.conversation?.let { ensureConversation(parsed, it) }
        val active = notificationDao.getActive(parsed.systemKey)
        if (active == null) {
            val recordId = notificationDao.insertRecord(parsed.toRecord(conversation?.id, recovered))
            val revisionId = notificationDao.insertRevision(parsed.toRevision(recordId, recovered))
            val insertedMessages = if (!parsed.isGroupSummary && conversation != null) {
                insertMessages(parsed, conversation, recordId, revisionId.takeIf { it > 0L })
            } else {
                0
            }
            notificationDao.upsertActive(
                ActiveNotificationEntity(
                    systemKey = parsed.systemKey,
                    recordId = recordId,
                    lastContentFingerprint = parsed.contentFingerprint,
                    lastSeenAt = parsed.capturedAt,
                ),
            )
            conversation?.let { refreshConversation(it.id) }
            operationalMetrics.recordPostedEventStored()
            if (insertedMessages > 0) operationalMetrics.recordMessagesStored(insertedMessages)
            return
        }

        val existing = notificationDao.getRecord(active.recordId)
        if (existing == null) {
            notificationDao.deleteActive(parsed.systemKey)
            applyPosted(parsed, recovered)
            return
        }
        val isNewRevision = active.lastContentFingerprint != parsed.contentFingerprint
        val oldConversationId = existing.conversationId
        val updated = existing.copy(
            packageName = parsed.packageName,
            appLabel = parsed.appLabel,
            notificationId = parsed.notificationId,
            notificationTag = parsed.notificationTag,
            userId = parsed.userId,
            channelId = parsed.channelId,
            groupKey = parsed.groupKey,
            shortcutId = parsed.shortcutId,
            locusId = parsed.locusId,
            lastUpdatedAt = parsed.capturedAt,
            endedAt = null,
            endReason = null,
            latestTitle = parsed.title,
            latestBody = parsed.body,
            latestBigText = parsed.bigText,
            latestSubText = parsed.subText,
            senderName = parsed.senderName,
            conversationTitle = parsed.conversationTitle,
            importance = parsed.importance,
            visibility = parsed.visibility,
            notificationFlags = parsed.notificationFlags,
            revisionCount = existing.revisionCount + if (isNewRevision) 1 else 0,
            conversationId = conversation?.id,
            isActive = true,
            isGroupSummary = parsed.isGroupSummary,
            wasRecovered = existing.wasRecovered || recovered,
        )
        notificationDao.updateRecord(updated)
        val revisionId = if (isNewRevision) {
            notificationDao.insertRevision(parsed.toRevision(existing.id, recovered)).takeIf { it > 0L }
        } else {
            null
        }
        val insertedMessages = if (!parsed.isGroupSummary && conversation != null) {
            insertMessages(parsed, conversation, existing.id, revisionId)
        } else {
            0
        }
        notificationDao.upsertActive(
            active.copy(
                lastContentFingerprint = parsed.contentFingerprint,
                lastSeenAt = parsed.capturedAt,
            ),
        )
        oldConversationId?.takeIf { it != conversation?.id }?.let { refreshConversation(it) }
        conversation?.let { refreshConversation(it.id) }
        if (isNewRevision) operationalMetrics.recordPostedEventStored() else operationalMetrics.recordDuplicateEvent()
        if (insertedMessages > 0) operationalMetrics.recordMessagesStored(insertedMessages)
    }

    private suspend fun applyRemoved(parsed: ParsedNotification, reason: Int) {
        val active = notificationDao.getActive(parsed.systemKey)
        if (active == null) {
            applyPosted(parsed, recovered = true)
        }
        val resolvedActive = notificationDao.getActive(parsed.systemKey) ?: return
        notificationDao.endRecord(resolvedActive.recordId, parsed.capturedAt, reason)
        notificationDao.deleteActive(parsed.systemKey)
        operationalMetrics.recordRemovedEventStored()
    }

    private suspend fun applyReconcile(notifications: List<ParsedNotification>, capturedAt: Long) {
        val settings = settingsStore.current()
        val eligible = notifications.filterNot {
            it.packageName == context.packageName || it.packageName in settings.excludedPackages
        }
        val activeKeys = eligible.mapTo(HashSet()) { it.systemKey }
        eligible.forEach { applyPosted(it, recovered = true) }
        val staleKeys = notificationDao.getActiveKeys().filterNot(activeKeys::contains)
        staleKeys.forEach { key ->
            notificationDao.getActive(key)?.let { active ->
                notificationDao.endRecord(active.recordId, capturedAt, REASON_RECONCILED_MISSING)
                notificationDao.deleteActive(key)
            }
        }
        operationalMetrics.recordReconciliation(notifications.size, staleKeys.size)
    }

    private suspend fun ensureConversation(
        parsed: ParsedNotification,
        match: ConversationMatch,
    ): ConversationEntity {
        val existing = notificationDao.getConversation(parsed.packageName, match.canonicalKey)
        if (existing != null) {
            val shouldRefresh = existing.appLabel != parsed.appLabel ||
                (existing.manualTitle == null && match.displayTitle != null &&
                    (existing.sourceTitle == null || match.confidence > existing.identityConfidence))
            if (!shouldRefresh) return existing
            val updated = existing.copy(
                appLabel = parsed.appLabel,
                identitySource = if (match.confidence >= existing.identityConfidence) match.identitySource else existing.identitySource,
                identityConfidence = maxOf(existing.identityConfidence, match.confidence),
                sourceTitle = if (match.confidence >= existing.identityConfidence) {
                    match.displayTitle ?: existing.sourceTitle
                } else {
                    existing.sourceTitle
                },
            )
            notificationDao.updateConversation(updated)
            return updated
        }
        val candidate = ConversationEntity(
            packageName = parsed.packageName,
            appLabel = parsed.appLabel,
            canonicalKey = match.canonicalKey,
            identitySource = match.identitySource,
            identityConfidence = match.confidence,
            sourceTitle = match.displayTitle,
            manualTitle = null,
            latestPreview = parsed.body ?: parsed.bigText ?: parsed.title,
            latestActivityAt = parsed.capturedAt,
            recordCount = 0,
            messageCount = 0,
            isPinned = false,
        )
        val id = notificationDao.insertConversation(candidate)
        return if (id > 0L) {
            candidate.copy(id = id)
        } else {
            requireNotNull(notificationDao.getConversation(parsed.packageName, match.canonicalKey))
        }
    }

    private suspend fun insertMessages(
        parsed: ParsedNotification,
        conversation: ConversationEntity,
        recordId: Long,
        revisionId: Long?,
    ): Int {
        var inserted = 0
        parsed.messages.forEach { message ->
            val rowId = notificationDao.insertMessage(
                ChatMessageEntity(
                    portableId = "message:${conversation.portableId}:${message.fingerprint}",
                    conversationId = conversation.id,
                    recordId = recordId,
                    revisionId = revisionId,
                    messageFingerprint = message.fingerprint,
                    senderKey = message.senderKey,
                    senderName = message.senderName,
                    text = message.text,
                    sourceTimestamp = message.timestamp,
                    capturedAt = parsed.capturedAt,
                    dataMimeType = message.dataMimeType,
                    dataUri = message.dataUri,
                    isHistoric = message.isHistoric,
                ),
            )
            if (rowId > 0L) inserted += 1
        }
        return inserted
    }

    private suspend fun refreshConversation(conversationId: Long) {
        val conversation = notificationDao.getConversation(conversationId) ?: return
        val recordCount = notificationDao.conversationRecordCount(conversationId)
        if (recordCount == 0) {
            notificationDao.deleteConversation(conversationId)
            return
        }
        val messageCount = notificationDao.conversationMessageCount(conversationId)
        val latest = notificationDao.conversationLatest(conversationId)
        notificationDao.updateConversation(
            conversation.copy(
                latestPreview = latest?.preview,
                latestActivityAt = latest?.timestamp ?: conversation.latestActivityAt,
                recordCount = recordCount,
                messageCount = messageCount,
            ),
        )
    }

    private suspend fun maybeRunRetentionCleanup(now: Long) {
        if (now - lastRetentionCleanupAt < RETENTION_CLEANUP_INTERVAL_MILLIS) return
        lastRetentionCleanupAt = now
        applyRetentionPolicy()
    }

    private suspend fun applyRetentionPolicy() {
        val retentionDays = settingsStore.current().retentionDays
        if (retentionDays <= 0) return
        database.withTransaction {
            val cutoffTimestamp = System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY
            val affectedConversationIds = notificationDao.conversationIdsForOlderRecords(cutoffTimestamp)
            val deleted = notificationDao.deleteOlderThan(cutoffTimestamp)
            if (deleted > 0) {
                for (conversationId in affectedConversationIds) {
                    refreshConversation(conversationId)
                }
            }
        }
    }

    private suspend fun importRows(
        conversations: List<ConversationEntity>,
        records: List<NotificationRecordEntity>,
        revisions: List<NotificationRevisionEntity>,
        messages: List<ChatMessageEntity>,
    ) {
        val conversationIdMap = HashMap<Long, Long>()
        conversations.forEach { imported ->
            val existing = notificationDao.getConversationByPortableId(imported.portableId)
                ?: notificationDao.getConversation(imported.packageName, imported.canonicalKey)
            val localId = existing?.id ?: notificationDao.insertConversation(imported.copy(id = 0L))
            conversationIdMap[imported.id] = localId
        }
        val recordIdMap = HashMap<Long, Long>()
        records.forEach { imported ->
            val existing = notificationDao.getRecordByPortableId(imported.portableId)
            val localId = existing?.id ?: notificationDao.insertRecord(
                imported.copy(
                    id = 0L,
                    conversationId = imported.conversationId?.let(conversationIdMap::get),
                    isActive = false,
                ),
            )
            recordIdMap[imported.id] = localId
        }
        val revisionIdMap = HashMap<Long, Long>()
        revisions.forEach { imported ->
            val localRecordId = recordIdMap[imported.recordId] ?: return@forEach
            val localId = notificationDao.insertRevision(imported.copy(id = 0L, recordId = localRecordId))
            if (localId > 0L) revisionIdMap[imported.id] = localId
        }
        messages.forEach { imported ->
            val localConversationId = conversationIdMap[imported.conversationId] ?: return@forEach
            val localRecordId = recordIdMap[imported.recordId] ?: return@forEach
            notificationDao.insertMessage(
                imported.copy(
                    id = 0L,
                    conversationId = localConversationId,
                    recordId = localRecordId,
                    revisionId = imported.revisionId?.let(revisionIdMap::get),
                ),
            )
        }
        conversationIdMap.values.distinct().forEach { refreshConversation(it) }
    }

    private fun ParsedNotification.toRecord(conversationId: Long?, recovered: Boolean): NotificationRecordEntity {
        return NotificationRecordEntity(
            systemKey = systemKey,
            packageName = packageName,
            appLabel = appLabel,
            notificationId = notificationId,
            notificationTag = notificationTag,
            userId = userId,
            channelId = channelId,
            groupKey = groupKey,
            shortcutId = shortcutId,
            locusId = locusId,
            lifecycleStartedAt = sourcePostedAt.takeIf { it > 0L } ?: capturedAt,
            lastUpdatedAt = capturedAt,
            endedAt = null,
            endReason = null,
            latestTitle = title,
            latestBody = body,
            latestBigText = bigText,
            latestSubText = subText,
            senderName = senderName,
            conversationTitle = conversationTitle,
            category = category,
            tags = emptyList(),
            importance = importance,
            visibility = visibility,
            notificationFlags = notificationFlags,
            revisionCount = 1,
            conversationId = conversationId,
            isActive = true,
            isGroupSummary = isGroupSummary,
            wasRecovered = recovered,
        )
    }

    private fun ParsedNotification.toRevision(recordId: Long, recovered: Boolean): NotificationRevisionEntity {
        return NotificationRevisionEntity(
            recordId = recordId,
            contentFingerprint = contentFingerprint,
            capturedAt = capturedAt,
            sourcePostedAt = sourcePostedAt,
            title = title,
            body = body,
            bigText = bigText,
            subText = subText,
            textLines = textLines,
            senderName = senderName,
            conversationTitle = conversationTitle,
            category = category,
            importance = importance,
            extrasJson = extrasJson,
            isRecovered = recovered,
        )
    }

    private fun clearDirectoryContents(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) return
        directory.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.walkTopDown().filter(File::isFile).sumOf(File::length)
    }

    private fun writeTextEntry(zipStream: ZipOutputStream, entryName: String, content: String) {
        zipStream.putNextEntry(ZipEntry(entryName))
        zipStream.write(content.toByteArray(Charsets.UTF_8))
        zipStream.closeEntry()
    }

    private fun copyDirectoryFilesIntoZip(zipStream: ZipOutputStream, directory: File?, prefix: String) {
        directory?.listFiles()?.filter(File::isFile)?.sortedBy(File::getName)?.forEach { file ->
            zipStream.putNextEntry(ZipEntry(prefix + file.name))
            file.inputStream().use { it.copyTo(zipStream) }
            zipStream.closeEntry()
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val INITIAL_LOAD_SIZE = 60
        private const val PREFETCH_DISTANCE = 15
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val RETENTION_CLEANUP_INTERVAL_MILLIS = 12L * 60L * 60L * 1000L
        private const val REASON_RECONCILED_MISSING = -2

        val exportFileFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}

private sealed interface PreparedEvent {
    data class Posted(val parsed: ParsedNotification, val recovered: Boolean) : PreparedEvent
    data class Removed(val parsed: ParsedNotification, val reason: Int) : PreparedEvent
    data class Reconcile(
        val notifications: List<ParsedNotification>,
        val capturedAt: Long,
    ) : PreparedEvent
}

internal fun String.toFtsQuery(): String? {
    val terms = trim()
        .split(ftsWhitespace)
        .map { term -> term.replace(ftsUnsupportedCharacter, "") }
        .filter(String::isNotBlank)
    if (terms.isEmpty()) return null
    return terms.joinToString(" AND ") { term -> "\"$term\"*" }
}

private val ftsWhitespace = Regex("\\s+")
private val ftsUnsupportedCharacter = Regex("[^\\p{L}\\p{N}_-]")

private fun OperationalSnapshot.toJson(): JSONObject {
    return JSONObject()
        .put("appStartCount", appStartCount)
        .put("listenerConnectedCount", listenerConnectedCount)
        .put("listenerDisconnectedCount", listenerDisconnectedCount)
        .put("postedEventCount", postedEventCount)
        .put("removedEventCount", removedEventCount)
        .put("queueDropCount", queueDropCount)
        .put("storeFailureCount", storeFailureCount)
        .put("removeFailureCount", removeFailureCount)
        .put("exportFailureCount", exportFailureCount)
        .put("parseFailureCount", parseFailureCount)
        .put("duplicateEventCount", duplicateEventCount)
        .put("messageCount", messageCount)
        .put("reconciliationCount", reconciliationCount)
        .put("queueHighWaterMark", queueHighWaterMark)
        .put("lastBatchLatencyMillis", lastBatchLatencyMillis)
        .put("latestCrash", latestCrash?.exceptionType)
}
