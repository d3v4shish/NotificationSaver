package dev.d3v.notificationsaver

object DemoDataSupport {
    const val isAvailable: Boolean = true

    suspend fun seed(app: NotificationSaverApp) {
        val now = System.currentTimeMillis()
        val conversationSpecs = listOf(
            Triple("com.whatsapp", "WhatsApp", "Weekend Trip"),
            Triple("com.whatsapp.w4b", "WhatsApp Business", "Studio Client"),
            Triple("org.telegram.messenger", "Telegram", "Launch Team"),
            Triple("com.instagram.android", "Instagram", "Avery"),
        )
        val conversations = conversationSpecs.mapIndexed { index, (packageName, appLabel, title) ->
            ConversationEntity(
                id = index + 1L,
                portableId = "demo-conversation-$index",
                packageName = packageName,
                appLabel = appLabel,
                canonicalKey = "demo:${title.lowercase()}",
                identitySource = "demo",
                identityConfidence = 100,
                sourceTitle = title,
                manualTitle = null,
                latestPreview = null,
                latestActivityAt = now - index * 15L * 60_000L,
                recordCount = 0,
                messageCount = 0,
                isPinned = index < 2,
            )
        }
        val specs = listOf(
            DemoSpec(1, "com.whatsapp", "WhatsApp", "Weekend Trip", "Alice", "Tickets are booked for Sunday morning.", 120),
            DemoSpec(1, "com.whatsapp", "WhatsApp", "Weekend Trip", "Bob", "I'll bring snacks and chargers.", 80),
            DemoSpec(2, "com.whatsapp.w4b", "WhatsApp Business", "Studio Client", "Mira", "Please send the updated estimate today.", 10),
            DemoSpec(3, "org.telegram.messenger", "Telegram", "Launch Team", "Nina", "Reminder: QA freeze at 5 PM.", 25),
            DemoSpec(4, "com.instagram.android", "Instagram", "Avery", "Avery", "Can you review the reel cover before lunch?", 40),
            DemoSpec(null, "com.google.android.gm", "Gmail", "Release checklist approved", null, "QA approved the release checklist.", 180),
            DemoSpec(null, "com.bank.app", "Acme Bank", "UPI payment received", null, "INR 1,250 received from Jordan.", 360),
        )
        val records = specs.mapIndexed { index, spec ->
            val timestamp = now - spec.minutesAgo * 60_000L
            NotificationRecordEntity(
                id = index + 1L,
                portableId = "demo-record-$index",
                systemKey = "demo-key-$index",
                packageName = spec.packageName,
                appLabel = spec.appLabel,
                notificationId = index,
                notificationTag = null,
                userId = 0,
                channelId = "demo",
                groupKey = null,
                shortcutId = spec.conversationId?.let { "demo-$it" },
                locusId = null,
                lifecycleStartedAt = timestamp,
                lastUpdatedAt = timestamp,
                endedAt = null,
                endReason = null,
                latestTitle = spec.title,
                latestBody = spec.body,
                latestBigText = null,
                latestSubText = null,
                senderName = spec.sender,
                conversationTitle = spec.conversationId?.let { spec.title },
                category = if (spec.conversationId == null) "General" else "Messages",
                tags = emptyList(),
                importance = 3,
                visibility = 0,
                notificationFlags = 0,
                revisionCount = 1,
                conversationId = spec.conversationId?.toLong(),
                isActive = false,
                isGroupSummary = false,
                wasRecovered = false,
            )
        }
        val revisions = records.map { record ->
            NotificationRevisionEntity(
                id = record.id,
                portableId = "demo-revision-${record.id}",
                recordId = record.id,
                contentFingerprint = "demo-${record.id}",
                capturedAt = record.lastUpdatedAt,
                sourcePostedAt = record.lifecycleStartedAt,
                title = record.latestTitle,
                body = record.latestBody,
                bigText = null,
                subText = null,
                textLines = emptyList(),
                senderName = record.senderName,
                conversationTitle = record.conversationTitle,
                category = record.category,
                importance = record.importance,
                extrasJson = "{\"demo\":true}",
                isRecovered = false,
            )
        }
        val messages = records.filter { it.conversationId != null }.map { record ->
            ChatMessageEntity(
                id = record.id,
                portableId = "demo-message-${record.id}",
                conversationId = requireNotNull(record.conversationId),
                recordId = record.id,
                revisionId = record.id,
                messageFingerprint = "demo-${record.id}",
                senderKey = null,
                senderName = record.senderName,
                text = record.latestBody,
                sourceTimestamp = record.lastUpdatedAt,
                capturedAt = record.lastUpdatedAt,
                dataMimeType = null,
                dataUri = null,
                isHistoric = false,
            )
        }
        app.repository.replaceAllForImport(conversations, records, revisions, messages)
        app.settingsStore.markOnboardingCompleted()
        app.settingsStore.setThemeMode(ThemeMode.Light)
    }

    private data class DemoSpec(
        val conversationId: Int?,
        val packageName: String,
        val appLabel: String,
        val title: String,
        val sender: String?,
        val body: String,
        val minutesAgo: Int,
    )
}
