package dev.d3v.notificationsaver

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RootTab(val label: String) {
    Timeline("Timeline"),
    Threads("Threads"),
    Priority("Priority"),
    Settings("Settings"),
}

enum class DateWindow(val label: String, val days: Int?) {
    AllTime("All time", null),
    LastDay("24h", 1),
    Last7Days("7 days", 7),
    Last30Days("30 days", 30),
    Last90Days("90 days", 90);

    fun fromTimestamp(now: Long = System.currentTimeMillis()): Long? {
        val dayCount = days ?: return null
        return now - dayCount * 24L * 60L * 60L * 1000L
    }
}

data class ExportRequest(
    val filename: String,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotificationSaverApp
    private val repository = app.repository
    private val settingsStore = app.settingsStore
    private val logger = app.logger

    private val rootTab = MutableStateFlow(RootTab.Timeline)
    private val searchText = MutableStateFlow("")
    private val selectedPackage = MutableStateFlow<String?>(null)
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val selectedDateWindow = MutableStateFlow(DateWindow.AllTime)
    private val detailId = MutableStateFlow<Long?>(null)
    private val selectedMessagingThreadTab = MutableStateFlow<MessagingThreadTab?>(null)
    private val threadSelection = MutableStateFlow<ThreadSelection?>(null)
    private val exportRequest = MutableStateFlow<ExportRequest?>(null)
    private val diagnosticsExportRequest = MutableStateFlow<ExportRequest?>(null)
    private val notificationAccessGranted = MutableStateFlow(false)
    private val storageBucketsMutable = MutableStateFlow<List<StorageBucket>>(emptyList())

    val snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val settings: StateFlow<AppSettings> = settingsStore.state
    val currentRootTab: StateFlow<RootTab> = rootTab
    val currentSearchText: StateFlow<String> = searchText
    val currentSelectedPackage: StateFlow<String?> = selectedPackage
    val currentSelectedCategory: StateFlow<String?> = selectedCategory
    val currentDateWindow: StateFlow<DateWindow> = selectedDateWindow
    val currentDetailId: StateFlow<Long?> = detailId
    val currentThreadSelection: StateFlow<ThreadSelection?> = threadSelection
    val pendingExportRequest: StateFlow<ExportRequest?> = exportRequest
    val pendingDiagnosticsExportRequest: StateFlow<ExportRequest?> = diagnosticsExportRequest
    val isNotificationAccessGranted: StateFlow<Boolean> = notificationAccessGranted
    val storageBuckets: StateFlow<List<StorageBucket>> = storageBucketsMutable
    val operationalSnapshot: StateFlow<OperationalSnapshot> = app.operationalMetrics.state

    val notifications: StateFlow<List<NotificationEntity>> =
        combine(searchText.debounce(300), selectedPackage, selectedCategory, selectedDateWindow) { search, pkg, category, window ->
            NotificationFilter(
                searchText = search,
                packageName = pkg,
                category = category,
                dateWindow = window,
            )
        }.flatMapLatest { filter ->
            repository.observeNotifications(filter)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val appSources: StateFlow<List<AppSource>> = repository.observeAppSources()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val categories: StateFlow<List<String>> =
        combine(repository.observeCategories(), settingsStore.state) { observed, currentSettings ->
            (DEFAULT_CATEGORIES + observed + currentSettings.fallbackCategory)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DEFAULT_CATEGORIES,
        )

    val selectedNotification: StateFlow<NotificationEntity?> =
        detailId.flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                repository.observeNotification(id)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    val threadSummaries: StateFlow<List<ThreadSummary>> =
        combine(repository.observeThreadSummaries(), settingsStore.state) { summaries, currentSettings ->
            summaries.map { summary ->
                summary.copy(isPinned = summary.stableId in currentSettings.pinnedThreadIds)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val activeMessagingThreadTabs: StateFlow<List<MessagingThreadTab>> =
        threadSummaries.map { summaries ->
            summaries.activeMessagingThreadTabs()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val pinnedThreads: StateFlow<List<ThreadSummary>> =
        threadSummaries.map { summaries ->
            summaries.filter { it.isPinned }
                .sortedByDescending { it.latestTimestamp }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val suggestedThreads: StateFlow<List<ThreadSummary>> =
        threadSummaries.map { summaries ->
            summaries.filterNot { it.isPinned }
                .sortedWith(suggestedThreadComparator())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val currentSelectedMessagingThreadTab: StateFlow<MessagingThreadTab?> =
        combine(selectedMessagingThreadTab, activeMessagingThreadTabs) { selectedTab, activeTabs ->
            when {
                activeTabs.isEmpty() -> null
                selectedTab != null && selectedTab in activeTabs -> selectedTab
                else -> activeTabs.first()
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    val visibleMessagingThreads: StateFlow<List<ThreadSummary>> =
        combine(threadSummaries, currentSelectedMessagingThreadTab) { summaries, selectedTab ->
            if (selectedTab == null) {
                emptyList()
            } else {
                summaries.threadsForMessagingTab(selectedTab)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val selectedThreadSummary: StateFlow<ThreadSummary?> =
        combine(threadSelection, threadSummaries) { selection, summaries ->
            selection?.let { selected ->
                summaries.firstOrNull {
                    it.packageName == selected.packageName && it.resolvedThreadId == selected.resolvedThreadId
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    val selectedThreadNotifications: StateFlow<List<NotificationEntity>> =
        threadSelection.flatMapLatest { selection ->
            if (selection == null) {
                flowOf(emptyList())
            } else {
                repository.observeThread(selection.packageName, selection.resolvedThreadId)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    fun setNotificationAccessGranted(granted: Boolean) {
        notificationAccessGranted.value = granted
    }

    fun selectRootTab(tab: RootTab) {
        rootTab.value = tab
        if (tab == RootTab.Settings && storageBucketsMutable.value.isEmpty()) {
            refreshStorageUsage()
        }
    }

    fun updateSearchText(value: String) {
        searchText.value = value
    }

    fun updateSelectedPackage(value: String?) {
        selectedPackage.value = value
    }

    fun updateSelectedCategory(value: String?) {
        selectedCategory.value = value
    }

    fun updateDateWindow(window: DateWindow) {
        selectedDateWindow.value = window
    }

    fun openNotificationDetail(id: Long) {
        detailId.value = id
    }

    fun selectMessagingThreadTab(tab: MessagingThreadTab) {
        rootTab.value = RootTab.Threads
        selectedMessagingThreadTab.value = tab
    }

    fun openThread(record: NotificationEntity) {
        val resolvedThreadId = record.resolvedThreadId() ?: return
        threadSelection.value = ThreadSelection(
            packageName = record.packageName,
            resolvedThreadId = resolvedThreadId,
        )
    }

    fun openThread(summary: ThreadSummary) {
        threadSelection.value = ThreadSelection(
            packageName = summary.packageName,
            resolvedThreadId = summary.resolvedThreadId,
        )
    }

    fun goBack() {
        if (threadSelection.value != null) {
            threadSelection.value = null
            return
        }
        if (detailId.value != null) {
            detailId.value = null
            return
        }
    }

    fun dismissOnboarding() {
        settingsStore.markOnboardingCompleted()
    }

    fun setHideNotificationPreviews(enabled: Boolean) {
        settingsStore.setHideNotificationPreviews(enabled)
    }

    fun setRetentionDays(days: Int) {
        settingsStore.setRetentionDays(days)
        viewModelScope.launch {
            repository.runRetentionCleanupNow()
        }
    }

    fun setFallbackCategory(category: String) {
        settingsStore.setFallbackCategory(category)
    }

    fun setThemeMode(themeMode: ThemeMode) {
        settingsStore.setThemeMode(themeMode)
    }

    fun saveAppCategoryOverride(packageName: String, category: String) {
        settingsStore.setAppCategoryOverride(packageName, category)
        viewModelScope.launch {
            snackbarMessages.emit("Saved category override")
        }
    }

    fun removeAppCategoryOverride(packageName: String) {
        settingsStore.removeAppCategoryOverride(packageName)
        viewModelScope.launch {
            snackbarMessages.emit("Removed category override")
        }
    }

    fun saveNotificationMetadata(
        category: String,
        tagsText: String,
        manualThreadLabel: String,
    ) {
        val id = detailId.value ?: return
        val tags = tagsText.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        viewModelScope.launch {
            repository.saveManualMetadata(
                id = id,
                category = category,
                tags = tags,
                manualThreadLabel = manualThreadLabel,
            )
            snackbarMessages.emit("Saved notification changes")
        }
    }

    fun renameCurrentThread(manualThreadLabel: String) {
        val currentThread = threadSelection.value ?: return
        val normalizedLabel = manualThreadLabel.trimmedOrNull()
        val oldStableId = currentThread.stableId
        viewModelScope.launch {
            repository.renameThread(
                packageName = currentThread.packageName,
                resolvedThreadId = currentThread.resolvedThreadId,
                manualThreadLabel = normalizedLabel,
            )
            if (normalizedLabel == null) {
                settingsStore.replacePinnedThreadId(oldStableId, null)
                threadSelection.value = null
                snackbarMessages.emit("Cleared manual thread name")
                return@launch
            }
            val updatedSelection = ThreadSelection(
                packageName = currentThread.packageName,
                resolvedThreadId = normalizedLabel,
            )
            settingsStore.replacePinnedThreadId(oldStableId, updatedSelection.stableId)
            threadSelection.value = updatedSelection
            snackbarMessages.emit("Saved thread name")
        }
    }

    fun toggleThreadPinned(summary: ThreadSummary) {
        if (summary.isPinned) {
            settingsStore.unpinThread(summary.stableId)
            viewModelScope.launch {
                snackbarMessages.emit("Removed pinned thread")
            }
            return
        }
        settingsStore.pinThread(summary.stableId)
        viewModelScope.launch {
            snackbarMessages.emit("Pinned thread")
        }
    }

    fun deleteSelectedNotification() {
        val id = detailId.value ?: return
        viewModelScope.launch {
            repository.deleteNotification(id)
            detailId.value = null
            refreshStorageUsage()
            snackbarMessages.emit("Deleted notification")
        }
    }

    fun deleteCurrentFilterSet() {
        viewModelScope.launch {
            repository.deleteFiltered(
                packageName = selectedPackage.value,
                dateWindow = selectedDateWindow.value,
            )
            refreshStorageUsage()
            snackbarMessages.emit("Deleted filtered notifications")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllUserData()
            rootTab.value = RootTab.Timeline
            searchText.value = ""
            selectedPackage.value = null
            selectedCategory.value = null
            selectedDateWindow.value = DateWindow.AllTime
            detailId.value = null
            selectedMessagingThreadTab.value = null
            threadSelection.value = null
            refreshStorageUsage()
            snackbarMessages.emit("Cleared saved app data")
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            storageBucketsMutable.value = repository.computeStorageBuckets()
        }
    }

    fun requestExport() {
        exportRequest.value = ExportRequest(
            filename = "notification-saver-${NotificationRepository.exportFileFormatter.format(Instant.now())}.json",
        )
    }

    fun requestDiagnosticsExport() {
        diagnosticsExportRequest.value = ExportRequest(
            filename = "notification-saver-diagnostics-${NotificationRepository.exportFileFormatter.format(Instant.now())}.zip",
        )
    }

    fun markExportRequestConsumed() {
        exportRequest.value = null
    }

    fun markDiagnosticsExportRequestConsumed() {
        diagnosticsExportRequest.value = null
    }

    fun handleExportResult(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                snackbarMessages.emit("Export cancelled")
                return@launch
            }
            runCatching {
                repository.exportNotifications(uri)
            }.onSuccess {
                refreshStorageUsage()
                snackbarMessages.emit("Exported notifications")
            }.onFailure {
                snackbarMessages.emit("Export failed")
            }
        }
    }

    fun handleDiagnosticsExportResult(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                snackbarMessages.emit("Diagnostics export cancelled")
                return@launch
            }
            runCatching {
                repository.exportDiagnosticsBundle(uri)
            }.onSuccess {
                refreshStorageUsage()
                snackbarMessages.emit("Exported diagnostics bundle")
            }.onFailure {
                snackbarMessages.emit("Diagnostics export failed")
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            refreshStorageUsage()
            snackbarMessages.emit("Cleared log files")
        }
    }

    fun clearCrashReports() {
        viewModelScope.launch {
            repository.clearCrashReports()
            refreshStorageUsage()
            snackbarMessages.emit("Cleared crash reports")
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            DemoDataSupport.seed(app)
            refreshStorageUsage()
            snackbarMessages.emit("Loaded demo data")
        }
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Calls",
            "Email",
            DEFAULT_FALLBACK_CATEGORY,
            "Finance",
            "Messages",
            "Shopping",
            "Social",
            "System",
            "Travel",
        )

        val RETENTION_OPTIONS = listOf(0, 7, 30, 90, 365)
    }
}
