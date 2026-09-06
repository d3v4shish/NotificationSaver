package dev.d3v.notificationsaver

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

class NotificationSaverApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val logger: AppLogger by lazy {
        AppLogger(
            logFile = File(filesDir, "logs/app.log"),
            scope = applicationScope,
        )
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(this, applicationScope, logger)
    }

    val operationalMetrics: OperationalMetricsStore by lazy {
        OperationalMetricsStore(this, applicationScope, logger)
    }

    val crashReporter: AppCrashReporter by lazy {
        AppCrashReporter(
            metricsStore = operationalMetrics,
            logger = logger,
        )
    }

    val database: NotificationDatabase by lazy {
        NotificationDatabase.build(this)
    }

    val notificationParser: NotificationParser by lazy {
        NotificationParser(this, settingsStore)
    }

    val repository: NotificationRepository by lazy {
        NotificationRepository(
            context = this,
            database = database,
            notificationDao = database.notificationDao(),
            settingsStore = settingsStore,
            logger = logger,
            operationalMetrics = operationalMetrics,
            parser = notificationParser,
        )
    }

    val notificationIngestor: NotificationIngestor by lazy {
        NotificationIngestor(
            scope = applicationScope,
            repository = repository,
            operationalMetrics = operationalMetrics,
            logger = logger,
        )
    }

    val backupManager: BackupManager by lazy {
        BackupManager(
            context = this,
            repository = repository,
            settingsStore = settingsStore,
            operationalMetrics = operationalMetrics,
            logger = logger,
        )
    }

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        operationalMetrics.recordAppStart()
        logger.info("NotificationSaverApp", "Application started")
        applicationScope.launch {
            repository.migrateLegacyPins()
            repository.runRetentionCleanupNow()
        }
        applicationScope.launch {
            settingsStore.state
                .map { settings -> settings.retentionDays }
                .distinctUntilChanged()
                .collect { days -> RetentionScheduler.sync(this@NotificationSaverApp, days) }
        }
    }
}
