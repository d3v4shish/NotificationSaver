package dev.d3v.notificationsaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadModelsTest {
    @Test
    fun resolvedThreadId_prefersManualLabelThenConversationTitleThenSenderThenThreadKey() {
        val withManualLabel = notification(
            manualThreadLabel = "Family",
            conversationTitle = "Weekend Trip",
            threadKey = "WhatsApp Group",
            senderName = "Alice",
        )
        val withConversationTitleOnly = notification(
            manualThreadLabel = null,
            conversationTitle = "Weekend Trip",
            threadKey = "WhatsApp Group",
            senderName = "Alice",
        )
        val withSenderOnly = notification(
            manualThreadLabel = null,
            conversationTitle = null,
            threadKey = null,
            senderName = "Alice",
        )
        val withThreadKeyOnly = notification(
            manualThreadLabel = null,
            conversationTitle = null,
            threadKey = "WhatsApp Group",
            senderName = null,
        )
        val withoutThreadIdentity = notification(
            manualThreadLabel = null,
            conversationTitle = null,
            threadKey = null,
            senderName = null,
        )

        assertEquals("Family", withManualLabel.resolvedThreadId())
        assertEquals("Weekend Trip", withConversationTitleOnly.resolvedThreadId())
        assertEquals("Alice", withSenderOnly.resolvedThreadId())
        assertEquals("WhatsApp Group", withThreadKeyOnly.resolvedThreadId())
        assertNull(withoutThreadIdentity.resolvedThreadId())
    }

    @Test
    fun resolvedThreadDisplayTitle_prefersSenderBeforeThreadKeyAndTitle() {
        val senderBackedNotification = notification(
            manualThreadLabel = null,
            conversationTitle = null,
            threadKey = "3 new messages",
            senderName = "Bob",
            title = "3 new messages",
            appLabel = "WhatsApp",
        )
        val conversationBackedNotification = notification(
            manualThreadLabel = null,
            conversationTitle = "Weekend Trip",
            threadKey = "Trip Thread",
            senderName = "Bob",
            title = "3 new messages",
            appLabel = "WhatsApp",
        )

        assertEquals("Bob", senderBackedNotification.resolvedThreadDisplayTitle())
        assertEquals("Weekend Trip", conversationBackedNotification.resolvedThreadDisplayTitle())
    }

    @Test
    fun suggestedThreadComparator_ordersByRecentCountThenTimestampThenImportance() {
        val quietButNewer = threadSummary(
            resolvedThreadId = "quiet",
            recentCount = 1,
            latestTimestamp = 300,
            maxImportance = 5,
        )
        val busy = threadSummary(
            resolvedThreadId = "busy",
            recentCount = 4,
            latestTimestamp = 100,
            maxImportance = 1,
        )
        val equallyBusyButHigherImportance = threadSummary(
            resolvedThreadId = "important",
            recentCount = 4,
            latestTimestamp = 100,
            maxImportance = 3,
        )

        val ordered = listOf(quietButNewer, busy, equallyBusyButHigherImportance)
            .sortedWith(suggestedThreadComparator())

        assertEquals(
            listOf("important", "busy", "quiet"),
            ordered.map { it.resolvedThreadId },
        )
    }

    @Test
    fun activeMessagingThreadTabs_onlyIncludesSupportedApps_inFixedOrder() {
        val whatsappBusiness = threadSummary(
            packageName = "com.whatsapp.w4b",
            appLabel = "WhatsApp Business",
            resolvedThreadId = "work",
            latestTimestamp = 150,
        )
        val instagram = threadSummary(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            resolvedThreadId = "alex",
            latestTimestamp = 300,
        )
        val signal = threadSummary(
            packageName = "org.signal",
            appLabel = "Signal",
            resolvedThreadId = "friends",
            latestTimestamp = 200,
        )
        val telegramX = threadSummary(
            packageName = "org.thunderdog.challegram",
            appLabel = "Telegram X",
            resolvedThreadId = "team",
            latestTimestamp = 100,
        )

        val activeTabs = listOf(signal, telegramX, instagram, whatsappBusiness).activeMessagingThreadTabs()

        assertEquals(
            listOf(
                MessagingThreadTab.WhatsApp,
                MessagingThreadTab.Instagram,
                MessagingThreadTab.Telegram,
            ),
            activeTabs,
        )
    }

    @Test
    fun threadsForMessagingTab_includesPackageVariants_andSortsByLatestActivity() {
        val olderWhatsApp = threadSummary(
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            resolvedThreadId = "family",
            latestTimestamp = 150,
        )
        val newerWhatsAppBusiness = threadSummary(
            packageName = "com.whatsapp.w4b",
            appLabel = "WhatsApp Business",
            resolvedThreadId = "work",
            latestTimestamp = 300,
        )
        val telegram = threadSummary(
            packageName = "org.telegram.messenger",
            appLabel = "Telegram",
            resolvedThreadId = "friends",
            latestTimestamp = 200,
        )

        val threads = listOf(olderWhatsApp, telegram, newerWhatsAppBusiness)
            .threadsForMessagingTab(MessagingThreadTab.WhatsApp)

        assertEquals(
            listOf("work", "family"),
            threads.map { it.resolvedThreadId },
        )
        assertEquals(
            listOf("com.whatsapp.w4b", "com.whatsapp"),
            threads.map { it.packageName },
        )
    }

    private fun notification(
        manualThreadLabel: String?,
        threadKey: String?,
        senderName: String?,
        conversationTitle: String? = null,
        title: String? = "Title",
        appLabel: String = "App",
    ): NotificationEntity {
        return NotificationEntity(
            id = 1,
            sourceKey = "source",
            packageName = "dev.test",
            appLabel = appLabel,
            notificationId = 1,
            notificationTag = null,
            postedAt = 100,
            lastSeenAt = 200,
            removedAt = null,
            title = title,
            body = "Body",
            bigText = null,
            category = "Messages",
            tags = emptyList(),
            manualThreadLabel = manualThreadLabel,
            threadKey = threadKey,
            senderName = senderName,
            conversationTitle = conversationTitle,
            importance = 1,
            extrasJson = "{}",
            isRemoved = false,
        )
    }

    private fun threadSummary(
        packageName: String = "dev.test",
        appLabel: String = "App",
        resolvedThreadId: String,
        recentCount: Int = 1,
        latestTimestamp: Long,
        maxImportance: Int = 1,
    ): ThreadSummary {
        return ThreadSummary(
            packageName = packageName,
            appLabel = appLabel,
            resolvedThreadId = resolvedThreadId,
            displayTitle = resolvedThreadId,
            latestPreview = "Preview",
            latestTimestamp = latestTimestamp,
            totalCount = 3,
            recentCount = recentCount,
            maxImportance = maxImportance,
            isPinned = false,
        )
    }
}
