package dev.d3v.notificationsaver

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private val notificationDao: NotificationDao,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger,
    private val operationalMetrics: OperationalMetricsStore,
) {
    private val appLabelCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var lastRetentionCleanupAt: Long = 0L

    fun observeNotifications(filter: NotificationFilter): Flow<List<NotificationEntity>> {
        return notificationDao.observeNotifications(
            searchText = filter.searchText.trim(),
            packageName = filter.packageName?.takeIf { it.isNotBlank() },
            category = filter.category?.takeIf { it.isNotBlank() },
            fromTimestamp = filter.dateWindow.fromTimestamp(),
        )
    }

    fun observeNotification(id: Long): Flow<NotificationEntity?> {
        return notificationDao.observeNotification(id)
    }

    fun observeThread(packageName: String, resolvedThreadId: String): Flow<List<NotificationEntity>> {
        return notificationDao.observeThread(packageName, resolvedThreadId)
    }

    fun observeThreadSummaries(): Flow<List<ThreadSummary>> {
        val recentCutoff = System.currentTimeMillis() - THREAD_ACTIVITY_WINDOW_MILLIS
        return notificationDao.observeThreadSummaries(recentCutoff).map { rows ->
            rows.map { row ->
                ThreadSummary(
                    packageName = row.packageName,
                    appLabel = row.appLabel,
                    resolvedThreadId = row.resolvedThreadId,
                    displayTitle = row.displayTitle,
                    latestPreview = row.latestPreview,
                    latestTimestamp = row.latestTimestamp,
                    totalCount = row.totalCount,
                    recentCount = row.recentCount,
                    maxImportance = row.maxImportance,
                    isPinned = false,
                )
            }
        }
    }

    fun observeAppSources(): Flow<List<AppSource>> {
        return notificationDao.observeAppSources()
    }

    fun observeCategories(): Flow<List<String>> {
        return notificationDao.observeCategories()
    }

    suspend fun recordPosted(sbn: StatusBarNotification) = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = parseNotification(sbn, isRemoved = false)
            val existing = notificationDao.getBySourceKey(parsed.sourceKey)
            if (existing == null) {
                notificationDao.insert(parsed)
            } else {
                val merged = parsed.mergeWith(existing)
                if (!merged.hasSameStoredContentAs(existing)) {
                    notificationDao.update(merged)
                }
            }
            maybeRunRetentionCleanup(parsed.lastSeenAt)
            operationalMetrics.recordPostedEventStored()
        }.onFailure {
            operationalMetrics.recordStoreFailure()
            logger.error("NotificationRepository", "Failed to store posted notification", it)
        }
    }

    suspend fun recordRemoved(sbn: StatusBarNotification) = withContext(Dispatchers.IO) {
        runCatching {
            val existing = notificationDao.getBySourceKey(buildSourceKey(sbn)) ?: return@runCatching
            notificationDao.update(
                existing.copy(
                    isRemoved = true,
                    removedAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                ),
            )
            operationalMetrics.recordRemovedEventStored()
        }.onFailure {
            operationalMetrics.recordRemoveFailure()
            logger.error("NotificationRepository", "Failed to mark notification as removed", it)
        }
    }

    suspend fun saveManualMetadata(
        id: Long,
        category: String,
        tags: List<String>,
        manualThreadLabel: String?,
    ) = withContext(Dispatchers.IO) {
        val existing = notificationDao.getById(id) ?: return@withContext
        notificationDao.update(
            existing.copy(
                category = category.trim().ifEmpty { DEFAULT_FALLBACK_CATEGORY },
                tags = tags.filter { it.isNotBlank() }.map { it.trim() }.distinct(),
                manualThreadLabel = manualThreadLabel.trimmedOrNull(),
            ),
        )
        logger.info("NotificationRepository", "Updated metadata for notification $id")
    }

    suspend fun renameThread(
        packageName: String,
        resolvedThreadId: String,
        manualThreadLabel: String?,
    ) = withContext(Dispatchers.IO) {
        notificationDao.updateManualThreadLabelByResolvedThread(
            packageName = packageName,
            resolvedThreadId = resolvedThreadId,
            manualThreadLabel = manualThreadLabel.trimmedOrNull(),
        )
        logger.info("NotificationRepository", "Updated manual thread label for $packageName/$resolvedThreadId")
    }

    suspend fun deleteNotification(id: Long) = withContext(Dispatchers.IO) {
        notificationDao.deleteById(id)
        logger.info("NotificationRepository", "Deleted notification $id")
    }

    suspend fun deleteFiltered(packageName: String?, dateWindow: DateWindow) = withContext(Dispatchers.IO) {
        notificationDao.deleteFiltered(
            packageName = packageName?.takeIf { it.isNotBlank() },
            fromTimestamp = dateWindow.fromTimestamp(),
        )
        logger.info("NotificationRepository", "Deleted filtered notifications")
    }

    suspend fun clearAllUserData() = withContext(Dispatchers.IO) {
        logger.info("NotificationRepository", "Clearing user-managed app data")
        notificationDao.clearAll()
        clearDirectoryContents(context.cacheDir)
        clearDatabaseSidecars()
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

    suspend fun exportDiagnosticsBundle(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            val notifications = notificationDao.getAllForExport()
            val payload = JSONObject()
                .put("exportedAt", Instant.now().toString())
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("buildType", BuildConfig.BUILD_TYPE)
                .put("packageName", context.packageName)
                .put(
                    "device",
                    JSONObject()
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("model", Build.MODEL)
                        .put("sdkInt", Build.VERSION.SDK_INT)
                        .put("release", Build.VERSION.RELEASE),
                )
                .put(
                    "settings",
                    JSONObject()
                        .put("retentionDays", settingsStore.current().retentionDays)
                        .put("hideNotificationPreviews", settingsStore.current().hideNotificationPreviews)
                        .put("fallbackCategory", settingsStore.current().fallbackCategory)
                        .put("themeMode", settingsStore.current().themeMode.name)
                        .put("appCategoryOverrides", JSONObject(settingsStore.current().appCategoryOverrides))
                        .put("pinnedThreadIds", JSONArray(settingsStore.current().pinnedThreadIds.sorted())),
                )
                .put("savedNotificationCount", notifications.size)
                .put("storageBuckets", JSONArray(computeStorageBuckets().map { it.toJson() }))
                .put("operationalMetrics", operationalMetrics.current().toJson())

            context.contentResolver.openOutputStream(uri, "w")?.use { rawStream ->
                ZipOutputStream(rawStream.buffered()).use { zipStream ->
                    writeTextEntry(zipStream, "summary.json", payload.toString(2))
                    copyDirectoryFilesIntoZip(
                        zipStream = zipStream,
                        directory = logger.logDirectory(),
                        prefix = "logs/",
                    )
                    copyDirectoryFilesIntoZip(
                        zipStream = zipStream,
                        directory = operationalMetrics.crashReportDirectory(),
                        prefix = "crash-reports/",
                    )
                }
            } ?: error("Could not open diagnostics destination")
            logger.info("NotificationRepository", "Exported diagnostics bundle")
        }.onFailure {
            operationalMetrics.recordExportFailure()
            logger.error("NotificationRepository", "Failed to export diagnostics bundle", it)
        }.getOrThrow()
    }

    suspend fun exportNotifications(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            val notifications = notificationDao.getAllForExport()
            val payload = JSONObject()
                .put("exportedAt", Instant.now().toString())
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put(
                    "settings",
                    JSONObject()
                        .put("retentionDays", settingsStore.current().retentionDays)
                        .put("hideNotificationPreviews", settingsStore.current().hideNotificationPreviews)
                        .put("fallbackCategory", settingsStore.current().fallbackCategory)
                        .put("themeMode", settingsStore.current().themeMode.name)
                        .put("pinnedThreadIds", JSONArray(settingsStore.current().pinnedThreadIds.sorted())),
                )
                .put(
                    "notifications",
                    JSONArray().apply {
                        notifications.forEach { record ->
                            put(record.toJson())
                        }
                    },
                )
            context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
                writer.write(payload.toString(2))
            } ?: error("Could not open export destination")
            logger.info("NotificationRepository", "Exported ${notifications.size} notifications")
        }.onFailure {
            operationalMetrics.recordExportFailure()
            logger.error("NotificationRepository", "Failed to export notifications", it)
        }.getOrThrow()
    }

    suspend fun computeStorageBuckets(): List<StorageBucket> = withContext(Dispatchers.IO) {
        val databaseFile = context.getDatabasePath(NotificationDatabase.FILE_NAME)
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
                path = sharedPrefsDir.absolutePath,
                sizeBytes = sizeOf(sharedPrefsDir),
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

    suspend fun runRetentionCleanupNow() = withContext(Dispatchers.IO) {
        applyRetentionPolicy()
    }

    suspend fun replaceNotificationsForDemo(records: List<NotificationEntity>) = withContext(Dispatchers.IO) {
        notificationDao.clearAll()
        records.forEach { record ->
            notificationDao.insert(record)
        }
        lastRetentionCleanupAt = 0L
    }

    private suspend fun maybeRunRetentionCleanup(now: Long) {
        if (now - lastRetentionCleanupAt < RETENTION_CLEANUP_INTERVAL_MILLIS) {
            return
        }
        lastRetentionCleanupAt = now
        applyRetentionPolicy()
    }

    private suspend fun applyRetentionPolicy() {
        val retentionDays = settingsStore.current().retentionDays
        if (retentionDays <= 0) {
            return
        }
        val cutoff = System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY
        notificationDao.deleteOlderThan(cutoff)
    }

    private fun parseNotification(sbn: StatusBarNotification, isRemoved: Boolean): NotificationEntity {
        val notification = sbn.notification
        val extras = notification.extras
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            .trimmedOrNull()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .trimmedOrNull()
        val bodyText = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            .trimmedOrNull()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            .trimmedOrNull()
        val messages = parseMessages(extras.getParcelableArray(Notification.EXTRA_MESSAGES))
        val lastMessage = messages.lastOrNull()
        val senderName = lastMessage?.first.trimmedOrNull()
        val messageBody = lastMessage?.second.trimmedOrNull()
        val normalizedBody = messageBody ?: bodyText ?: bigText
        val threadKey = conversationTitle ?: title ?: sbn.groupKey.trimmedOrNull()
        val appLabel = resolveAppLabel(sbn.packageName)
        val category = inferCategory(
            packageName = sbn.packageName,
            notificationCategory = notification.category,
            title = title,
            body = normalizedBody,
        )
        val now = System.currentTimeMillis()

        return NotificationEntity(
            sourceKey = buildSourceKey(sbn),
            packageName = sbn.packageName,
            appLabel = appLabel,
            notificationId = sbn.id,
            notificationTag = sbn.tag,
            postedAt = sbn.postTime,
            lastSeenAt = now,
            removedAt = if (isRemoved) now else null,
            title = title,
            body = normalizedBody,
            bigText = bigText,
            category = category,
            tags = emptyList(),
            manualThreadLabel = null,
            threadKey = threadKey,
            senderName = senderName,
            conversationTitle = conversationTitle,
            importance = notification.priority,
            extrasJson = buildExtrasJson(
                title = title,
                text = bodyText,
                bigText = bigText,
                conversationTitle = conversationTitle,
                senderName = senderName,
                messageCount = messages.size,
            ),
            isRemoved = isRemoved,
        )
    }

    private fun buildSourceKey(sbn: StatusBarNotification): String {
        return sbn.key ?: "${sbn.packageName}:${sbn.id}:${sbn.tag.orEmpty()}"
    }

    private fun resolveAppLabel(packageName: String): String {
        appLabelCache[packageName]?.let { return it }
        val resolved = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        appLabelCache[packageName] = resolved
        return resolved
    }

    private fun inferCategory(
        packageName: String,
        notificationCategory: String?,
        title: String?,
        body: String?,
    ): String {
        val settings = settingsStore.current()
        settings.appCategoryOverrides[packageName]?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        val lowerPackage = packageName.lowercase()
        val lowerText = buildString {
            append(title.orEmpty())
            append(' ')
            append(body.orEmpty())
        }.lowercase()

        return when {
            notificationCategory == Notification.CATEGORY_CALL ||
                "dialer" in lowerPackage ||
                "phone" in lowerPackage -> "Calls"
            notificationCategory == Notification.CATEGORY_EMAIL ||
                "gmail" in lowerPackage ||
                "outlook" in lowerPackage ||
                "mail" in lowerPackage -> "Email"
            notificationCategory == Notification.CATEGORY_MESSAGE ||
                "whatsapp" in lowerPackage ||
                "telegram" in lowerPackage ||
                "signal" in lowerPackage ||
                "messag" in lowerPackage -> "Messages"
            "instagram" in lowerPackage ||
                "facebook" in lowerPackage ||
                "x." in lowerPackage ||
                "twitter" in lowerPackage ||
                "social" in lowerText -> "Social"
            "bank" in lowerText ||
                "upi" in lowerText ||
                "payment" in lowerText ||
                "wallet" in lowerText -> "Finance"
            notificationCategory == Notification.CATEGORY_SYSTEM ||
                "android" in lowerPackage -> "System"
            "order" in lowerText ||
                "delivery" in lowerText ||
                "shopping" in lowerText -> "Shopping"
            "travel" in lowerText ||
                "flight" in lowerText ||
                "cab" in lowerText -> "Travel"
            else -> settings.fallbackCategory.ifBlank { DEFAULT_FALLBACK_CATEGORY }
        }
    }

    private fun parseMessages(parcelables: Array<Parcelable>?): List<Pair<String?, String?>> {
        if (parcelables == null) {
            return emptyList()
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcelables)
                    .map { message ->
                        val sender = message.senderPerson?.name?.toString()
                            ?: message.sender?.toString()
                        sender to message.text?.toString()
                    }
            } else {
                parcelables.mapNotNull { (it as? Bundle)?.toMessagePair() }
            }
        }.getOrDefault(emptyList())
    }

    private fun Bundle.toMessagePair(): Pair<String?, String?>? {
        val text = getCharSequence("text")?.toString()
            ?: getCharSequence("mText")?.toString()
        val personName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            (
                (getParcelable("sender_person") as? android.app.Person)
                    ?: (getParcelable("person") as? android.app.Person)
                )
                ?.name
                ?.toString()
        } else {
            null
        }
        val sender = personName
            ?: getCharSequence("sender")?.toString()
            ?: getCharSequence("mSender")?.toString()
        return if (sender == null && text == null) null else sender to text
    }

    private fun buildExtrasJson(
        title: String?,
        text: String?,
        bigText: String?,
        conversationTitle: String?,
        senderName: String?,
        messageCount: Int,
    ): String {
        return JSONObject()
            .put("title", title)
            .put("text", text)
            .put("bigText", bigText)
            .put("conversationTitle", conversationTitle)
            .put("senderName", senderName)
            .put("messageCount", messageCount)
            .toString()
    }

    private fun clearDirectoryContents(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) {
            return
        }
        directory.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
    }

    private fun clearDatabaseSidecars() {
        val databaseFile = context.getDatabasePath(NotificationDatabase.FILE_NAME)
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
    }

    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) {
            return 0L
        }
        if (file.isFile) {
            return file.length()
        }
        return file.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun NotificationEntity.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("sourceKey", sourceKey)
            .put("packageName", packageName)
            .put("appLabel", appLabel)
            .put("notificationId", notificationId)
            .put("notificationTag", notificationTag)
            .put("postedAt", postedAt)
            .put("lastSeenAt", lastSeenAt)
            .put("removedAt", removedAt)
            .put("title", title)
            .put("body", body)
            .put("bigText", bigText)
            .put("category", category)
            .put("tags", JSONArray(tags))
            .put("manualThreadLabel", manualThreadLabel)
            .put("threadKey", threadKey)
            .put("senderName", senderName)
            .put("conversationTitle", conversationTitle)
            .put("importance", importance)
            .put("extrasJson", extrasJson)
            .put("isRemoved", isRemoved)
    }

    private fun StorageBucket.toJson(): JSONObject {
        return JSONObject()
            .put("label", label)
            .put("path", path)
            .put("sizeBytes", sizeBytes)
    }

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
            .put("lastAppStartAt", lastAppStartAt)
            .put("lastListenerConnectedAt", lastListenerConnectedAt)
            .put("lastListenerDisconnectedAt", lastListenerDisconnectedAt)
            .put("lastQueueDropAt", lastQueueDropAt)
            .put("lastStoreFailureAt", lastStoreFailureAt)
            .put("lastRemoveFailureAt", lastRemoveFailureAt)
            .put("lastExportFailureAt", lastExportFailureAt)
            .put("latestCrash", latestCrash?.toJson())
    }

    private fun CrashReportSummary.toJson(): JSONObject {
        return JSONObject()
            .put("occurredAt", occurredAt)
            .put("threadName", threadName)
            .put("exceptionType", exceptionType)
            .put("message", message)
            .put("reportPath", reportPath)
    }

    private fun writeTextEntry(zipStream: ZipOutputStream, entryName: String, content: String) {
        zipStream.putNextEntry(ZipEntry(entryName))
        zipStream.write(content.toByteArray(Charsets.UTF_8))
        zipStream.closeEntry()
    }

    private fun copyDirectoryFilesIntoZip(
        zipStream: ZipOutputStream,
        directory: File?,
        prefix: String,
    ) {
        val files = directory?.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?: return
        files.forEach { file ->
            zipStream.putNextEntry(ZipEntry(prefix + file.name))
            file.inputStream().use { input ->
                input.copyTo(zipStream)
            }
            zipStream.closeEntry()
        }
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val RETENTION_CLEANUP_INTERVAL_MILLIS = 12L * 60L * 60L * 1000L
        private const val THREAD_ACTIVITY_WINDOW_MILLIS = 7L * MILLIS_PER_DAY
        val exportFileFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}

private fun NotificationEntity.mergeWith(existing: NotificationEntity): NotificationEntity {
    return copy(
        id = existing.id,
        postedAt = existing.postedAt,
        tags = existing.tags,
        category = existing.category,
        manualThreadLabel = existing.manualThreadLabel,
    )
}

private fun NotificationEntity.hasSameStoredContentAs(existing: NotificationEntity): Boolean {
    return sourceKey == existing.sourceKey &&
        packageName == existing.packageName &&
        appLabel == existing.appLabel &&
        notificationId == existing.notificationId &&
        notificationTag == existing.notificationTag &&
        removedAt == existing.removedAt &&
        title == existing.title &&
        body == existing.body &&
        bigText == existing.bigText &&
        category == existing.category &&
        tags == existing.tags &&
        manualThreadLabel == existing.manualThreadLabel &&
        threadKey == existing.threadKey &&
        senderName == existing.senderName &&
        conversationTitle == existing.conversationTitle &&
        importance == existing.importance &&
        extrasJson == existing.extrasJson &&
        isRemoved == existing.isRemoved
}
