package dev.d3v.notificationsaver

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.util.LruCache
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip as MaterialFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.CancellationException
import java.util.Locale

@Serializable
private sealed interface AppRoute : NavKey

@Serializable
private data object InboxRoute : AppRoute

@Serializable
private data object ChatsRoute : AppRoute

@Serializable
private data object SettingsRoute : AppRoute

@Serializable
private data object CaptureSettingsRoute : AppRoute

@Serializable
private data object PrivacySettingsRoute : AppRoute

@Serializable
private data object StorageSettingsRoute : AppRoute

@Serializable
private data object BackupSettingsRoute : AppRoute

@Serializable
private data object AdvancedSettingsRoute : AppRoute

@Serializable
private data object AboutSettingsRoute : AppRoute

@Serializable
private data class RecordRoute(val id: Long) : AppRoute

@Serializable
private data class ConversationRoute(val id: Long) : AppRoute

class MainActivity : FragmentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private var appUnlocked by mutableStateOf(false)
    private var backgroundedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificationSaverRoot(
                viewModel = mainViewModel,
                appUnlocked = appUnlocked,
                onUnlock = ::authenticate,
                onLock = { appUnlocked = false },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.setNotificationAccessGranted(hasNotificationListenerAccess(this))
        mainViewModel.refreshHomeSummary()
        val app = application as NotificationSaverApp
        val settings = app.settingsStore.current()
        if (!settings.appLockEnabled) {
            appUnlocked = true
        } else if (backgroundedAt > 0L) {
            val timeout = settings.appLockTimeoutMinutes * 60_000L
            if (settings.appLockTimeoutMinutes == 0 || System.currentTimeMillis() - backgroundedAt >= timeout) {
                appUnlocked = false
            }
        }
    }

    override fun onStop() {
        backgroundedAt = System.currentTimeMillis()
        super.onStop()
    }

    private fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    appUnlocked = true
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_title))
                .setSubtitle(getString(R.string.unlock_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }
}

@Composable
private fun NotificationSaverRoot(
    viewModel: MainViewModel,
    appUnlocked: Boolean,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val settingsInitialized by viewModel.settingsInitialized.collectAsStateWithLifecycle()
    val activity = requireNotNull(LocalActivity.current)
    LaunchedEffect(settings.blockScreenshots, settings.hideRecentsPreview) {
        applyWindowPrivacy(activity, settings)
    }
    NotificationSaverTheme(themeMode = settings.themeMode) {
        LaunchedEffect(settingsInitialized, settings.appLockEnabled) {
            if (settingsInitialized && settings.appLockEnabled) onLock()
        }
        if (!settingsInitialized) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.loading)) }
            }
        } else if (settings.appLockEnabled && !appUnlocked) {
            LockedScreen(onUnlock)
        } else if (!settings.onboardingCompleted) {
            OnboardingScreen(
                onOpenAccess = { openNotificationListenerSettings(activity) },
                onContinue = viewModel::dismissOnboarding,
            )
        } else {
            AppNavigation(viewModel, settings)
        }
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_locked), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_locked_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text(stringResource(R.string.unlock)) }
        }
    }
}

@Composable
private fun OnboardingScreen(onOpenAccess: () -> Unit, onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.onboarding_summary), style = MaterialTheme.typography.bodyLarge)
            }
            item {
                DisclosureCard(
                    icon = Icons.Default.Notifications,
                    titleRes = R.string.onboarding_capture_title,
                    bodyRes = R.string.onboarding_capture_body_compact,
                )
            }
            item {
                DisclosureCard(
                    icon = Icons.Default.Shield,
                    titleRes = R.string.onboarding_private_title,
                    bodyRes = R.string.onboarding_private_body_compact,
                )
            }
            item {
                Button(onClick = onOpenAccess, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.open_notification_access))
                }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.continue_to_app))
                }
            }
        }
    }
}

@Composable
private fun DisclosureCard(icon: ImageVector, titleRes: Int, bodyRes: Int) {
    UtilityCard(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(bodyRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigation(viewModel: MainViewModel, settings: AppSettings) {
    val backStack = rememberNavBackStack(InboxRoute)
    val snackbarHostState = remember { SnackbarHostState() }
    val exportRequest by viewModel.pendingExportRequest.collectAsStateWithLifecycle()
    val importRequest by viewModel.pendingImportRequest.collectAsStateWithLifecycle()
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), viewModel::handleExportResult)
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), viewModel::handleExportResult)
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), viewModel::handleExportResult)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::handleImportResult)

    LaunchedEffect(Unit) {
        viewModel.messages.collect(snackbarHostState::showSnackbar)
    }
    LaunchedEffect(exportRequest) {
        val request = exportRequest ?: return@LaunchedEffect
        when (request.kind) {
            ExportKind.EncryptedBackup -> backupLauncher.launch(request.filename)
            ExportKind.ReadableJson -> jsonLauncher.launch(request.filename)
            ExportKind.Diagnostics -> zipLauncher.launch(request.filename)
        }
    }
    LaunchedEffect(importRequest) {
        if (importRequest != null) {
            importLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        val topLevel = backStack.firstOrNull().toRootDestination()
        val showPrimaryNavigation = backStack.size == 1

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!expanded && showPrimaryNavigation) {
                    NavigationBar {
                        RootDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = topLevel == destination,
                                onClick = {
                                    backStack.clear()
                                    backStack.add(destination.route())
                                    viewModel.closeRecord()
                                    viewModel.closeConversation()
                                },
                                icon = { DestinationIcon(destination, selected = topLevel == destination) },
                                label = { Text(destination.label()) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (expanded && showPrimaryNavigation) {
                    NavigationRail {
                        Spacer(Modifier.height(12.dp))
                        RootDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = topLevel == destination,
                                onClick = {
                                    backStack.clear()
                                    backStack.add(destination.route())
                                    viewModel.closeRecord()
                                    viewModel.closeConversation()
                                },
                                icon = { DestinationIcon(destination, selected = topLevel == destination) },
                                label = { Text(destination.label()) },
                            )
                        }
                    }
                }
                NavDisplay(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        viewModel.closeRecord()
                        viewModel.closeConversation()
                    },
                    entryProvider = entryProvider {
                        entry<InboxRoute> {
                            InboxScreen(
                                viewModel = viewModel,
                                settings = settings,
                                expanded = expanded,
                                onOpenRecord = { id ->
                                    viewModel.openRecord(id)
                                    if (!expanded) backStack.add(RecordRoute(id))
                                },
                            )
                        }
                        entry<ChatsRoute> {
                            ChatsScreen(
                                viewModel = viewModel,
                                settings = settings,
                                expanded = expanded,
                                onOpenConversation = { id ->
                                    viewModel.openConversation(id)
                                    if (!expanded) backStack.add(ConversationRoute(id))
                                },
                            )
                        }
                        entry<SettingsRoute> {
                            SettingsScreen(
                                viewModel = viewModel,
                                settings = settings,
                                onOpenCapture = { backStack.add(CaptureSettingsRoute) },
                                onOpenPrivacy = { backStack.add(PrivacySettingsRoute) },
                                onOpenStorage = { backStack.add(StorageSettingsRoute) },
                                onOpenBackup = { backStack.add(BackupSettingsRoute) },
                                onOpenAdvanced = { backStack.add(AdvancedSettingsRoute) },
                                onOpenAbout = { backStack.add(AboutSettingsRoute) },
                            )
                        }
                        entry<CaptureSettingsRoute> {
                            CaptureSettingsScreen(viewModel, settings, onBack = { backStack.removeAt(backStack.lastIndex) })
                        }
                        entry<PrivacySettingsRoute> {
                            PrivacySettingsScreen(viewModel, settings, onBack = { backStack.removeAt(backStack.lastIndex) })
                        }
                        entry<StorageSettingsRoute> {
                            StorageSettingsScreen(viewModel, settings, onBack = { backStack.removeAt(backStack.lastIndex) })
                        }
                        entry<BackupSettingsRoute> {
                            BackupSettingsScreen(viewModel, onBack = { backStack.removeAt(backStack.lastIndex) })
                        }
                        entry<AdvancedSettingsRoute> {
                            AdvancedSettingsScreen(viewModel, onBack = { backStack.removeAt(backStack.lastIndex) })
                        }
                        entry<AboutSettingsRoute> {
                            AboutSettingsScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
                        }
                        entry<RecordRoute> { route ->
                            LaunchedEffect(route.id) { viewModel.openRecord(route.id) }
                            RecordDetailScreen(viewModel, onBack = { backStack.removeAt(backStack.lastIndex) })
                        }
                        entry<ConversationRoute> { route ->
                            LaunchedEffect(route.id) { viewModel.openConversation(route.id) }
                            ConversationDetailScreen(viewModel, onBack = { backStack.removeAt(backStack.lastIndex) })
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: RootDestination, selected: Boolean) {
    Icon(
        imageVector = when (destination) {
            RootDestination.Home -> if (selected) Icons.Default.Home else Icons.Outlined.Home
            RootDestination.Chats -> if (selected) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat
            RootDestination.Settings -> if (selected) Icons.Default.Settings else Icons.Outlined.Settings
        },
        contentDescription = null,
    )
}

@Composable
private fun RootDestination.label(): String = when (this) {
    RootDestination.Home -> stringResource(R.string.home)
    RootDestination.Chats -> stringResource(R.string.chats)
    RootDestination.Settings -> stringResource(R.string.settings)
}

private fun RootDestination.route(): AppRoute = when (this) {
    RootDestination.Home -> InboxRoute
    RootDestination.Chats -> ChatsRoute
    RootDestination.Settings -> SettingsRoute
}

private fun NavKey?.toRootDestination(): RootDestination = when (this) {
    ChatsRoute -> RootDestination.Chats
    SettingsRoute -> RootDestination.Settings
    else -> RootDestination.Home
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    expanded: Boolean,
    onOpenRecord: (Long) -> Unit,
) {
    val context = LocalContext.current
    val records = viewModel.records.collectAsLazyPagingItems()
    val accessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val search by viewModel.currentSearchText.collectAsStateWithLifecycle()
    val selectedPackage by viewModel.currentSelectedPackage.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.currentSelectedCategory.collectAsStateWithLifecycle()
    val selectedDate by viewModel.currentDateWindow.collectAsStateWithLifecycle()
    val appSources by viewModel.appSources.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val summary by viewModel.homeSummary.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FilterSheet(
                appSources,
                categories,
                selectedPackage,
                selectedCategory,
                selectedDate,
                viewModel::updateSelectedPackage,
                viewModel::updateSelectedCategory,
                viewModel::updateDateWindow,
                onClear = {
                    viewModel.updateSelectedPackage(null)
                    viewModel.updateSelectedCategory(null)
                    viewModel.updateDateWindow(DateWindow.AllTime)
                },
                onDelete = { showFilters = false; confirmDelete = true },
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_matching_title),
            message = stringResource(R.string.delete_matching_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; viewModel.deleteCurrentFilterSet() },
            onDismiss = { confirmDelete = false },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            InboxList(
                modifier = if (expanded) Modifier.weight(0.45f) else Modifier.fillMaxSize(),
                records = records,
                accessGranted = accessGranted,
                summary = summary,
                search = search,
                selectedPackage = selectedPackage,
                selectedCategory = selectedCategory,
                selectedDate = selectedDate,
                hidePreviews = settings.hideNotificationPreviews,
                onSearch = viewModel::updateSearchText,
                onDate = viewModel::updateDateWindow,
                onClearFilters = {
                    viewModel.updateSelectedPackage(null)
                    viewModel.updateSelectedCategory(null)
                    viewModel.updateDateWindow(DateWindow.AllTime)
                },
                onFilters = { showFilters = true },
                onOpenAccess = { openNotificationListenerSettings(context) },
                onOpenRecord = onOpenRecord,
            )
            if (expanded) {
                VerticalDivider(Modifier.fillMaxHeight())
                Box(Modifier.weight(0.55f).fillMaxHeight()) {
                    val record by viewModel.selectedRecord.collectAsStateWithLifecycle()
                    if (record == null) {
                        SelectionHint(Icons.Default.Archive, R.string.select_notification)
                    } else {
                        RecordDetailContent(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxList(
    modifier: Modifier,
    records: LazyPagingItems<NotificationRecordEntity>,
    accessGranted: Boolean,
    summary: HomeSummary,
    search: String,
    selectedPackage: String?,
    selectedCategory: String?,
    selectedDate: DateWindow,
    hidePreviews: Boolean,
    onSearch: (String) -> Unit,
    onDate: (DateWindow) -> Unit,
    onClearFilters: () -> Unit,
    onFilters: () -> Unit,
    onOpenAccess: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { HomeSummaryCard(summary, accessGranted, onOpenAccess) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.archive))
                Spacer(Modifier.weight(1f))
                val activeFilterCount = listOfNotNull(selectedPackage, selectedCategory).size +
                    if (selectedDate == DateWindow.AllTime) 0 else 1
                if (activeFilterCount > 0) {
                    TextButton(onClick = onClearFilters) {
                        Text(stringResource(R.string.clear))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearch,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_notifications)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
                IconButton(onClick = onFilters) {
                    val activeFilterCount = listOfNotNull(selectedPackage, selectedCategory).size +
                        if (selectedDate == DateWindow.AllTime) 0 else 1
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(
                            if (activeFilterCount == 0) R.string.filters else R.string.filters_active,
                            activeFilterCount,
                        ),
                    )
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DateWindow.Today, DateWindow.Last7Days, DateWindow.AllTime).forEach { window ->
                    FilterChip(
                        selected = selectedDate == window,
                        onClick = { onDate(window) },
                        label = { Text(window.quickLabel()) },
                    )
                }
            }
        }
        when {
            records.loadState.refresh is LoadState.Loading -> item { LoadingState() }
            records.loadState.refresh is LoadState.Error -> item {
                ErrorState(onRetry = records::retry)
            }
            records.itemCount == 0 -> item {
                EmptyState(Icons.Default.Archive, R.string.empty_inbox_title, R.string.empty_inbox_body)
            }
            else -> items(
                count = records.itemCount,
                key = { index -> records[index]?.id ?: "placeholder-$index" },
                contentType = { "notification" },
            ) { index ->
                records[index]?.let { record ->
                    NotificationRow(record, hidePreviews, onClick = { onOpenRecord(record.id) })
                }
            }
        }
        if (records.loadState.append is LoadState.Loading) item { LoadingState() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSummaryCard(
    summary: HomeSummary,
    accessGranted: Boolean,
    onOpenAccess: () -> Unit,
) {
    UtilityCard(
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (accessGranted) Icons.Default.CheckCircle else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = if (accessGranted) UtilityColors.Success else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (accessGranted) R.string.capture_ready else R.string.capture_needs_attention), fontWeight = FontWeight.SemiBold)
                }
                if (!accessGranted) {
                    TextButton(onClick = onOpenAccess) { Text(stringResource(R.string.manage)) }
                }
            }
            if (accessGranted) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryMetric(summary.savedToday.toString(), stringResource(R.string.saved_today), Modifier.weight(1f))
                    SummaryMetric(summary.activeCount.toString(), stringResource(R.string.active_now), Modifier.weight(1f))
                    summary.latestActivityAt?.let {
                        val activityTime = if (System.currentTimeMillis() - it < 90_000L) {
                            stringResource(R.string.now)
                        } else {
                            relativeTime(it)
                        }
                        SummaryMetric(activityTime, stringResource(R.string.latest_activity), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun NotificationRow(record: NotificationRecordEntity, hidePreviews: Boolean, onClick: () -> Unit) {
    UtilityCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        ListItem(
            leadingContent = { AppIcon(record.packageName, record.appLabel) },
            headlineContent = {
                Text(record.latestTitle ?: record.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Column {
                    Text(record.appLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (hidePreviews) stringResource(R.string.content_hidden) else record.latestBody ?: record.latestBigText ?: stringResource(R.string.no_preview),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(relativeTime(record.lastUpdatedAt), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    if (record.revisionCount > 1) Text(pluralStringResource(R.plurals.updates_count, record.revisionCount, record.revisionCount), style = MaterialTheme.typography.labelSmall)
                    if (record.isActive) Text(stringResource(R.string.active), color = UtilityColors.Success, style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    appSources: List<AppSource>,
    categories: List<String>,
    selectedPackage: String?,
    selectedCategory: String?,
    selectedDate: DateWindow,
    onPackage: (String?) -> Unit,
    onCategory: (String?) -> Unit,
    onDate: (DateWindow) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.filters))
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClear,
                    enabled = selectedPackage != null || selectedCategory != null || selectedDate != DateWindow.AllTime,
                ) { Text(stringResource(R.string.clear_all_filters)) }
            }
        }
        item {
            Text(stringResource(R.string.apps), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedPackage == null, { onPackage(null) }, { Text(stringResource(R.string.all_apps)) })
                appSources.forEach { app ->
                    FilterChip(selectedPackage == app.packageName, { onPackage(app.packageName) }, { Text(app.appLabel) })
                }
            }
        }
        item {
            Text(stringResource(R.string.categories), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedCategory == null, { onCategory(null) }, { Text(stringResource(R.string.all_categories)) })
                categories.forEach { category ->
                    FilterChip(selectedCategory == category, { onCategory(category) }, { Text(category) })
                }
            }
        }
        item {
            Text(stringResource(R.string.date_range), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateWindow.entries.forEach { date ->
                    FilterChip(selectedDate == date, { onDate(date) }, { Text(date.label()) })
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onDelete,
                enabled = selectedPackage != null || selectedDate != DateWindow.AllTime,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_matching))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    expanded: Boolean,
    onOpenConversation: (Long) -> Unit,
) {
    val conversations = viewModel.conversations.collectAsLazyPagingItems()
    val search by viewModel.currentChatSearchText.collectAsStateWithLifecycle()
    val selectedPackage by viewModel.currentChatSelectedPackage.collectAsStateWithLifecycle()
    val appSources by viewModel.appSources.collectAsStateWithLifecycle()
    var showAppFilter by rememberSaveable { mutableStateOf(false) }
    if (showAppFilter) {
        ModalBottomSheet(onDismissRequest = { showAppFilter = false }) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.all_apps)) },
                        modifier = Modifier.clickable {
                            viewModel.updateChatSelectedPackage(null)
                            showAppFilter = false
                        },
                    )
                }
                items(appSources, key = AppSource::packageName) { app ->
                    ListItem(
                        headlineContent = { Text(app.appLabel) },
                        supportingContent = { Text(app.packageName) },
                        modifier = Modifier.clickable {
                            viewModel.updateChatSelectedPackage(app.packageName)
                            showAppFilter = false
                        },
                    )
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = if (expanded) Modifier.weight(0.45f) else Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = viewModel::updateChatSearchText,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_chats)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showAppFilter = true }) {
                                Icon(Icons.Default.FilterList, stringResource(R.string.filter_chats_by_app))
                            }
                        },
                    )
                    selectedPackage?.let { packageName ->
                        val label = appSources.firstOrNull { it.packageName == packageName }?.appLabel ?: packageName
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.updateChatSelectedPackage(null) },
                            label = { Text(label) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    conversations.loadState.refresh is LoadState.Loading -> item { LoadingState() }
                    conversations.loadState.refresh is LoadState.Error -> item { ErrorState(conversations::retry) }
                    conversations.itemCount == 0 -> item { EmptyState(Icons.AutoMirrored.Filled.Chat, R.string.empty_chats_title, R.string.empty_chats_body) }
                    else -> items(
                        count = conversations.itemCount,
                        key = { index -> conversations[index]?.id ?: "chat-$index" },
                        contentType = { "conversation" },
                    ) { index ->
                        conversations[index]?.let { conversation ->
                            ConversationRow(
                                conversation,
                                settings.hideNotificationPreviews,
                                onOpen = { onOpenConversation(conversation.id) },
                                onPin = { viewModel.toggleConversationPinned(conversation) },
                            )
                        }
                    }
                }
            }
            if (expanded) {
                VerticalDivider(Modifier.fillMaxHeight())
                Box(Modifier.weight(0.55f)) {
                    val conversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
                    if (conversation == null) SelectionHint(Icons.AutoMirrored.Filled.Chat, R.string.select_conversation) else ConversationDetailContent(viewModel)
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    hidePreviews: Boolean,
    onOpen: () -> Unit,
    onPin: () -> Unit,
) {
    UtilityCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surface,
        emphasized = conversation.isPinned,
    ) {
        ListItem(
            leadingContent = { AppIcon(conversation.packageName, conversation.appLabel) },
            headlineContent = { Text(conversation.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column {
                    Text(conversation.appLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (hidePreviews) stringResource(R.string.content_hidden) else conversation.latestPreview ?: stringResource(R.string.no_preview),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onPin) {
                        Icon(
                            if (conversation.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(if (conversation.isPinned) R.string.unpin else R.string.pin),
                        )
                    }
                    Text(relativeTime(conversation.latestActivityAt), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.notification_detail)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        RecordDetailContent(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailContent(viewModel: MainViewModel) {
    val record by viewModel.selectedRecord.collectAsStateWithLifecycle()
    val revisions by viewModel.selectedRevisions.collectAsStateWithLifecycle()
    val current = record ?: return LoadingState()
    var category by remember(current.id, current.category) { mutableStateOf(current.category) }
    var tags by remember(current.id, current.tags) { mutableStateOf(current.tags.joinToString(", ")) }
    var showEditor by rememberSaveable(current.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (showEditor) {
        ModalBottomSheet(onDismissRequest = { showEditor = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(stringResource(R.string.edit_metadata))
                Text(
                    stringResource(R.string.edit_metadata_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.category)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.tags)) }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        viewModel.saveRecordMetadata(category, tags)
                        showEditor = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save_changes)) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_notification_title),
            stringResource(R.string.delete_notification_body),
            stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; viewModel.deleteSelectedRecord() },
            onDismiss = { confirmDelete = false },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DetailSection(stringResource(R.string.saved_content), Icons.Default.Notifications) {
                Text(current.latestTitle ?: stringResource(R.string.no_title), style = MaterialTheme.typography.titleLarge)
                current.latestBody?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                current.latestBigText?.takeIf { it != current.latestBody }?.let { Text(it) }
            }
        }
        item {
            DetailSection(stringResource(R.string.overview), Icons.Default.Info) {
                DetailLine(stringResource(R.string.app), current.appLabel)
                DetailLine(stringResource(R.string.package_name), current.packageName, technical = true)
                DetailLine(stringResource(R.string.category), current.category)
                DetailLine(stringResource(R.string.posted), fullTime(current.lifecycleStartedAt), technical = true)
                DetailLine(stringResource(R.string.last_update), fullTime(current.lastUpdatedAt), technical = true)
                current.endedAt?.let { DetailLine(stringResource(R.string.removed), fullTime(it), technical = true) }
                current.conversationTitle?.let { DetailLine(stringResource(R.string.conversation), it) }
                current.senderName?.let { DetailLine(stringResource(R.string.sender), it) }
            }
        }
        item {
            DetailSection(stringResource(R.string.manage), Icons.Default.Category) {
                Text(stringResource(R.string.manage_notification_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.edit_metadata))
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.revision_history)) }
        items(revisions, key = NotificationRevisionEntity::id) { revision ->
            DetailSection(fullTime(revision.capturedAt), Icons.Default.Notifications) {
                revision.title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                revision.body?.let { Text(it) }
                if (revision.isRecovered) Text(stringResource(R.string.recovered), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationDetailScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val conversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(conversation?.displayTitle ?: stringResource(R.string.conversation)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        ConversationDetailContent(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationDetailContent(viewModel: MainViewModel) {
    val conversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val entries = viewModel.conversationEntries.collectAsLazyPagingItems()
    val current = conversation ?: return LoadingState()
    var rename by remember(current.id, current.manualTitle) { mutableStateOf(current.manualTitle.orEmpty()) }
    var showRename by rememberSaveable(current.id) { mutableStateOf(false) }
    if (showRename) {
        ModalBottomSheet(onDismissRequest = { showRename = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(stringResource(R.string.rename_conversation))
                Text(stringResource(R.string.rename_conversation_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(rename, { rename = it }, label = { Text(stringResource(R.string.conversation_name)) }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        viewModel.renameConversation(rename)
                        showRename = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save_changes)) }
                TextButton(
                    onClick = {
                        rename = ""
                        viewModel.renameConversation("")
                        showRename = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.use_automatic_name)) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DetailSection(current.displayTitle, Icons.AutoMirrored.Filled.Chat) {
                Text(current.appLabel, color = MaterialTheme.colorScheme.primary)
                Text(pluralStringResource(R.plurals.activity_count, current.activityCount(), current.activityCount()))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { viewModel.toggleConversationPinned(current) }) {
                        Icon(if (current.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, stringResource(if (current.isPinned) R.string.unpin else R.string.pin))
                    }
                    OutlinedButton(onClick = { showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.rename))
                    }
                }
            }
        }
        when {
            entries.loadState.refresh is LoadState.Loading -> item { LoadingState() }
            entries.loadState.refresh is LoadState.Error -> item { ErrorState(entries::retry) }
            entries.itemCount == 0 -> item { EmptyState(Icons.AutoMirrored.Filled.Chat, R.string.empty_thread_title, R.string.empty_thread_body) }
            else -> items(
                count = entries.itemCount,
                key = { index -> entries[index]?.stableId ?: "entry-$index" },
                contentType = { "conversation-entry" },
            ) { index ->
                entries[index]?.let { ConversationEntry(it) }
            }
        }
    }
}

@Composable
private fun ConversationEntry(entry: ConversationEntryRow) {
    UtilityCard(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entry.senderName?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            Text(entry.text ?: stringResource(R.string.no_preview))
            Text(fullTime(entry.timestamp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LegacySettingsScreen(viewModel: MainViewModel, settings: AppSettings) {
    val accessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val storage by viewModel.storageBuckets.collectAsStateWithLifecycle()
    val metrics by viewModel.operationalSnapshot.collectAsStateWithLifecycle()
    val appSources by viewModel.appSources.collectAsStateWithLifecycle()
    var backupPassword by rememberSaveable { mutableStateOf("") }
    var restoreSettings by rememberSaveable { mutableStateOf(false) }
    var overridePackage by rememberSaveable { mutableStateOf("") }
    var overrideCategory by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshStorageUsage() }
    if (confirmClear) {
        ConfirmDialog(
            stringResource(R.string.clear_all_title),
            stringResource(R.string.clear_all_body),
            stringResource(R.string.clear_all),
            onConfirm = { confirmClear = false; viewModel.clearAllData() },
            onDismiss = { confirmClear = false },
        )
    }
    if (confirmReplace) {
        ConfirmDialog(
            stringResource(R.string.replace_import_title),
            stringResource(R.string.replace_import_body),
            stringResource(R.string.replace),
            onConfirm = {
                confirmReplace = false
                viewModel.requestImport(backupPassword, ImportMode.Replace, restoreSettings)
            },
            onDismiss = { confirmReplace = false },
        )
    }
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text(stringResource(R.string.settings)) })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AccessStatus(accessGranted) { openNotificationListenerSettings(context) } }
            item {
                DetailSection(stringResource(R.string.capture), Icons.Default.Notifications) {
                    Text(stringResource(R.string.capture_all_default), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(appSources, key = AppSource::packageName, contentType = { "capture-source" }) { app ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SettingSwitchRow(
                        title = app.appLabel,
                        checked = app.packageName !in settings.excludedPackages,
                        onChange = { included -> viewModel.setPackageExcluded(app.packageName, !included) },
                        detail = app.packageName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            item {
                DetailSection(stringResource(R.string.category_rules), Icons.Default.Category) {
                    Text(stringResource(R.string.category_rules_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        overridePackage,
                        { overridePackage = it },
                        label = { Text(stringResource(R.string.package_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        overrideCategory,
                        { overrideCategory = it },
                        label = { Text(stringResource(R.string.category)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            viewModel.saveAppCategoryOverride(overridePackage, overrideCategory)
                            overridePackage = ""
                            overrideCategory = ""
                        },
                        enabled = overridePackage.isNotBlank() && overrideCategory.isNotBlank(),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save_rule))
                    }
                    settings.appCategoryOverrides.toSortedMap().forEach { (packageName, category) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(category)
                                Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.removeAppCategoryOverride(packageName) }) {
                                Text(stringResource(R.string.remove))
                            }
                        }
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.privacy), Icons.Default.Shield) {
                    SettingSwitchRow(stringResource(R.string.hide_list_previews), settings.hideNotificationPreviews, viewModel::setHideNotificationPreviews)
                    SettingSwitchRow(stringResource(R.string.hide_recents), settings.hideRecentsPreview, viewModel::setHideRecentsPreview)
                    SettingSwitchRow(stringResource(R.string.block_screenshots), settings.blockScreenshots, viewModel::setBlockScreenshots)
                    SettingSwitchRow(stringResource(R.string.app_lock), settings.appLockEnabled, viewModel::setAppLockEnabled)
                    if (settings.appLockEnabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsStore.APP_LOCK_TIMEOUT_OPTIONS.forEach { minutes ->
                                FilterChip(
                                    selected = settings.appLockTimeoutMinutes == minutes,
                                    onClick = { viewModel.setAppLockTimeoutMinutes(minutes) },
                                    label = { Text(lockTimeoutLabel(minutes)) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.appearance), Icons.Default.Palette) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(settings.themeMode == mode, { viewModel.setThemeMode(mode) }, { Text(mode.label()) })
                        }
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.retention_storage), Icons.Default.Storage) {
                    Text(stringResource(R.string.retention_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MainViewModel.RETENTION_OPTIONS.forEach { days ->
                            FilterChip(settings.retentionDays == days, { viewModel.setRetentionDays(days) }, { Text(retentionLabel(days)) })
                        }
                    }
                    storage.forEach { bucket -> DetailLine(bucket.label, formatBytes(bucket.sizeBytes)) }
                    OutlinedButton(onClick = viewModel::refreshStorageUsage) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.refresh_storage))
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.backup_restore), Icons.Default.Backup) {
                    Text(stringResource(R.string.backup_warning), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.backup_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Button(onClick = { viewModel.requestEncryptedBackup(backupPassword) }) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.create_encrypted_backup))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.requestImport(backupPassword, ImportMode.Merge, restoreSettings) }) { Text(stringResource(R.string.import_merge)) }
                        TextButton(onClick = { confirmReplace = true }) { Text(stringResource(R.string.import_replace)) }
                    }
                    SettingSwitchRow(
                        stringResource(R.string.restore_settings),
                        restoreSettings,
                        { restoreSettings = it },
                    )
                    TextButton(onClick = viewModel::requestReadableExport) { Text(stringResource(R.string.export_readable_json)) }
                }
            }
            item {
                DetailSection(stringResource(R.string.advanced), Icons.Default.Tune) {
                    TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(stringResource(if (showAdvanced) R.string.hide else R.string.show)) }
                    if (showAdvanced) {
                        DetailLine(stringResource(R.string.listener_connections), metrics.listenerConnectedCount.toString())
                        DetailLine(stringResource(R.string.events_stored), metrics.postedEventCount.toString())
                        DetailLine(stringResource(R.string.messages_stored), metrics.messageCount.toString())
                        DetailLine(stringResource(R.string.duplicates_skipped), metrics.duplicateEventCount.toString())
                        DetailLine(stringResource(R.string.queue_high_water), metrics.queueHighWaterMark.toString())
                        DetailLine(stringResource(R.string.last_batch_latency), stringResource(R.string.milliseconds, metrics.lastBatchLatencyMillis))
                        DetailLine(stringResource(R.string.store_failures), metrics.storeFailureCount.toString())
                        OutlinedButton(onClick = viewModel::requestDiagnosticsExport) { Text(stringResource(R.string.export_diagnostics)) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = viewModel::clearLogs) { Text(stringResource(R.string.clear_logs)) }
                            TextButton(onClick = viewModel::clearCrashReports) { Text(stringResource(R.string.clear_crashes)) }
                        }
                        if (DemoDataSupport.isAvailable) OutlinedButton(onClick = viewModel::loadDemoData) { Text(stringResource(R.string.load_demo_data)) }
                        HorizontalDivider()
                        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_all), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.about), Icons.Default.Info) {
                    DetailLine(stringResource(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    TextButton(onClick = { openUrl(BuildConfig.PUBLIC_PRIVACY_URL, context) }) { Text(stringResource(R.string.privacy_policy)) }
                    TextButton(onClick = { openUrl(BuildConfig.PUBLIC_REPO_URL, context) }) { Text(stringResource(R.string.source_code)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    onOpenCapture: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val accessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { AccessStatus(accessGranted) { openNotificationListenerSettings(context) } }
            item { SectionTitle(stringResource(R.string.settings_for_your_data)) }
            item { SettingsShortcut(Icons.Default.Notifications, R.string.capture_rules, onOpenCapture) }
            item { SettingsShortcut(Icons.Default.Shield, R.string.privacy_security, onOpenPrivacy) }
            item { SettingsShortcut(Icons.Default.Storage, R.string.storage_retention, onOpenStorage) }
            item { SettingsShortcut(Icons.Default.Backup, R.string.backups_export, onOpenBackup) }
            item {
                DetailSection(stringResource(R.string.appearance), Icons.Default.Palette) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(settings.themeMode == mode, { viewModel.setThemeMode(mode) }, { Text(mode.label()) })
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.more)) }
            item { SettingsShortcut(Icons.Default.Tune, R.string.advanced_support, onOpenAdvanced) }
            item { SettingsShortcut(Icons.Default.Info, R.string.about, onOpenAbout) }
        }
    }
}

@Composable
private fun SettingsShortcut(icon: ImageVector, titleRes: Int, onClick: () -> Unit) {
    UtilityCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        ListItem(
            leadingContent = {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
                }
            },
            headlineContent = { Text(stringResource(titleRes), fontWeight = FontWeight.SemiBold) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CaptureSettingsScreen(viewModel: MainViewModel, settings: AppSettings, onBack: () -> Unit) {
    val accessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val appSources by viewModel.appSources.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var overridePackage by rememberSaveable { mutableStateOf("") }
    var overrideCategory by rememberSaveable { mutableStateOf("") }
    SettingsPage(R.string.capture_rules, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AccessStatus(accessGranted) { openNotificationListenerSettings(context) } }
            if (appSources.isNotEmpty()) item { SectionTitle(stringResource(R.string.included_apps)) }
            items(appSources, key = AppSource::packageName) { app ->
                UtilityCard(color = MaterialTheme.colorScheme.surface) {
                    SettingSwitchRow(
                        title = app.appLabel,
                        checked = app.packageName !in settings.excludedPackages,
                        onChange = { included -> viewModel.setPackageExcluded(app.packageName, !included) },
                        detail = app.packageName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            item {
                DetailSection(stringResource(R.string.category_rules), Icons.Default.Category) {
                    OutlinedTextField(overridePackage, { overridePackage = it }, label = { Text(stringResource(R.string.package_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(overrideCategory, { overrideCategory = it }, label = { Text(stringResource(R.string.category)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = {
                            viewModel.saveAppCategoryOverride(overridePackage, overrideCategory)
                            overridePackage = ""
                            overrideCategory = ""
                        },
                        enabled = overridePackage.isNotBlank() && overrideCategory.isNotBlank(),
                    ) { Text(stringResource(R.string.save_rule)) }
                    settings.appCategoryOverrides.toSortedMap().forEach { (packageName, category) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(category)
                                Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.removeAppCategoryOverride(packageName) }) { Text(stringResource(R.string.remove)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PrivacySettingsScreen(viewModel: MainViewModel, settings: AppSettings, onBack: () -> Unit) {
    SettingsPage(R.string.privacy_security, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailSection(stringResource(R.string.privacy), Icons.Default.Shield) {
                    SettingSwitchRow(stringResource(R.string.hide_list_previews), settings.hideNotificationPreviews, viewModel::setHideNotificationPreviews)
                    SettingSwitchRow(stringResource(R.string.hide_recents), settings.hideRecentsPreview, viewModel::setHideRecentsPreview)
                    SettingSwitchRow(stringResource(R.string.block_screenshots), settings.blockScreenshots, viewModel::setBlockScreenshots)
                    SettingSwitchRow(stringResource(R.string.app_lock), settings.appLockEnabled, viewModel::setAppLockEnabled)
                }
            }
            if (settings.appLockEnabled) item {
                DetailSection(stringResource(R.string.lock_after), Icons.Default.Lock) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsStore.APP_LOCK_TIMEOUT_OPTIONS.forEach { minutes ->
                            FilterChip(
                                selected = settings.appLockTimeoutMinutes == minutes,
                                onClick = { viewModel.setAppLockTimeoutMinutes(minutes) },
                                label = { Text(lockTimeoutLabel(minutes)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StorageSettingsScreen(viewModel: MainViewModel, settings: AppSettings, onBack: () -> Unit) {
    val storage by viewModel.storageBuckets.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshStorageUsage() }
    SettingsPage(R.string.storage_retention, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailSection(stringResource(R.string.retention_storage), Icons.Default.Storage) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MainViewModel.RETENTION_OPTIONS.forEach { days ->
                            FilterChip(settings.retentionDays == days, { viewModel.setRetentionDays(days) }, { Text(retentionLabel(days)) })
                        }
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.storage_used), Icons.Default.Storage) {
                    if (storage.isEmpty()) Text(stringResource(R.string.calculating_storage))
                    storage.forEach { bucket -> DetailLine(bucket.label, formatBytes(bucket.sizeBytes)) }
                    OutlinedButton(onClick = viewModel::refreshStorageUsage) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.refresh_storage))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BackupSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var restoreSettings by rememberSaveable { mutableStateOf(false) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    if (confirmReplace) {
        ConfirmDialog(
            stringResource(R.string.replace_import_title),
            stringResource(R.string.replace_import_body),
            stringResource(R.string.replace),
            onConfirm = { confirmReplace = false; viewModel.requestImport(password, ImportMode.Replace, restoreSettings) },
            onDismiss = { confirmReplace = false },
        )
    }
    SettingsPage(R.string.backups_export, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailSection(stringResource(R.string.backup_restore), Icons.Default.Backup) {
                    Text(stringResource(R.string.backup_warning), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.backup_password)) }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(onClick = { viewModel.requestEncryptedBackup(password) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.create_encrypted_backup))
                    }
                    Text(stringResource(R.string.restore_from_backup), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.requestImport(password, ImportMode.Merge, restoreSettings) }) { Text(stringResource(R.string.import_merge)) }
                        TextButton(onClick = { confirmReplace = true }) { Text(stringResource(R.string.import_replace)) }
                    }
                    SettingSwitchRow(stringResource(R.string.restore_settings), restoreSettings, { restoreSettings = it })
                    HorizontalDivider()
                    TextButton(onClick = viewModel::requestReadableExport) { Text(stringResource(R.string.export_readable_json)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val metrics by viewModel.operationalSnapshot.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (confirmClear) {
        ConfirmDialog(
            stringResource(R.string.clear_all_title),
            stringResource(R.string.clear_all_body),
            stringResource(R.string.clear_all),
            onConfirm = { confirmClear = false; viewModel.clearAllData() },
            onDismiss = { confirmClear = false },
        )
    }
    SettingsPage(R.string.advanced_support, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailSection(stringResource(R.string.capture_health), Icons.Default.Tune) {
                    DetailLine(stringResource(R.string.listener_connections), metrics.listenerConnectedCount.toString())
                    DetailLine(stringResource(R.string.events_stored), metrics.postedEventCount.toString())
                    DetailLine(stringResource(R.string.messages_stored), metrics.messageCount.toString())
                    DetailLine(stringResource(R.string.duplicates_skipped), metrics.duplicateEventCount.toString())
                    DetailLine(stringResource(R.string.queue_high_water), metrics.queueHighWaterMark.toString())
                    DetailLine(stringResource(R.string.last_batch_latency), stringResource(R.string.milliseconds, metrics.lastBatchLatencyMillis))
                    DetailLine(stringResource(R.string.store_failures), metrics.storeFailureCount.toString())
                }
            }
            item {
                DetailSection(stringResource(R.string.support_tools), Icons.Default.Tune) {
                    OutlinedButton(onClick = viewModel::requestDiagnosticsExport) { Text(stringResource(R.string.export_diagnostics)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::clearLogs) { Text(stringResource(R.string.clear_logs)) }
                        TextButton(onClick = viewModel::clearCrashReports) { Text(stringResource(R.string.clear_crashes)) }
                    }
                    if (DemoDataSupport.isAvailable) OutlinedButton(onClick = viewModel::loadDemoData) { Text(stringResource(R.string.load_demo_data)) }
                }
            }
            item {
                DetailSection(stringResource(R.string.danger_zone), Icons.Default.Delete) {
                    Text(stringResource(R.string.clear_all_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_all), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(R.string.about, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailSection(stringResource(R.string.about), Icons.Default.Info) {
                    DetailLine(stringResource(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    TextButton(onClick = { openUrl(BuildConfig.PUBLIC_PRIVACY_URL, context) }) { Text(stringResource(R.string.privacy_policy)) }
                    TextButton(onClick = { openUrl(BuildConfig.PUBLIC_REPO_URL, context) }) { Text(stringResource(R.string.source_code)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(titleRes: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(titleRes)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
        )
        content()
    }
}

@Composable
private fun AccessStatus(granted: Boolean, onOpen: () -> Unit) {
    val statusColor = if (granted) UtilityColors.Success else MaterialTheme.colorScheme.error
    UtilityCard(
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Notifications,
                        contentDescription = null,
                        tint = statusColor,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (granted) R.string.access_enabled else R.string.access_needed),
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onOpen) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (granted) R.string.manage else R.string.open_settings))
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String, fallbackLabel: String) {
    val context = LocalContext.current.applicationContext
    val cachedIcon = appIconCache.get(packageName)
    val knownUnavailable = unavailableAppIcons.get(packageName) == true
    val icon by produceState<ImageBitmap?>(
        initialValue = cachedIcon,
        key1 = packageName,
    ) {
        if (cachedIcon == null && !knownUnavailable) {
            value = withContext(Dispatchers.IO) { loadAppIcon(context, packageName) }
        }
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) {
            val resolvedIcon = icon
            if (resolvedIcon == null) {
                Text(fallbackLabel.trim().firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
            } else {
                Image(
                    bitmap = resolvedIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private const val APP_ICON_SIZE_PX = 128
private const val APP_ICON_CACHE_SIZE = 48

private val appIconCache = LruCache<String, ImageBitmap>(APP_ICON_CACHE_SIZE)
private val unavailableAppIcons = LruCache<String, Boolean>(APP_ICON_CACHE_SIZE)

private fun loadAppIcon(context: Context, packageName: String): ImageBitmap? {
    appIconCache.get(packageName)?.let { return it }
    if (unavailableAppIcons.get(packageName) == true) return null
    return runCatching {
        context.packageManager
            .getApplicationIcon(packageName)
            .toBitmap(APP_ICON_SIZE_PX, APP_ICON_SIZE_PX)
            .asImageBitmap()
            .also { appIconCache.put(packageName, it) }
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        unavailableAppIcons.put(packageName, true)
        null
    }
}

@Composable
private fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        content = content,
    )
}

@Composable
private fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        content = content,
    )
}

@Composable
private fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MaterialFilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
    )
}

@Composable
private fun UtilityCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color? = null,
    emphasized: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val visibleBorder = borderColor ?: if (emphasized) {
        if (LocalUtilityDarkTheme.current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    } else {
        null
    }
    Surface(
        modifier = modifier
            .shadow(2.dp, shape, clip = false)
            .then(
                if (visibleBorder == null) Modifier else Modifier.border(
                    width = if (emphasized) 2.dp else 1.dp,
                    color = visibleBorder,
                    shape = shape,
                ),
            ),
        shape = shape,
        color = color,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

@Composable
private fun DetailSection(
    title: String,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    UtilityCard(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun DetailLine(label: String, value: String, technical: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = if (technical) FontFamily.Monospace else FontFamily.SansSerif)
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SelectionHint(icon: ImageVector, messageRes: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(messageRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, titleRes: Int, bodyRes: Int) {
    UtilityCard(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(titleRes), fontWeight = FontWeight.SemiBold)
                Text(stringResource(bodyRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    UtilityCard(
        color = UtilityColors.ErrorContainer,
        borderColor = UtilityColors.Error,
        emphasized = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.load_failed), color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DateWindow.label(): String = when (this) {
    DateWindow.AllTime -> stringResource(R.string.all_time)
    DateWindow.Today -> stringResource(R.string.today)
    DateWindow.LastDay -> stringResource(R.string.last_day)
    DateWindow.Last7Days -> stringResource(R.string.last_7_days)
    DateWindow.Last30Days -> stringResource(R.string.last_30_days)
    DateWindow.Last90Days -> stringResource(R.string.last_90_days)
}

@Composable
private fun DateWindow.quickLabel(): String = when (this) {
    DateWindow.Today -> stringResource(R.string.today)
    DateWindow.Last7Days -> stringResource(R.string.last_7_days_short)
    DateWindow.AllTime -> stringResource(R.string.all_time)
    else -> label()
}

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> stringResource(R.string.follow_system)
    ThemeMode.Light -> stringResource(R.string.light)
    ThemeMode.Dark -> stringResource(R.string.dark)
}

@Composable
private fun retentionLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.keep_until_deleted)
    else -> pluralStringResource(R.plurals.days_count, days, days)
}

@Composable
private fun lockTimeoutLabel(minutes: Int): String = when (minutes) {
    0 -> stringResource(R.string.immediately)
    1 -> stringResource(R.string.one_minute)
    else -> pluralStringResource(R.plurals.minutes_count, minutes, minutes)
}

private fun relativeTime(timestamp: Long): String = DateUtils.getRelativeTimeSpanString(
    timestamp,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE,
).toString()

private fun fullTime(timestamp: Long): String = java.text.DateFormat.getDateTimeInstance(
    java.text.DateFormat.MEDIUM,
    java.text.DateFormat.SHORT,
).format(java.util.Date(timestamp))

private fun formatBytes(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", size / (1024f * 1024f))
}

private fun applyWindowPrivacy(activity: Activity, settings: AppSettings) {
    if (settings.blockScreenshots) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.setRecentsScreenshotEnabled(!settings.hideRecentsPreview)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        activity.window.decorView.setContentSensitivity(View.CONTENT_SENSITIVITY_SENSITIVE)
    }
}

private fun hasNotificationListenerAccess(context: Context): Boolean {
    val component = ComponentName(context, NotificationCaptureService::class.java)
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        .orEmpty()
        .split(':')
        .any { ComponentName.unflattenFromString(it) == component }
}

private fun openNotificationListenerSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.recoverCatching {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openUrl(url: String, context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
