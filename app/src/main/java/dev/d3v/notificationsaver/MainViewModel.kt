package dev.d3v.notificationsaver

import android.app.Application
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RootDestination {
    Home,
    Chats,
    Settings,
}

enum class DateWindow(val days: Int?) {
    AllTime(null),
    Today(0),
    LastDay(1),
    Last7Days(7),
    Last30Days(30),
    Last90Days(90);

    fun fromTimestamp(now: Long = System.currentTimeMillis()): Long? {
        if (this == Today) {
            return LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        return days?.let { now - it * 24L * 60L * 60L * 1000L }
    }
}

enum class ExportKind {
    EncryptedBackup,
    ReadableJson,
    Diagnostics,
}

data class ExportRequest(
    val kind: ExportKind,
    val filename: String,
    val mimeType: String,
    val password: CharArray? = null,
)

data class ImportRequest(
    val password: CharArray?,
    val mode: ImportMode,
    val restoreSettings: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotificationSaverApp
    private val repository = app.repository
    private val settingsStore = app.settingsStore
    private val backupManager = app.backupManager

    private val searchText = MutableStateFlow("")
    private val selectedPackage = MutableStateFlow<String?>(null)
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val selectedDateWindow = MutableStateFlow(DateWindow.AllTime)
    private val chatSearchText = MutableStateFlow("")
    private val chatSelectedPackage = MutableStateFlow<String?>(null)
    private val selectedRecordId = MutableStateFlow<Long?>(null)
    private val selectedConversationId = MutableStateFlow<Long?>(null)
    private val notificationAccessGranted = MutableStateFlow(false)
    private val storageBucketsMutable = MutableStateFlow<List<StorageBucket>>(emptyList())
    private val exportRequest = MutableStateFlow<ExportRequest?>(null)
    private val importRequest = MutableStateFlow<ImportRequest?>(null)
    private val homeDayStart = MutableStateFlow(startOfToday())

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val settings: StateFlow<AppSettings> = settingsStore.state
    val settingsInitialized: StateFlow<Boolean> = settingsStore.initialized
    val currentSearchText: StateFlow<String> = searchText
    val currentSelectedPackage: StateFlow<String?> = selectedPackage
    val currentSelectedCategory: StateFlow<String?> = selectedCategory
    val currentDateWindow: StateFlow<DateWindow> = selectedDateWindow
    val currentChatSearchText: StateFlow<String> = chatSearchText
    val currentChatSelectedPackage: StateFlow<String?> = chatSelectedPackage
    val currentRecordId: StateFlow<Long?> = selectedRecordId
    val currentConversationId: StateFlow<Long?> = selectedConversationId
    val isNotificationAccessGranted: StateFlow<Boolean> = notificationAccessGranted
    val storageBuckets: StateFlow<List<StorageBucket>> = storageBucketsMutable
    val operationalSnapshot: StateFlow<OperationalSnapshot> = app.operationalMetrics.state
    val pendingExportRequest: StateFlow<ExportRequest?> = exportRequest
    val pendingImportRequest: StateFlow<ImportRequest?> = importRequest
    val homeSummary: StateFlow<HomeSummary> = homeDayStart
        .flatMapLatest(repository::observeHomeSummary)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummary(0, 0, null))

    val records: Flow<PagingData<NotificationRecordEntity>> =
        combine(
            searchText.map { it.trim() }.debounce(250).distinctUntilChanged(),
            selectedPackage,
            selectedCategory,
            selectedDateWindow,
        ) { search, pkg, category, date ->
            NotificationFilter(search, pkg, category, date)
        }
            .distinctUntilChanged()
            .flatMapLatest(repository::pageRecords)
            .cachedIn(viewModelScope)

    val conversations: Flow<PagingData<ConversationEntity>> =
        combine(
            chatSearchText.map { it.trim() }.debounce(250).distinctUntilChanged(),
            chatSelectedPackage,
        ) { search, pkg -> search to pkg }
            .distinctUntilChanged()
            .flatMapLatest { (search, pkg) -> repository.pageConversations(search, pkg) }
            .cachedIn(viewModelScope)

    val selectedRecord: StateFlow<NotificationRecordEntity?> = selectedRecordId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeRecord(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedRevisions: StateFlow<List<NotificationRevisionEntity>> = selectedRecordId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeRevisions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedConversation: StateFlow<ConversationEntity?> = selectedConversationId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeConversation(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val conversationEntries: Flow<PagingData<ConversationEntryRow>> = selectedConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(PagingData.empty()) else repository.pageConversationEntries(id)
        }
        .cachedIn(viewModelScope)

    val appSources: StateFlow<List<AppSource>> = repository.observeAppSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = combine(repository.observeCategories(), settingsStore.state) { observed, current ->
        (DEFAULT_CATEGORIES + observed + current.fallbackCategory).map(String::trim).filter(String::isNotEmpty).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_CATEGORIES)

    init {
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val nextDay = LocalDate.now().plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                delay((nextDay - now).coerceAtLeast(1_000L))
                homeDayStart.value = startOfToday()
            }
        }
    }

    fun setNotificationAccessGranted(granted: Boolean) {
        notificationAccessGranted.value = granted
    }

    fun refreshHomeSummary() { homeDayStart.value = startOfToday() }

    fun updateSearchText(value: String) { searchText.value = value }
    fun updateSelectedPackage(value: String?) { selectedPackage.value = value }
    fun updateSelectedCategory(value: String?) { selectedCategory.value = value }
    fun updateDateWindow(value: DateWindow) { selectedDateWindow.value = value }
    fun updateChatSearchText(value: String) { chatSearchText.value = value }
    fun updateChatSelectedPackage(value: String?) { chatSelectedPackage.value = value }
    fun openRecord(id: Long) { selectedRecordId.value = id }
    fun closeRecord() { selectedRecordId.value = null }
    fun openConversation(id: Long) { selectedConversationId.value = id }
    fun closeConversation() { selectedConversationId.value = null }

    fun dismissOnboarding() = settingsStore.markOnboardingCompleted()
    fun setHideNotificationPreviews(enabled: Boolean) = settingsStore.setHideNotificationPreviews(enabled)
    fun setHideRecentsPreview(enabled: Boolean) = settingsStore.setHideRecentsPreview(enabled)
    fun setBlockScreenshots(enabled: Boolean) = settingsStore.setBlockScreenshots(enabled)
    fun setAppLockEnabled(enabled: Boolean) {
        if (!enabled) {
            settingsStore.setAppLockEnabled(false)
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(getApplication()).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            settingsStore.setAppLockEnabled(true)
        } else {
            messages.tryEmit("Set up a device credential or strong biometric before enabling app lock")
        }
    }
    fun setAppLockTimeoutMinutes(minutes: Int) = settingsStore.setAppLockTimeoutMinutes(minutes)
    fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)
    fun setFallbackCategory(value: String) = settingsStore.setFallbackCategory(value)
    fun setPackageExcluded(packageName: String, excluded: Boolean) = settingsStore.setPackageExcluded(packageName, excluded)

    fun setRetentionDays(days: Int) {
        settingsStore.setRetentionDays(days)
        RetentionScheduler.sync(getApplication(), days)
        viewModelScope.launch { repository.runRetentionCleanupNow() }
    }

    fun saveAppCategoryOverride(packageName: String, category: String) {
        settingsStore.setAppCategoryOverride(packageName, category)
        messages.tryEmit("Saved category override")
    }

    fun removeAppCategoryOverride(packageName: String) {
        settingsStore.removeAppCategoryOverride(packageName)
        messages.tryEmit("Removed category override")
    }

    fun saveRecordMetadata(category: String, tagsText: String) {
        val id = selectedRecordId.value ?: return
        val tags = tagsText.split(',').mapNotNull(String::trimmedOrNull)
        viewModelScope.launch {
            repository.updateRecordMetadata(id, category, tags)
            messages.emit("Saved notification metadata")
        }
    }

    fun renameConversation(title: String) {
        val id = selectedConversationId.value ?: return
        viewModelScope.launch {
            repository.renameConversation(id, title)
            messages.emit(if (title.isBlank()) "Restored automatic conversation name" else "Renamed conversation")
        }
    }

    fun toggleConversationPinned(conversation: ConversationEntity) {
        viewModelScope.launch {
            repository.setConversationPinned(conversation.id, !conversation.isPinned)
            messages.emit(if (conversation.isPinned) "Unpinned conversation" else "Pinned conversation")
        }
    }

    fun deleteSelectedRecord() {
        val id = selectedRecordId.value ?: return
        viewModelScope.launch {
            repository.deleteRecord(id)
            selectedRecordId.value = null
            refreshStorageUsage()
            messages.emit("Deleted notification")
        }
    }

    fun deleteCurrentFilterSet() {
        viewModelScope.launch {
            repository.deleteFiltered(selectedPackage.value, selectedDateWindow.value)
            refreshStorageUsage()
            messages.emit("Deleted matching notifications")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllUserData()
            selectedRecordId.value = null
            selectedConversationId.value = null
            searchText.value = ""
            selectedPackage.value = null
            selectedCategory.value = null
            selectedDateWindow.value = DateWindow.AllTime
            refreshStorageUsage()
            messages.emit("Cleared local app data")
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch { storageBucketsMutable.value = repository.computeStorageBuckets() }
    }

    fun requestEncryptedBackup(password: String) {
        if (password.length < BackupManager.MIN_PASSWORD_LENGTH) {
            messages.tryEmit("Use at least ${BackupManager.MIN_PASSWORD_LENGTH} characters for the backup password")
            return
        }
        exportRequest.value = ExportRequest(
            kind = ExportKind.EncryptedBackup,
            filename = "notification-saver-${timestamp()}.nsbackup",
            mimeType = "application/octet-stream",
            password = password.toCharArray(),
        )
    }

    fun requestReadableExport() {
        exportRequest.value = ExportRequest(
            kind = ExportKind.ReadableJson,
            filename = "notification-saver-${timestamp()}.json",
            mimeType = "application/json",
        )
    }

    fun requestDiagnosticsExport() {
        exportRequest.value = ExportRequest(
            kind = ExportKind.Diagnostics,
            filename = "notification-saver-diagnostics-${timestamp()}.zip",
            mimeType = "application/zip",
        )
    }

    fun handleExportResult(uri: Uri?) {
        val request = exportRequest.value ?: return
        exportRequest.value = null
        if (uri == null) {
            request.password?.fill('\u0000')
            messages.tryEmit("Export cancelled")
            return
        }
        viewModelScope.launch {
            runCatching {
                when (request.kind) {
                    ExportKind.EncryptedBackup -> backupManager.exportEncrypted(uri, requireNotNull(request.password))
                    ExportKind.ReadableJson -> backupManager.exportReadableJson(uri)
                    ExportKind.Diagnostics -> repository.exportDiagnosticsBundle(uri)
                }
            }.onSuccess {
                messages.emit("Export complete")
            }.onFailure {
                request.password?.fill('\u0000')
                messages.emit("Export failed: ${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    fun requestImport(password: String, mode: ImportMode, restoreSettings: Boolean) {
        importRequest.value = ImportRequest(password.trimmedOrNull()?.toCharArray(), mode, restoreSettings)
    }

    fun handleImportResult(uri: Uri?) {
        val request = importRequest.value ?: return
        importRequest.value = null
        if (uri == null) {
            request.password?.fill('\u0000')
            messages.tryEmit("Import cancelled")
            return
        }
        viewModelScope.launch {
            runCatching {
                backupManager.import(uri, request.password, request.mode, request.restoreSettings)
            }.onSuccess { summary ->
                refreshStorageUsage()
                messages.emit("Imported ${summary.recordCount} notifications and ${summary.messageCount} messages")
            }.onFailure {
                request.password?.fill('\u0000')
                messages.emit("Import failed: ${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch { repository.clearLogs(); refreshStorageUsage(); messages.emit("Cleared logs") }
    }

    fun clearCrashReports() {
        viewModelScope.launch { repository.clearCrashReports(); refreshStorageUsage(); messages.emit("Cleared crash reports") }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            DemoDataSupport.seed(app)
            messages.emit("Loaded demo data")
        }
    }

    private fun timestamp(): String = NotificationRepository.exportFileFormatter.format(Instant.now())

    private fun startOfToday(): Long = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Calls", "Email", DEFAULT_FALLBACK_CATEGORY, "Finance", "Messages", "Shopping", "Social", "System", "Travel",
        )
        val RETENTION_OPTIONS = listOf(0, 7, 30, 90, 365)
    }
}
