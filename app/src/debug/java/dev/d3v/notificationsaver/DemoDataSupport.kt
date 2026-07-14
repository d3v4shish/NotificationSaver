package dev.d3v.notificationsaver

import java.time.Instant
import java.time.temporal.ChronoUnit

object DemoDataSupport {
    const val isAvailable: Boolean = true

    suspend fun seed(app: NotificationSaverApp) {
        val now = Instant.now()
        val records = listOf(
            demoNotification(
                sourceKey = "demo-whatsapp-family-1",
                packageName = "com.whatsapp",
                appLabel = "WhatsApp",
                title = "Weekend Trip",
                body = "Alice: Tickets are booked for Sunday morning.",
                conversationTitle = "Weekend Trip",
                senderName = "Alice",
                category = "Messages",
                postedAt = now.minus(2, ChronoUnit.HOURS).toEpochMilli(),
                lastSeenAt = now.minus(90, ChronoUnit.MINUTES).toEpochMilli(),
                importance = 3,
            ),
            demoNotification(
                sourceKey = "demo-whatsapp-family-2",
                packageName = "com.whatsapp",
                appLabel = "WhatsApp",
                title = "Weekend Trip",
                body = "Bob: I'll bring snacks and chargers.",
                conversationTitle = "Weekend Trip",
                senderName = "Bob",
                category = "Messages",
                postedAt = now.minus(80, ChronoUnit.MINUTES).toEpochMilli(),
                lastSeenAt = now.minus(70, ChronoUnit.MINUTES).toEpochMilli(),
                importance = 3,
            ),
            demoNotification(
                sourceKey = "demo-instagram-1",
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                title = "Avery",
                body = "Can you review the reel cover before lunch?",
                conversationTitle = null,
                senderName = "Avery",
                category = "Social",
                postedAt = now.minus(40, ChronoUnit.MINUTES).toEpochMilli(),
                lastSeenAt = now.minus(35, ChronoUnit.MINUTES).toEpochMilli(),
                importance = 2,
            ),
            demoNotification(
                sourceKey = "demo-telegram-1",
                packageName = "org.telegram.messenger",
                appLabel = "Telegram",
                title = "Launch Team",
                body = "Reminder: QA freeze at 5 PM.",
                conversationTitle = "Launch Team",
                senderName = "Nina",
                category = "Messages",
                postedAt = now.minus(25, ChronoUnit.MINUTES).toEpochMilli(),
                lastSeenAt = now.minus(20, ChronoUnit.MINUTES).toEpochMilli(),
                importance = 4,
            ),
            demoNotification(
                sourceKey = "demo-gmail-1",
                packageName = "com.google.android.gm",
                appLabel = "Gmail",
                title = "Release checklist approved",
                body = "Your release checklist was approved by QA.",
                conversationTitle = null,
                senderName = null,
                category = "Email",
                postedAt = now.minus(3, ChronoUnit.HOURS).toEpochMilli(),
                lastSeenAt = now.minus(3, ChronoUnit.HOURS).toEpochMilli(),
                importance = 1,
            ),
            demoNotification(
                sourceKey = "demo-bank-1",
                packageName = "com.bank.app",
                appLabel = "Acme Bank",
                title = "UPI payment received",
                body = "INR 1,250 received from Jordan.",
                conversationTitle = null,
                senderName = null,
                category = "Finance",
                postedAt = now.minus(6, ChronoUnit.HOURS).toEpochMilli(),
                lastSeenAt = now.minus(6, ChronoUnit.HOURS).toEpochMilli(),
                importance = 2,
            ),
            demoNotification(
                sourceKey = "demo-shopping-1",
                packageName = "com.amazon.mshop.android.shopping",
                appLabel = "Amazon Shopping",
                title = "Order shipped",
                body = "Your phone stand arrives tomorrow.",
                conversationTitle = null,
                senderName = null,
                category = "Shopping",
                postedAt = now.minus(12, ChronoUnit.HOURS).toEpochMilli(),
                lastSeenAt = now.minus(12, ChronoUnit.HOURS).toEpochMilli(),
                importance = 1,
            ),
            demoNotification(
                sourceKey = "demo-whatsapp-work-1",
                packageName = "com.whatsapp.w4b",
                appLabel = "WhatsApp Business",
                title = "Studio Client",
                body = "Please send the updated estimate today.",
                conversationTitle = "Studio Client",
                senderName = "Mira",
                category = "Messages",
                postedAt = now.minus(10, ChronoUnit.MINUTES).toEpochMilli(),
                lastSeenAt = now.minus(5, ChronoUnit.MINUTES).toEpochMilli(),
                importance = 5,
                manualThreadLabel = "Studio Client",
            ),
        )

        app.repository.replaceNotificationsForDemo(records)
        app.settingsStore.reset(logAction = false)
        app.settingsStore.markOnboardingCompleted()
        app.settingsStore.pinThread(buildThreadStableId("com.whatsapp", "Weekend Trip"))
        app.settingsStore.pinThread(buildThreadStableId("com.whatsapp.w4b", "Studio Client"))
        app.settingsStore.setThemeMode(ThemeMode.Light)
    }

    private fun demoNotification(
        sourceKey: String,
        packageName: String,
        appLabel: String,
        title: String,
        body: String,
        conversationTitle: String?,
        senderName: String?,
        category: String,
        postedAt: Long,
        lastSeenAt: Long,
        importance: Int,
        manualThreadLabel: String? = null,
    ): NotificationEntity {
        return NotificationEntity(
            sourceKey = sourceKey,
            packageName = packageName,
            appLabel = appLabel,
            notificationId = sourceKey.hashCode(),
            notificationTag = null,
            postedAt = postedAt,
            lastSeenAt = lastSeenAt,
            removedAt = null,
            title = title,
            body = body,
            bigText = null,
            category = category,
            tags = emptyList(),
            manualThreadLabel = manualThreadLabel,
            threadKey = conversationTitle ?: title,
            senderName = senderName,
            conversationTitle = conversationTitle,
            importance = importance,
            extrasJson = """{"demo":true}""",
            isRemoved = false,
        )
    }
}
