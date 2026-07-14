package dev.d3v.notificationsaver

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        SettingsStore(this, logger)
    }

    val operationalMetrics: OperationalMetricsStore by lazy {
        OperationalMetricsStore(this, logger)
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

    val repository: NotificationRepository by lazy {
        NotificationRepository(
            context = this,
            notificationDao = database.notificationDao(),
            settingsStore = settingsStore,
            logger = logger,
            operationalMetrics = operationalMetrics,
        )
    }

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        operationalMetrics.recordAppStart()
        logger.info("NotificationSaverApp", "Application started")
        applicationScope.launch {
            repository.runRetentionCleanupNow()
        }
    }
}
