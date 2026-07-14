package dev.d3v.notificationsaver

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CrashReportSummary(
    val occurredAt: Long,
    val threadName: String,
    val exceptionType: String,
    val message: String?,
    val reportPath: String,
)

data class OperationalSnapshot(
    val appStartCount: Int = 0,
    val listenerConnectedCount: Int = 0,
    val listenerDisconnectedCount: Int = 0,
    val postedEventCount: Int = 0,
    val removedEventCount: Int = 0,
    val queueDropCount: Int = 0,
    val storeFailureCount: Int = 0,
    val removeFailureCount: Int = 0,
    val exportFailureCount: Int = 0,
    val lastAppStartAt: Long? = null,
    val lastListenerConnectedAt: Long? = null,
    val lastListenerDisconnectedAt: Long? = null,
    val lastQueueDropAt: Long? = null,
    val lastStoreFailureAt: Long? = null,
    val lastRemoveFailureAt: Long? = null,
    val lastExportFailureAt: Long? = null,
    val latestCrash: CrashReportSummary? = null,
)

class OperationalMetricsStore(
    context: Context,
    private val logger: AppLogger,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("notification_saver_operational_metrics", Context.MODE_PRIVATE)
    private val crashReportDir = File(context.filesDir, "crash-reports")

    private val mutableState = MutableStateFlow(loadSnapshot())
    val state: StateFlow<OperationalSnapshot> = mutableState.asStateFlow()

    fun current(): OperationalSnapshot = mutableState.value

    fun crashReportDirectory(): File = crashReportDir

    fun crashReportFiles(): List<File> {
        return crashReportDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
    }

    fun recordAppStart() {
        update {
            copy(
                appStartCount = appStartCount + 1,
                lastAppStartAt = System.currentTimeMillis(),
            )
        }
    }

    fun recordListenerConnected() {
        update {
            copy(
                listenerConnectedCount = listenerConnectedCount + 1,
                lastListenerConnectedAt = System.currentTimeMillis(),
            )
        }
    }

    fun recordListenerDisconnected() {
        update {
            copy(
                listenerDisconnectedCount = listenerDisconnectedCount + 1,
                lastListenerDisconnectedAt = System.currentTimeMillis(),
            )
        }
    }

    fun recordPostedEventStored() {
        update {
            copy(postedEventCount = postedEventCount + 1)
        }
    }

    fun recordRemovedEventStored() {
        update {
            copy(removedEventCount = removedEventCount + 1)
        }
    }

    fun recordQueueDrop() {
        update {
            copy(
                queueDropCount = queueDropCount + 1,
                lastQueueDropAt = System.currentTimeMillis(),
            )
        }
    }

    fun recordStoreFailure() {
        update {
            copy(
                storeFailureCount = storeFailureCount + 1,
                lastStoreFailureAt = System.currentTimeMillis(),
            )
        }
    }

    fun recordRemoveFailure() {
        update {
            copy(
                removeFailureCount = removeFailureCount + 1,
                lastRemoveFailureAt = System.currentTimeMillis(),
            )
        }
    }

    fun recordExportFailure() {
        update {
            copy(
                exportFailureCount = exportFailureCount + 1,
                lastExportFailureAt = System.currentTimeMillis(),
            )
        }
    }

    fun recordCrash(thread: Thread, throwable: Throwable) {
        val summary = writeCrashReport(thread, throwable)
        preferences.edit()
            .putLong(KEY_LAST_CRASH_AT, summary.occurredAt)
            .putString(KEY_LAST_CRASH_THREAD, summary.threadName)
            .putString(KEY_LAST_CRASH_EXCEPTION, summary.exceptionType)
            .putString(KEY_LAST_CRASH_MESSAGE, summary.message)
            .putString(KEY_LAST_CRASH_PATH, summary.reportPath)
            .commit()
        mutableState.value = loadSnapshot()
    }

    fun clearAll(logAction: Boolean = true) {
        preferences.edit().clear().apply()
        clearDirectoryContents(crashReportDir)
        mutableState.value = OperationalSnapshot()
        if (logAction) {
            logger.info("OperationalMetricsStore", "Operational metrics and crash reports cleared")
        }
    }

    fun clearCrashReports(logAction: Boolean = true) {
        clearDirectoryContents(crashReportDir)
        preferences.edit()
            .remove(KEY_LAST_CRASH_AT)
            .remove(KEY_LAST_CRASH_THREAD)
            .remove(KEY_LAST_CRASH_EXCEPTION)
            .remove(KEY_LAST_CRASH_MESSAGE)
            .remove(KEY_LAST_CRASH_PATH)
            .apply()
        mutableState.value = loadSnapshot()
        if (logAction) {
            logger.info("OperationalMetricsStore", "Crash reports cleared")
        }
    }

    private fun update(transform: OperationalSnapshot.() -> OperationalSnapshot) {
        val updated = mutableState.value.transform()
        saveSnapshot(updated)
        mutableState.value = updated
    }

    private fun saveSnapshot(snapshot: OperationalSnapshot) {
        preferences.edit()
            .putInt(KEY_APP_START_COUNT, snapshot.appStartCount)
            .putInt(KEY_LISTENER_CONNECTED_COUNT, snapshot.listenerConnectedCount)
            .putInt(KEY_LISTENER_DISCONNECTED_COUNT, snapshot.listenerDisconnectedCount)
            .putInt(KEY_POSTED_EVENT_COUNT, snapshot.postedEventCount)
            .putInt(KEY_REMOVED_EVENT_COUNT, snapshot.removedEventCount)
            .putInt(KEY_QUEUE_DROP_COUNT, snapshot.queueDropCount)
            .putInt(KEY_STORE_FAILURE_COUNT, snapshot.storeFailureCount)
            .putInt(KEY_REMOVE_FAILURE_COUNT, snapshot.removeFailureCount)
            .putInt(KEY_EXPORT_FAILURE_COUNT, snapshot.exportFailureCount)
            .putLong(KEY_LAST_APP_START_AT, snapshot.lastAppStartAt ?: 0L)
            .putLong(KEY_LAST_LISTENER_CONNECTED_AT, snapshot.lastListenerConnectedAt ?: 0L)
            .putLong(KEY_LAST_LISTENER_DISCONNECTED_AT, snapshot.lastListenerDisconnectedAt ?: 0L)
            .putLong(KEY_LAST_QUEUE_DROP_AT, snapshot.lastQueueDropAt ?: 0L)
            .putLong(KEY_LAST_STORE_FAILURE_AT, snapshot.lastStoreFailureAt ?: 0L)
            .putLong(KEY_LAST_REMOVE_FAILURE_AT, snapshot.lastRemoveFailureAt ?: 0L)
            .putLong(KEY_LAST_EXPORT_FAILURE_AT, snapshot.lastExportFailureAt ?: 0L)
            .apply()
    }

    private fun loadSnapshot(): OperationalSnapshot {
        return OperationalSnapshot(
            appStartCount = preferences.getInt(KEY_APP_START_COUNT, 0),
            listenerConnectedCount = preferences.getInt(KEY_LISTENER_CONNECTED_COUNT, 0),
            listenerDisconnectedCount = preferences.getInt(KEY_LISTENER_DISCONNECTED_COUNT, 0),
            postedEventCount = preferences.getInt(KEY_POSTED_EVENT_COUNT, 0),
            removedEventCount = preferences.getInt(KEY_REMOVED_EVENT_COUNT, 0),
            queueDropCount = preferences.getInt(KEY_QUEUE_DROP_COUNT, 0),
            storeFailureCount = preferences.getInt(KEY_STORE_FAILURE_COUNT, 0),
            removeFailureCount = preferences.getInt(KEY_REMOVE_FAILURE_COUNT, 0),
            exportFailureCount = preferences.getInt(KEY_EXPORT_FAILURE_COUNT, 0),
            lastAppStartAt = preferences.getLongOrNull(KEY_LAST_APP_START_AT),
            lastListenerConnectedAt = preferences.getLongOrNull(KEY_LAST_LISTENER_CONNECTED_AT),
            lastListenerDisconnectedAt = preferences.getLongOrNull(KEY_LAST_LISTENER_DISCONNECTED_AT),
            lastQueueDropAt = preferences.getLongOrNull(KEY_LAST_QUEUE_DROP_AT),
            lastStoreFailureAt = preferences.getLongOrNull(KEY_LAST_STORE_FAILURE_AT),
            lastRemoveFailureAt = preferences.getLongOrNull(KEY_LAST_REMOVE_FAILURE_AT),
            lastExportFailureAt = preferences.getLongOrNull(KEY_LAST_EXPORT_FAILURE_AT),
            latestCrash = loadCrashSummary(),
        )
    }

    private fun loadCrashSummary(): CrashReportSummary? {
        val occurredAt = preferences.getLongOrNull(KEY_LAST_CRASH_AT) ?: return null
        return CrashReportSummary(
            occurredAt = occurredAt,
            threadName = preferences.getString(KEY_LAST_CRASH_THREAD, null).orEmpty(),
            exceptionType = preferences.getString(KEY_LAST_CRASH_EXCEPTION, null).orEmpty(),
            message = preferences.getString(KEY_LAST_CRASH_MESSAGE, null),
            reportPath = preferences.getString(KEY_LAST_CRASH_PATH, null).orEmpty(),
        )
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable): CrashReportSummary {
        val occurredAt = System.currentTimeMillis()
        val timestamp = crashFileFormatter.format(Instant.ofEpochMilli(occurredAt))
        val reportFile = File(crashReportDir, "crash-$timestamp.txt")
        val reportPath = runCatching {
            crashReportDir.mkdirs()
            reportFile.writeText(
                buildString {
                    append("occurredAt=")
                    append(Instant.ofEpochMilli(occurredAt))
                    append('\n')
                    append("thread=")
                    append(thread.name)
                    append('\n')
                    append("exception=")
                    append(throwable.javaClass.name)
                    append('\n')
                    append("message=")
                    append(throwable.message.orEmpty())
                    append('\n')
                    append('\n')
                    append(Log.getStackTraceString(throwable))
                    append('\n')
                },
            )
            trimCrashReports()
            reportFile.absolutePath
        }.getOrElse { error ->
            logger.warn("OperationalMetricsStore", "Failed to write crash report file", error)
            "(failed to write crash report: ${error.javaClass.simpleName})"
        }
        return CrashReportSummary(
            occurredAt = occurredAt,
            threadName = thread.name,
            exceptionType = throwable.javaClass.name,
            message = throwable.message,
            reportPath = reportPath,
        )
    }

    private fun clearDirectoryContents(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) {
            return
        }
        directory.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
    }

    private fun trimCrashReports() {
        val files = crashReportFiles().toMutableList()
        var totalBytes = files.sumOf { it.length() }
        while (files.size > MAX_CRASH_REPORT_FILES || totalBytes > MAX_CRASH_REPORT_BYTES) {
            val oldest = files.removeFirstOrNull() ?: break
            totalBytes -= oldest.length()
            oldest.delete()
        }
    }

    private fun SharedPreferences.getLongOrNull(key: String): Long? {
        val value = getLong(key, 0L)
        return value.takeIf { it > 0L }
    }

    companion object {
        private const val KEY_APP_START_COUNT = "app_start_count"
        private const val KEY_LISTENER_CONNECTED_COUNT = "listener_connected_count"
        private const val KEY_LISTENER_DISCONNECTED_COUNT = "listener_disconnected_count"
        private const val KEY_POSTED_EVENT_COUNT = "posted_event_count"
        private const val KEY_REMOVED_EVENT_COUNT = "removed_event_count"
        private const val KEY_QUEUE_DROP_COUNT = "queue_drop_count"
        private const val KEY_STORE_FAILURE_COUNT = "store_failure_count"
        private const val KEY_REMOVE_FAILURE_COUNT = "remove_failure_count"
        private const val KEY_EXPORT_FAILURE_COUNT = "export_failure_count"
        private const val KEY_LAST_APP_START_AT = "last_app_start_at"
        private const val KEY_LAST_LISTENER_CONNECTED_AT = "last_listener_connected_at"
        private const val KEY_LAST_LISTENER_DISCONNECTED_AT = "last_listener_disconnected_at"
        private const val KEY_LAST_QUEUE_DROP_AT = "last_queue_drop_at"
        private const val KEY_LAST_STORE_FAILURE_AT = "last_store_failure_at"
        private const val KEY_LAST_REMOVE_FAILURE_AT = "last_remove_failure_at"
        private const val KEY_LAST_EXPORT_FAILURE_AT = "last_export_failure_at"
        private const val KEY_LAST_CRASH_AT = "last_crash_at"
        private const val KEY_LAST_CRASH_THREAD = "last_crash_thread"
        private const val KEY_LAST_CRASH_EXCEPTION = "last_crash_exception"
        private const val KEY_LAST_CRASH_MESSAGE = "last_crash_message"
        private const val KEY_LAST_CRASH_PATH = "last_crash_path"
        private const val MAX_CRASH_REPORT_FILES = 10
        private const val MAX_CRASH_REPORT_BYTES = 2L * 1024L * 1024L

        private val crashFileFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
    }
}

class AppCrashReporter(
    private val metricsStore: OperationalMetricsStore,
    private val logger: AppLogger,
) {
    private var installed = false

    fun install() {
        if (installed) {
            return
        }
        installed = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                metricsStore.recordCrash(thread, throwable)
            }.onFailure {
                logger.warn("AppCrashReporter", "Failed to persist uncaught exception", it)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
