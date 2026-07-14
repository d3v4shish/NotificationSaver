package dev.d3v.notificationsaver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificationSaverRoot(mainViewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.setNotificationAccessGranted(hasNotificationListenerAccess(this))
    }
}

@Composable
private fun NotificationSaverRoot(viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    NotificationSaverTheme(themeMode = settings.themeMode) {
        NotificationSaverScreen(
            viewModel = viewModel,
            settings = settings,
        )
    }
}

@Composable
private fun NotificationSaverScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
) {
    val currentTab by viewModel.currentRootTab.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val appSources by viewModel.appSources.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activeMessagingThreadTabs by viewModel.activeMessagingThreadTabs.collectAsState()
    val pinnedThreads by viewModel.pinnedThreads.collectAsState()
    val suggestedThreads by viewModel.suggestedThreads.collectAsState()
    val selectedPackage by viewModel.currentSelectedPackage.collectAsState()
    val selectedCategory by viewModel.currentSelectedCategory.collectAsState()
    val selectedDateWindow by viewModel.currentDateWindow.collectAsState()
    val searchText by viewModel.currentSearchText.collectAsState()
    val selectedNotification by viewModel.selectedNotification.collectAsState()
    val selectedMessagingThreadTab by viewModel.currentSelectedMessagingThreadTab.collectAsState()
    val selectedThread by viewModel.currentThreadSelection.collectAsState()
    val visibleMessagingThreads by viewModel.visibleMessagingThreads.collectAsState()
    val selectedThreadSummary by viewModel.selectedThreadSummary.collectAsState()
    val selectedThreadNotifications by viewModel.selectedThreadNotifications.collectAsState()
    val exportRequest by viewModel.pendingExportRequest.collectAsState()
    val diagnosticsExportRequest by viewModel.pendingDiagnosticsExportRequest.collectAsState()
    val notificationAccessGranted by viewModel.isNotificationAccessGranted.collectAsState()
    val storageBuckets by viewModel.storageBuckets.collectAsState()
    val operationalSnapshot by viewModel.operationalSnapshot.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmFilteredDelete by rememberSaveable { mutableStateOf(false) }
    var confirmClearAll by rememberSaveable { mutableStateOf(false) }
    var confirmSingleDelete by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        viewModel.handleExportResult(uri)
    }
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        viewModel.handleDiagnosticsExportResult(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(exportRequest) {
        val pending = exportRequest ?: return@LaunchedEffect
        exportLauncher.launch(pending.filename)
        viewModel.markExportRequestConsumed()
    }

    LaunchedEffect(diagnosticsExportRequest) {
        val pending = diagnosticsExportRequest ?: return@LaunchedEffect
        diagnosticsExportLauncher.launch(pending.filename)
        viewModel.markDiagnosticsExportRequestConsumed()
    }

    BackHandler(enabled = selectedNotification != null || selectedThread != null) {
        viewModel.goBack()
    }

    if (!settings.onboardingCompleted) {
        OnboardingDialog(
            onDismiss = viewModel::dismissOnboarding,
            onOpenSettings = { openNotificationListenerSettings(context) },
        )
    }

    if (confirmFilteredDelete) {
        ConfirmDialog(
            title = "Delete filtered notifications?",
            message = "This removes notifications matching the current app and date filters.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmFilteredDelete = false
                viewModel.deleteCurrentFilterSet()
            },
            onDismiss = { confirmFilteredDelete = false },
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "Clear all app data?",
            message = "This deletes saved notifications, logs, crash reports, cache, and resets app settings.",
            confirmLabel = "Clear all",
            onConfirm = {
                confirmClearAll = false
                viewModel.clearAllData()
            },
            onDismiss = { confirmClearAll = false },
        )
    }

    if (confirmSingleDelete) {
        ConfirmDialog(
            title = "Delete this notification?",
            message = "This removes only the selected saved notification.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmSingleDelete = false
                viewModel.deleteSelectedNotification()
            },
            onDismiss = { confirmSingleDelete = false },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            AppTopBar(
                currentTab = currentTab,
                selectedNotification = selectedNotification,
                selectedMessagingThreadTab = selectedMessagingThreadTab,
                selectedThread = selectedThread,
                selectedThreadSummary = selectedThreadSummary,
                onBack = viewModel::goBack,
            )
        },
        bottomBar = {
            if (selectedNotification == null && selectedThread == null) {
                PrimaryTabRow(
                    selectedTabIndex = currentTab.ordinal,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    RootTab.entries.forEach { tab ->
                        Tab(
                            selected = currentTab == tab,
                            onClick = { viewModel.selectRootTab(tab) },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                selectedThread != null -> {
                    selectedThread?.let { currentThread ->
                        ThreadScreen(
                            selection = currentThread,
                            thread = selectedThreadSummary,
                            notifications = selectedThreadNotifications,
                            onRenameThread = viewModel::renameCurrentThread,
                            onTogglePinned = {
                                selectedThreadSummary?.let(viewModel::toggleThreadPinned)
                            },
                        )
                    }
                }

                selectedNotification != null -> {
                    selectedNotification?.let { detail ->
                        NotificationDetailScreen(
                            notification = detail,
                            onSave = viewModel::saveNotificationMetadata,
                            onDelete = { confirmSingleDelete = true },
                            onOpenThread = { viewModel.openThread(detail) },
                        )
                    }
                }

                currentTab == RootTab.Settings -> {
                    SettingsScreen(
                        settings = settings,
                        storageBuckets = storageBuckets,
                        operationalSnapshot = operationalSnapshot,
                        appSources = appSources,
                        notificationAccessGranted = notificationAccessGranted,
                        onOpenNotificationAccess = { openNotificationListenerSettings(context) },
                        onHidePreviewsChanged = viewModel::setHideNotificationPreviews,
                        onRetentionSelected = viewModel::setRetentionDays,
                        onFallbackCategoryChanged = viewModel::setFallbackCategory,
                        onThemeModeChanged = viewModel::setThemeMode,
                        onSaveAppCategoryOverride = viewModel::saveAppCategoryOverride,
                        onRemoveAppCategoryOverride = viewModel::removeAppCategoryOverride,
                        onRefreshStorage = viewModel::refreshStorageUsage,
                        onRequestExport = viewModel::requestExport,
                        onRequestDiagnosticsExport = viewModel::requestDiagnosticsExport,
                        onClearLogs = viewModel::clearLogs,
                        onClearCrashReports = viewModel::clearCrashReports,
                        onLoadDemoData = viewModel::loadDemoData,
                        onClearAll = { confirmClearAll = true },
                    )
                }

                currentTab == RootTab.Threads -> {
                    MessagingThreadsScreen(
                        activeTabs = activeMessagingThreadTabs,
                        selectedTab = selectedMessagingThreadTab,
                        threads = visibleMessagingThreads,
                        hidePreviews = settings.hideNotificationPreviews,
                        onSelectTab = viewModel::selectMessagingThreadTab,
                        onOpenThread = { viewModel.openThread(it) },
                        onTogglePinned = viewModel::toggleThreadPinned,
                    )
                }

                currentTab == RootTab.Priority -> {
                    PriorityScreen(
                        pinnedThreads = pinnedThreads,
                        suggestedThreads = suggestedThreads,
                        hidePreviews = settings.hideNotificationPreviews,
                        onOpenThread = { viewModel.openThread(it) },
                        onTogglePinned = viewModel::toggleThreadPinned,
                    )
                }

                else -> {
                    TimelineScreen(
                        notificationAccessGranted = notificationAccessGranted,
                        notifications = notifications,
                        appSources = appSources,
                        categories = categories,
                        searchText = searchText,
                        selectedPackage = selectedPackage,
                        selectedCategory = selectedCategory,
                        selectedDateWindow = selectedDateWindow,
                        hideNotificationPreviews = settings.hideNotificationPreviews,
                        onOpenNotificationAccess = { openNotificationListenerSettings(context) },
                        onSearchChanged = viewModel::updateSearchText,
                        onPackageChanged = viewModel::updateSelectedPackage,
                        onCategoryChanged = viewModel::updateSelectedCategory,
                        onDateWindowChanged = viewModel::updateDateWindow,
                        onOpenNotification = { viewModel.openNotificationDetail(it.id) },
                        onDeleteFiltered = { confirmFilteredDelete = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(
    currentTab: RootTab,
    selectedNotification: NotificationEntity?,
    selectedMessagingThreadTab: MessagingThreadTab?,
    selectedThread: ThreadSelection?,
    selectedThreadSummary: ThreadSummary?,
    onBack: () -> Unit,
) {
    val isDetailScreen = selectedNotification != null || selectedThread != null
    val title = when {
        selectedThread != null -> selectedThreadSummary?.displayTitle ?: selectedThread.resolvedThreadId
        selectedNotification != null -> "Notification"
        currentTab == RootTab.Timeline -> "Timeline"
        currentTab == RootTab.Threads -> "Threads"
        currentTab == RootTab.Priority -> "Priority"
        currentTab == RootTab.Settings -> "Settings"
        else -> "Timeline"
    }
    val subtitle = when {
        selectedThread != null -> selectedThreadSummary?.packageName ?: selectedThread.packageName
        selectedNotification != null -> selectedNotification.appLabel
        currentTab == RootTab.Timeline -> "Search and revisit saved notifications"
        currentTab == RootTab.Threads -> selectedMessagingThreadTab?.label ?: "Supported messaging apps"
        currentTab == RootTab.Priority -> "Pinned and suggested conversations"
        currentTab == RootTab.Settings -> "Basic settings with advanced tools tucked away"
        else -> "Search and revisit saved notifications"
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = if (isDetailScreen) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isDetailScreen) {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = if (isDetailScreen) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun OnboardingDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("How this app works") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Grant notification listener access.")
                Text("2. The app saves incoming notifications locally on your device.")
                Text("3. You can browse, pin, export, and delete saved history from the UI.")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Continue")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onOpenSettings) {
                Text("Open access settings")
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TimelineScreen(
    notificationAccessGranted: Boolean,
    notifications: List<NotificationEntity>,
    appSources: List<AppSource>,
    categories: List<String>,
    searchText: String,
    selectedPackage: String?,
    selectedCategory: String?,
    selectedDateWindow: DateWindow,
    hideNotificationPreviews: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onPackageChanged: (String?) -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onDateWindowChanged: (DateWindow) -> Unit,
    onOpenNotification: (NotificationEntity) -> Unit,
    onDeleteFiltered: () -> Unit,
) {
    val deleteScopeActive = selectedPackage != null || selectedDateWindow != DateWindow.AllTime
    val appFilterLabel = appSources.firstOrNull { it.packageName == selectedPackage }?.appLabel ?: "All apps"
    val categoryFilterLabel = selectedCategory ?: "All categories"
    var showFilters by rememberSaveable { mutableStateOf(false) }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
        ) {
            TimelineFilterSheet(
                appSources = appSources,
                categories = categories,
                selectedPackage = selectedPackage,
                selectedCategory = selectedCategory,
                selectedDateWindow = selectedDateWindow,
                deleteScopeActive = deleteScopeActive,
                onPackageChanged = onPackageChanged,
                onCategoryChanged = onCategoryChanged,
                onDateWindowChanged = onDateWindowChanged,
                onDeleteFiltered = {
                    showFilters = false
                    onDeleteFiltered()
                },
                onDismiss = { showFilters = false },
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NotificationAccessStatus(
                notificationAccessGranted = notificationAccessGranted,
                onOpenNotificationAccess = onOpenNotificationAccess,
            )
        }
        item {
            SectionBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = onSearchChanged,
                        label = { Text("Search title or body") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedButton(onClick = { showFilters = true }) {
                        Text("Filters")
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterSummaryPill(
                        text = appFilterLabel,
                        emphasized = selectedPackage != null,
                    )
                    FilterSummaryPill(
                        text = categoryFilterLabel,
                        emphasized = selectedCategory != null,
                    )
                    FilterSummaryPill(
                        text = selectedDateWindow.label,
                        emphasized = selectedDateWindow != DateWindow.AllTime,
                    )
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                EmptyState(
                    title = "No saved notifications yet",
                    message = if (notificationAccessGranted) {
                        "New notifications will appear here after they are captured."
                    } else {
                        "Grant notification access first, then incoming notifications can be saved."
                    },
                )
            }
        } else {
            item {
                Text(
                    text = "${notifications.size} saved notifications",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(
                items = notifications,
                key = { it.id },
            ) { notification ->
                NotificationCard(
                    notification = notification,
                    hideContent = hideNotificationPreviews,
                    onClick = { onOpenNotification(notification) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineFilterSheet(
    appSources: List<AppSource>,
    categories: List<String>,
    selectedPackage: String?,
    selectedCategory: String?,
    selectedDateWindow: DateWindow,
    deleteScopeActive: Boolean,
    onPackageChanged: (String?) -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onDateWindowChanged: (DateWindow) -> Unit,
    onDeleteFiltered: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        Text("Apps", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedPackage == null,
                onClick = { onPackageChanged(null) },
                label = { Text("All apps") },
            )
            appSources.forEach { source ->
                FilterChip(
                    selected = selectedPackage == source.packageName,
                    onClick = { onPackageChanged(source.packageName) },
                    label = { Text(source.appLabel) },
                )
            }
        }

        Text("Categories", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategoryChanged(null) },
                label = { Text("All categories") },
            )
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategoryChanged(category) },
                    label = { Text(category) },
                )
            }
        }

        Text("Date range", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateWindow.entries.forEach { window ->
                FilterChip(
                    selected = selectedDateWindow == window,
                    onClick = { onDateWindowChanged(window) },
                    label = { Text(window.label) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDismiss) {
                Text("Done")
            }
            OutlinedButton(
                onClick = onDeleteFiltered,
                enabled = deleteScopeActive,
            ) {
                Text("Delete current scope")
            }
        }
        if (!deleteScopeActive) {
            Text(
                text = "Bulk delete only applies when an app or date window is selected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MessagingThreadsScreen(
    activeTabs: List<MessagingThreadTab>,
    selectedTab: MessagingThreadTab?,
    threads: List<ThreadSummary>,
    hidePreviews: Boolean,
    onSelectTab: (MessagingThreadTab) -> Unit,
    onOpenThread: (ThreadSummary) -> Unit,
    onTogglePinned: (ThreadSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (activeTabs.isEmpty()) {
            item {
                EmptyState(
                    title = "No messaging threads yet",
                    message = "WhatsApp, Instagram, and Telegram conversations appear here when notifications expose conversation names, sender names, or manual thread labels.",
                )
            }
        } else {
            val currentTab = selectedTab ?: activeTabs.first()

            item {
                PrimaryTabRow(selectedTabIndex = activeTabs.indexOf(currentTab)) {
                    activeTabs.forEach { tab ->
                        Tab(
                            selected = currentTab == tab,
                            onClick = { onSelectTab(tab) },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }
            item {
                Text(
                    text = "${threads.size} grouped conversations",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (threads.isEmpty()) {
                item {
                    EmptyState(
                        title = "No threads in ${currentTab.label}",
                        message = "This app currently has no grouped conversations to show.",
                    )
                }
            } else {
                items(
                    items = threads,
                    key = { it.stableId },
                ) { thread ->
                    ThreadSummaryCard(
                        thread = thread,
                        hidePreview = hidePreviews,
                        onOpenThread = { onOpenThread(thread) },
                        onTogglePinned = { onTogglePinned(thread) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityScreen(
    pinnedThreads: List<ThreadSummary>,
    suggestedThreads: List<ThreadSummary>,
    hidePreviews: Boolean,
    onOpenThread: (ThreadSummary) -> Unit,
    onTogglePinned: (ThreadSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeading("Pinned")
        }
        if (pinnedThreads.isEmpty()) {
            item {
                EmptyState(
                    title = "No pinned threads",
                    message = "Pin important conversations from the Threads list or from inside a thread.",
                )
            }
        } else {
            items(
                items = pinnedThreads,
                key = { it.stableId },
            ) { thread ->
                ThreadSummaryCard(
                    thread = thread,
                    hidePreview = hidePreviews,
                    onOpenThread = { onOpenThread(thread) },
                    onTogglePinned = { onTogglePinned(thread) },
                )
            }
        }

        item {
            SectionHeading("Suggested")
        }
        if (suggestedThreads.isEmpty()) {
            item {
                EmptyState(
                    title = "No suggested threads",
                    message = "Suggested threads appear after the app has enough recent conversation activity to rank.",
                )
            }
        } else {
            items(
                items = suggestedThreads,
                key = { it.stableId },
            ) { thread ->
                ThreadSummaryCard(
                    thread = thread,
                    hidePreview = hidePreviews,
                    onOpenThread = { onOpenThread(thread) },
                    onTogglePinned = { onTogglePinned(thread) },
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationEntity,
    hideContent: Boolean,
    onClick: () -> Unit,
) {
    val preview = buildNotificationPreview(notification, hideContent)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (notification.isRemoved) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = notification.appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTimestamp(notification.lastSeenAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.first?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            preview.second
                ?.takeIf { it.isNotBlank() && it != preview.first }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notification.isRemoved) {
                    SmallBadge("Removed")
                }
                if (notification.manualThreadLabel.trimmedOrNull() != null) {
                    SmallBadge("Manual")
                }
            }
        }
    }
}

@Composable
private fun ThreadSummaryCard(
    thread: ThreadSummary,
    hidePreview: Boolean,
    onOpenThread: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenThread),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = thread.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = thread.appLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onTogglePinned) {
                    Text(if (thread.isPinned) "Unpin" else "Pin")
                }
            }
            Text(
                text = if (hidePreview) {
                    "Content hidden"
                } else {
                    thread.latestPreview.orEmpty().ifBlank { "No preview text saved." }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${thread.totalCount} items • Last activity ${formatTimestamp(thread.latestTimestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationDetailScreen(
    notification: NotificationEntity,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit,
    onOpenThread: () -> Unit,
) {
    var categoryText by remember(notification.id) { mutableStateOf(notification.category) }
    var tagsText by remember(notification.id) { mutableStateOf(notification.tags.joinToString(", ")) }
    var threadLabelText by remember(notification.id) { mutableStateOf(notification.manualThreadLabel.orEmpty()) }
    var showDiagnostics by rememberSaveable(notification.id) { mutableStateOf(false) }
    val hasThread = notification.resolvedThreadId() != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionBlock(title = "Overview") {
            DetailLine("App", notification.appLabel)
            DetailLine("Package", notification.packageName)
            DetailLine("Category", notification.category)
            DetailLine("Posted", formatTimestamp(notification.postedAt))
            DetailLine("Last update", formatTimestamp(notification.lastSeenAt))
            notification.removedAt?.let { DetailLine("Removed", formatTimestamp(it)) }
            notification.senderName?.let { DetailLine("Sender", it) }
            notification.conversationTitle?.let { DetailLine("Conversation", it) }
            notification.manualThreadLabel?.let { DetailLine("Manual thread", it) }
        }

        SectionBlock(title = "Saved content") {
            Text(
                text = notification.title.orEmpty().ifBlank { "(No title)" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = notification.body.orEmpty().ifBlank { "(No body)" },
                style = MaterialTheme.typography.bodyLarge,
            )
            notification.bigText?.takeIf { it.isNotBlank() && it != notification.body }?.let {
                HorizontalDivider()
                Text("Expanded text", style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionBlock(title = "Edit metadata") {
            OutlinedTextField(
                value = categoryText,
                onValueChange = { categoryText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tags (comma-separated)") },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = threadLabelText,
                onValueChange = { threadLabelText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Manual thread name") },
                supportingText = {
                    Text("Leave blank to use notification metadata for grouping.")
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(categoryText, tagsText, threadLabelText) }) {
                    Text("Save")
                }
                if (hasThread) {
                    OutlinedButton(onClick = onOpenThread) {
                        Text("Open thread")
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }

        SectionBlock(
            title = "Diagnostics",
            subtitle = "Raw notification extras",
            actionLabel = if (showDiagnostics) "Hide" else "Show",
            onAction = { showDiagnostics = !showDiagnostics },
        ) {
            if (showDiagnostics) {
                Spacer(Modifier.height(8.dp))
                Text(notification.extrasJson, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ThreadScreen(
    selection: ThreadSelection,
    thread: ThreadSummary?,
    notifications: List<NotificationEntity>,
    onRenameThread: (String) -> Unit,
    onTogglePinned: () -> Unit,
) {
    var manualThreadName by rememberSaveable(selection.stableId) { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionBlock(title = thread?.displayTitle ?: selection.resolvedThreadId) {
                Text(
                    text = thread?.packageName ?: selection.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${notifications.size} items in thread",
                    style = MaterialTheme.typography.labelLarge,
                )
                thread?.let { currentThread ->
                    TextButton(onClick = onTogglePinned) {
                        Text(if (currentThread.isPinned) "Unpin" else "Pin")
                    }
                }
            }
        }
        item {
            SectionBlock(title = "Manual thread name") {
                OutlinedTextField(
                    value = manualThreadName,
                    onValueChange = { manualThreadName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Thread name") },
                    supportingText = {
                        Text("Save to rename this whole thread. Clear to fall back to notification metadata.")
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRenameThread(manualThreadName) }) {
                        Text("Save name")
                    }
                    OutlinedButton(
                        onClick = {
                            manualThreadName = ""
                            onRenameThread("")
                        },
                    ) {
                        Text("Clear name")
                    }
                }
            }
        }
        if (notifications.isEmpty()) {
            item {
                EmptyState(
                    title = "No thread items",
                    message = "This thread has no saved items right now.",
                )
            }
        } else {
            items(
                items = notifications,
                key = { it.id },
            ) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (item.isRemoved) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.senderName ?: item.title ?: item.appLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = item.body.orEmpty().ifBlank { item.bigText.orEmpty().ifBlank { "(No body)" } },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatTimestamp(item.postedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    settings: AppSettings,
    storageBuckets: List<StorageBucket>,
    operationalSnapshot: OperationalSnapshot,
    appSources: List<AppSource>,
    notificationAccessGranted: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onHidePreviewsChanged: (Boolean) -> Unit,
    onRetentionSelected: (Int) -> Unit,
    onFallbackCategoryChanged: (String) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSaveAppCategoryOverride: (String, String) -> Unit,
    onRemoveAppCategoryOverride: (String) -> Unit,
    onRefreshStorage: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestDiagnosticsExport: () -> Unit,
    onClearLogs: () -> Unit,
    onClearCrashReports: () -> Unit,
    onLoadDemoData: () -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    var fallbackCategoryText by remember(settings.fallbackCategory) { mutableStateOf(settings.fallbackCategory) }
    var overridePackageText by rememberSaveable { mutableStateOf("") }
    var overrideCategoryText by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NotificationAccessStatus(
            notificationAccessGranted = notificationAccessGranted,
            onOpenNotificationAccess = onOpenNotificationAccess,
        )

        SectionBlock(title = "Appearance") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { themeMode ->
                    FilterChip(
                        selected = settings.themeMode == themeMode,
                        onClick = { onThemeModeChanged(themeMode) },
                        label = { Text(themeMode.label) },
                    )
                }
            }
        }

        SectionBlock(title = "Privacy & retention") {
            SettingToggleRow(
                title = "Hide previews in list views",
                description = "Keep timeline, threads, and priority preview text hidden until a detail view is opened.",
                checked = settings.hideNotificationPreviews,
                onCheckedChange = onHidePreviewsChanged,
            )
            Spacer(Modifier.height(16.dp))
            Text("Retention window", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MainViewModel.RETENTION_OPTIONS.forEach { days ->
                    FilterChip(
                        selected = settings.retentionDays == days,
                        onClick = { onRetentionSelected(days) },
                        label = {
                            Text(
                                when (days) {
                                    0 -> "Never auto-delete"
                                    1 -> "1 day"
                                    else -> "$days days"
                                },
                            )
                        },
                    )
                }
            }
        }

        SectionBlock(title = "Categories") {
            OutlinedTextField(
                value = fallbackCategoryText,
                onValueChange = { fallbackCategoryText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Fallback category") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onFallbackCategoryChanged(fallbackCategoryText) }) {
                Text("Save fallback")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Per-app override", style = MaterialTheme.typography.labelLarge)
            if (appSources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    appSources.forEach { source ->
                        FilterChip(
                            selected = overridePackageText == source.packageName,
                            onClick = { overridePackageText = source.packageName },
                            label = { Text(source.appLabel) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = overridePackageText,
                onValueChange = { overridePackageText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Package name") },
                singleLine = true,
                supportingText = {
                    Text("Pick a known app above or enter a package manually.")
                },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = overrideCategoryText,
                onValueChange = { overrideCategoryText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Override category") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onSaveAppCategoryOverride(overridePackageText, overrideCategoryText)
                    overridePackageText = ""
                    overrideCategoryText = ""
                },
            ) {
                Text("Save override")
            }

            if (settings.appCategoryOverrides.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No app overrides configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(16.dp))
                settings.appCategoryOverrides.forEach { (packageName, category) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(packageName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRemoveAppCategoryOverride(packageName) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }

        SectionBlock(title = "About & support") {
            DetailLine("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            DetailLine("Build", BuildConfig.BUILD_TYPE.replaceFirstChar(Char::uppercase))
            DetailLine("License", BuildConfig.OSS_LICENSE_NAME)
            DetailLine("Package", BuildConfig.APPLICATION_ID)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openUrl(BuildConfig.PUBLIC_REPO_URL, context) }) {
                    Text("GitHub repo")
                }
                OutlinedButton(onClick = { openUrl(BuildConfig.PUBLIC_LATEST_RELEASE_URL, context) }) {
                    Text("Latest release")
                }
                OutlinedButton(onClick = { openUrl(BuildConfig.PUBLIC_PRIVACY_URL, context) }) {
                    Text("Privacy policy")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Diagnostics bundles include local app health data, logs, crash reports, and storage summaries. Notification contents are not included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestDiagnosticsExport) {
                    Text("Export diagnostics")
                }
                OutlinedButton(onClick = onRequestExport) {
                    Text("Export notifications")
                }
            }
            if (DemoDataSupport.isAvailable) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onLoadDemoData) {
                    Text("Load demo data")
                }
            }
        }

        SectionBlock(
            title = "Advanced",
            subtitle = "Storage, export, and destructive tools",
            actionLabel = if (showAdvanced) "Hide" else "Show",
            onAction = { showAdvanced = !showAdvanced },
        ) {
            if (showAdvanced) {
                Spacer(Modifier.height(8.dp))
                Text("Storage & export", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefreshStorage) {
                        Text("Refresh usage")
                    }
                    OutlinedButton(onClick = onRequestDiagnosticsExport) {
                        Text("Export diagnostics")
                    }
                    OutlinedButton(onClick = onRequestExport) {
                        Text("Export notifications")
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (storageBuckets.isEmpty()) {
                    Text(
                        "Storage report unavailable yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    storageBuckets.forEach { bucket ->
                        StorageBucketRow(bucket)
                    }
                    Text(
                        text = "Total: ${formatBytes(storageBuckets.sumOf { it.sizeBytes })}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Operational health", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Local counters help verify listener health, capture throughput, export failures, and uncaught crashes in release builds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OperationalMetricRow("App starts", operationalSnapshot.appStartCount.toString())
                OperationalMetricRow("Listener connected", operationalSnapshot.listenerConnectedCount.toString())
                OperationalMetricRow("Listener disconnected", operationalSnapshot.listenerDisconnectedCount.toString())
                OperationalMetricRow("Posted events stored", operationalSnapshot.postedEventCount.toString())
                OperationalMetricRow("Removed events stored", operationalSnapshot.removedEventCount.toString())
                OperationalMetricRow("Queue drops", operationalSnapshot.queueDropCount.toString())
                OperationalMetricRow("Store failures", operationalSnapshot.storeFailureCount.toString())
                OperationalMetricRow("Remove failures", operationalSnapshot.removeFailureCount.toString())
                OperationalMetricRow("Export failures", operationalSnapshot.exportFailureCount.toString())
                OperationalMetricRow("Last app start", formatOptionalTimestamp(operationalSnapshot.lastAppStartAt))
                OperationalMetricRow("Last listener connect", formatOptionalTimestamp(operationalSnapshot.lastListenerConnectedAt))
                OperationalMetricRow("Last listener disconnect", formatOptionalTimestamp(operationalSnapshot.lastListenerDisconnectedAt))
                OperationalMetricRow("Last queue drop", formatOptionalTimestamp(operationalSnapshot.lastQueueDropAt))
                OperationalMetricRow("Last store failure", formatOptionalTimestamp(operationalSnapshot.lastStoreFailureAt))
                OperationalMetricRow("Last remove failure", formatOptionalTimestamp(operationalSnapshot.lastRemoveFailureAt))
                OperationalMetricRow("Last export failure", formatOptionalTimestamp(operationalSnapshot.lastExportFailureAt))
                operationalSnapshot.latestCrash?.let { crash ->
                    OperationalMetricRow("Latest crash", "${crash.exceptionType} at ${formatTimestamp(crash.occurredAt)}")
                    OperationalMetricRow("Crash thread", crash.threadName)
                    crash.message?.takeIf { it.isNotBlank() }?.let { message ->
                        OperationalMetricRow("Crash message", message)
                    }
                    OperationalMetricRow("Crash report", crash.reportPath)
                } ?: Text(
                    text = "No crash reports recorded on this device yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = "Danger zone",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Routine cleanup",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClearLogs) {
                        Text("Clear logs")
                    }
                    OutlinedButton(onClick = onClearCrashReports) {
                        Text("Clear crash reports")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Clear all deletes saved notifications, logs, crash reports, cache, and settings from inside the app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onClearAll) {
                    Text("Clear all app data")
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun NotificationAccessStatus(
    notificationAccessGranted: Boolean,
    onOpenNotificationAccess: () -> Unit,
) {
    if (notificationAccessGranted) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Notification access granted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = onOpenNotificationAccess) {
                Text("Manage")
            }
        }
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Notification access needed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Grant access in system settings so incoming notifications can be saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenNotificationAccess) {
                Text("Open settings")
            }
        }
    }
}

@Composable
private fun StorageBucketRow(bucket: StorageBucket) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(bucket.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = bucket.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(formatBytes(bucket.sizeBytes), style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun OperationalMetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionBlock(
    title: String? = null,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null || subtitle != null || (actionLabel != null && onAction != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        title?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction) {
                            Text(actionLabel)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun FilterSummaryPill(
    text: String,
    emphasized: Boolean,
) {
    Surface(
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SmallBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun buildNotificationPreview(
    notification: NotificationEntity,
    hideContent: Boolean,
): Pair<String?, String?> {
    if (hideContent) {
        return "Content hidden" to "Open the detail view to reveal saved text."
    }
    return notification.title to (notification.body ?: notification.bigText)
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(timestamp))
}

private fun formatOptionalTimestamp(timestamp: Long?): String {
    return timestamp?.let(::formatTimestamp) ?: "Never"
}

private fun formatBytes(sizeBytes: Long): String {
    if (sizeBytes < 1024) {
        return "$sizeBytes B"
    }
    if (sizeBytes < 1024 * 1024) {
        return "${sizeBytes / 1024} KB"
    }
    return String.format("%.2f MB", sizeBytes / (1024f * 1024f))
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.System -> "Follow system"
        ThemeMode.Light -> "Light"
        ThemeMode.Dark -> "Dark"
    }

private fun hasNotificationListenerAccess(context: Context): Boolean {
    val component = ComponentName(context, NotificationCaptureService::class.java)
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ).orEmpty()
    return enabledListeners.split(':').any { flattened ->
        ComponentName.unflattenFromString(flattened) == component
    }
}

private fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(intent)
    }.recoverCatching {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openUrl(url: String, context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
