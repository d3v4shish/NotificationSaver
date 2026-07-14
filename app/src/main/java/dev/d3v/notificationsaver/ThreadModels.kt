package dev.d3v.notificationsaver

private const val THREAD_ID_SEPARATOR = "\u001F"

data class ThreadSelection(
    val packageName: String,
    val resolvedThreadId: String,
) {
    val stableId: String
        get() = buildThreadStableId(packageName, resolvedThreadId)
}

data class ThreadSummary(
    val packageName: String,
    val appLabel: String,
    val resolvedThreadId: String,
    val displayTitle: String,
    val latestPreview: String?,
    val latestTimestamp: Long,
    val totalCount: Int,
    val recentCount: Int,
    val maxImportance: Int?,
    val isPinned: Boolean,
) {
    val stableId: String
        get() = buildThreadStableId(packageName, resolvedThreadId)
}

enum class MessagingThreadTab(
    val label: String,
    private val packageNames: Set<String>,
) {
    WhatsApp(
        label = "WhatsApp",
        packageNames = setOf("com.whatsapp", "com.whatsapp.w4b"),
    ),
    Instagram(
        label = "Instagram",
        packageNames = setOf("com.instagram.android", "com.instagram.lite"),
    ),
    Telegram(
        label = "Telegram",
        packageNames = setOf("org.telegram.messenger", "org.thunderdog.challegram"),
    );

    fun supportsPackage(packageName: String): Boolean {
        return packageName in packageNames
    }

    companion object {
        fun fromPackageName(packageName: String): MessagingThreadTab? {
            return entries.firstOrNull { it.supportsPackage(packageName) }
        }
    }
}

data class ThreadSummaryRow(
    val packageName: String,
    val appLabel: String,
    val resolvedThreadId: String,
    val displayTitle: String,
    val latestPreview: String?,
    val latestTimestamp: Long,
    val totalCount: Int,
    val recentCount: Int,
    val maxImportance: Int,
)

fun buildThreadStableId(packageName: String, resolvedThreadId: String): String {
    return packageName + THREAD_ID_SEPARATOR + resolvedThreadId
}

fun NotificationEntity.resolvedThreadId(): String? {
    return manualThreadLabel.trimmedOrNull()
        ?: conversationTitle.trimmedOrNull()
        ?: senderName.trimmedOrNull()
        ?: threadKey.trimmedOrNull()
}

fun NotificationEntity.resolvedThreadDisplayTitle(): String {
    return manualThreadLabel.trimmedOrNull()
        ?: conversationTitle.trimmedOrNull()
        ?: senderName.trimmedOrNull()
        ?: threadKey.trimmedOrNull()
        ?: title.trimmedOrNull()
        ?: appLabel
}

fun suggestedThreadComparator(): Comparator<ThreadSummary> {
    return compareByDescending<ThreadSummary> { it.recentCount }
        .thenByDescending { it.latestTimestamp }
        .thenByDescending { it.maxImportance ?: Int.MIN_VALUE }
        .thenBy { it.displayTitle.lowercase() }
}

fun List<ThreadSummary>.activeMessagingThreadTabs(): List<MessagingThreadTab> {
    return MessagingThreadTab.entries.filter { tab ->
        any { summary -> tab.supportsPackage(summary.packageName) }
    }
}

fun List<ThreadSummary>.threadsForMessagingTab(tab: MessagingThreadTab): List<ThreadSummary> {
    return filter { summary -> tab.supportsPackage(summary.packageName) }
        .sortedWith(
            compareByDescending<ThreadSummary> { it.latestTimestamp }
                .thenBy { it.displayTitle.lowercase() }
                .thenBy { it.packageName },
        )
}

fun String?.trimmedOrNull(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }
}
