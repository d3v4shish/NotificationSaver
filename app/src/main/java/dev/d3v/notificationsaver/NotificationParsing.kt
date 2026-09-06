package dev.d3v.notificationsaver

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

data class ParsedChatMessage(
    val fingerprint: String,
    val senderKey: String?,
    val senderName: String?,
    val text: String?,
    val timestamp: Long,
    val dataMimeType: String?,
    val dataUri: String?,
    val isHistoric: Boolean,
)

data class ConversationMatch(
    val canonicalKey: String,
    val displayTitle: String?,
    val identitySource: String,
    val confidence: Int,
)

data class ParsedNotification(
    val systemKey: String,
    val packageName: String,
    val appLabel: String,
    val notificationId: Int,
    val notificationTag: String?,
    val userId: Int,
    val channelId: String?,
    val groupKey: String?,
    val shortcutId: String?,
    val locusId: String?,
    val sourcePostedAt: Long,
    val capturedAt: Long,
    val title: String?,
    val body: String?,
    val bigText: String?,
    val subText: String?,
    val textLines: List<String>,
    val senderName: String?,
    val conversationTitle: String?,
    val category: String,
    val importance: Int?,
    val visibility: Int,
    val notificationFlags: Int,
    val isGroupSummary: Boolean,
    val isProgress: Boolean,
    val contentFingerprint: String,
    val extrasJson: String,
    val messages: List<ParsedChatMessage>,
    val conversation: ConversationMatch?,
)

class NotificationParser(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    private val appLabelCache = ConcurrentHashMap<String, String>()

    fun parse(
        sbn: StatusBarNotification,
        capturedAt: Long,
        rankingImportance: Int?,
    ): ParsedNotification {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY
        val conversationTitle = extras.charSequence(Notification.EXTRA_CONVERSATION_TITLE)
        val title = extras.charSequence(Notification.EXTRA_TITLE)
        val bodyText = extras.charSequence(Notification.EXTRA_TEXT)
        val bigText = extras.charSequence(Notification.EXTRA_BIG_TEXT)
        val subText = extras.charSequence(Notification.EXTRA_SUB_TEXT)
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString().trimmedOrNull() }
            .orEmpty()
        val messages = parseMessages(extras, capturedAt)
        val latestMessage = messages.maxByOrNull { it.timestamp }
        val senderName = latestMessage?.senderName.trimmedOrNull()
        val normalizedBody = latestMessage?.text.trimmedOrNull()
            ?: bodyText
            ?: bigText
            ?: textLines.lastOrNull()
        val category = inferCategory(
            packageName = sbn.packageName,
            notificationCategory = notification.category,
            title = title,
            body = normalizedBody,
        )
        val shortcutId = notification.shortcutId.trimmedOrNull()
        val locusId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            notification.locusId?.id.trimmedOrNull()
        } else {
            null
        }
        val groupKey = sbn.groupKey.trimmedOrNull()
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val isProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val parsedMessages = messages.mapIndexed { index, message ->
            message.copy(
                fingerprint = sha256(
                    listOf(
                        message.timestamp.toString(),
                        message.senderKey.orEmpty(),
                        message.senderName.orEmpty(),
                        message.text.orEmpty(),
                        message.dataMimeType.orEmpty(),
                        message.dataUri.orEmpty(),
                        message.isHistoric.toString(),
                        duplicateOrdinal(messages, index).toString(),
                    ).joinToString("\u001f"),
                ),
            )
        }
        val conversation = resolveConversation(
            notificationCategory = notification.category,
            shortcutId = shortcutId,
            locusId = locusId,
            groupKey = groupKey,
            notificationId = sbn.id,
            notificationTag = sbn.tag,
            conversationTitle = conversationTitle,
            senderName = senderName,
            title = title,
            hasStructuredMessages = parsedMessages.isNotEmpty(),
        )
        val progressFinished = if (isProgress) {
            val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
            max > 0 && progress >= max
        } else {
            false
        }
        val contentFingerprint = sha256(
            if (isProgress) {
                listOf(
                    title.orEmpty(),
                    bigText.orEmpty(),
                    category,
                    progressFinished.toString(),
                    notification.flags.toString(),
                ).joinToString("\u001f")
            } else {
                listOf(
                    title.orEmpty(),
                    normalizedBody.orEmpty(),
                    bigText.orEmpty(),
                    subText.orEmpty(),
                    textLines.joinToString("\u001e"),
                    conversationTitle.orEmpty(),
                    senderName.orEmpty(),
                    category,
                    parsedMessages.joinToString("\u001e") { it.fingerprint },
                    notification.flags.toString(),
                ).joinToString("\u001f")
            },
        )

        return ParsedNotification(
            systemKey = sbn.key ?: "${sbn.packageName}:${sbn.id}:${sbn.tag.orEmpty()}:${sbn.postTime}",
            packageName = sbn.packageName,
            appLabel = resolveAppLabel(sbn.packageName),
            notificationId = sbn.id,
            notificationTag = sbn.tag,
            userId = sbn.userId,
            channelId = notification.channelId.trimmedOrNull(),
            groupKey = groupKey,
            shortcutId = shortcutId,
            locusId = locusId,
            sourcePostedAt = sbn.postTime,
            capturedAt = capturedAt,
            title = title,
            body = normalizedBody,
            bigText = bigText,
            subText = subText,
            textLines = textLines,
            senderName = senderName,
            conversationTitle = conversationTitle,
            category = category,
            importance = rankingImportance ?: notification.priority,
            visibility = notification.visibility,
            notificationFlags = notification.flags,
            isGroupSummary = isGroupSummary,
            isProgress = isProgress,
            contentFingerprint = contentFingerprint,
            extrasJson = buildExtrasJson(
                title = title,
                body = bodyText,
                bigText = bigText,
                subText = subText,
                conversationTitle = conversationTitle,
                senderName = senderName,
                textLines = textLines,
                messageCount = parsedMessages.size,
                isProgress = isProgress,
            ),
            messages = parsedMessages,
            conversation = conversation,
        )
    }

    private fun Bundle.charSequence(key: String): String? {
        return getCharSequence(key)?.toString().trimmedOrNull()
    }

    private fun parseMessages(extras: Bundle, capturedAt: Long): List<ParsedChatMessage> {
        val current = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            .toMessages(isHistoric = false, capturedAt = capturedAt)
        val historic = extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES)
            .toMessages(isHistoric = true, capturedAt = capturedAt)
        return (historic + current).sortedWith(
            compareBy<ParsedChatMessage> { it.timestamp }
                .thenBy { it.senderName.orEmpty() }
                .thenBy { it.text.orEmpty() },
        )
    }

    private fun Array<Parcelable>?.toMessages(
        isHistoric: Boolean,
        capturedAt: Long,
    ): List<ParsedChatMessage> {
        if (this == null) return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return mapNotNull { (it as? Bundle)?.toFallbackMessage(isHistoric, capturedAt) }
        }
        return runCatching {
            Api30Messaging.getMessages(this).map { message ->
                val sender = Api28Messaging.sender(message)
                @Suppress("DEPRECATION")
                val legacySender = message.sender?.toString()
                ParsedChatMessage(
                    fingerprint = "",
                    senderKey = sender.first,
                    senderName = sender.second
                        ?: legacySender.trimmedOrNull(),
                    text = message.text?.toString().trimmedOrNull(),
                    timestamp = message.timestamp.takeIf { it > 0L } ?: capturedAt,
                    dataMimeType = message.dataMimeType.trimmedOrNull(),
                    dataUri = message.dataUri?.toString().trimmedOrNull(),
                    isHistoric = isHistoric,
                )
            }
        }.getOrElse {
            mapNotNull { (it as? Bundle)?.toFallbackMessage(isHistoric, capturedAt) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private object Api30Messaging {
        fun getMessages(bundleArray: Array<Parcelable>): List<Notification.MessagingStyle.Message> {
            return Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundleArray).toList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private object Api28Messaging {
        fun sender(message: Notification.MessagingStyle.Message): Pair<String?, String?> {
            val person = message.senderPerson
            return person?.key.trimmedOrNull() to person?.name?.toString().trimmedOrNull()
        }
    }

    private fun Bundle.toFallbackMessage(
        isHistoric: Boolean,
        capturedAt: Long,
    ): ParsedChatMessage? {
        val text = charSequence("text") ?: charSequence("mText")
        val sender = charSequence("sender") ?: charSequence("mSender")
        if (text == null && sender == null) return null
        return ParsedChatMessage(
            fingerprint = "",
            senderKey = null,
            senderName = sender,
            text = text,
            timestamp = getLong("time", getLong("mTimestamp", capturedAt)).takeIf { it > 0L }
                ?: capturedAt,
            dataMimeType = getString("type").trimmedOrNull(),
            dataUri = getParcelable<Parcelable>("uri")?.toString().trimmedOrNull(),
            isHistoric = isHistoric,
        )
    }

    private fun duplicateOrdinal(messages: List<ParsedChatMessage>, index: Int): Int {
        val target = messages[index]
        return messages.take(index).count { prior ->
            prior.timestamp == target.timestamp &&
                prior.senderKey == target.senderKey &&
                prior.senderName == target.senderName &&
                prior.text == target.text &&
                prior.dataMimeType == target.dataMimeType &&
                prior.dataUri == target.dataUri &&
                prior.isHistoric == target.isHistoric
        }
    }

    private fun resolveConversation(
        notificationCategory: String?,
        shortcutId: String?,
        locusId: String?,
        groupKey: String?,
        notificationId: Int,
        notificationTag: String?,
        conversationTitle: String?,
        senderName: String?,
        title: String?,
        hasStructuredMessages: Boolean,
    ): ConversationMatch? {
        shortcutId?.let {
            return ConversationMatch("shortcut:$it", conversationTitle ?: title ?: senderName, "shortcut", 100)
        }
        locusId?.let {
            return ConversationMatch("locus:$it", conversationTitle ?: title ?: senderName, "locus", 95)
        }
        val isMessage = hasStructuredMessages || notificationCategory == Notification.CATEGORY_MESSAGE
        if (!isMessage) return null

        val displayTitle = conversationTitle ?: title ?: senderName
        val stableNotificationPart = notificationTag.trimmedOrNull() ?: notificationId.toString()
        if (hasStructuredMessages) {
            val normalizedTitle = displayTitle.normalizedIdentityPart()
            return ConversationMatch(
                canonicalKey = "messaging:$stableNotificationPart:${normalizedTitle.orEmpty()}",
                displayTitle = displayTitle,
                identitySource = "messaging_style",
                confidence = if (normalizedTitle == null) 70 else 85,
            )
        }
        conversationTitle.normalizedIdentityPart()?.let {
            return ConversationMatch("title:$it", conversationTitle, "conversation_title", 70)
        }
        senderName.normalizedIdentityPart()?.let {
            return ConversationMatch("sender:$it", senderName, "sender", 55)
        }
        title.normalizedIdentityPart()?.let {
            return ConversationMatch("title:$it", title, "message_title", 45)
        }
        groupKey.normalizedIdentityPart()?.let {
            return ConversationMatch("group:$it", displayTitle, "group", 35)
        }
        return null
    }

    private fun resolveAppLabel(packageName: String): String {
        appLabelCache[packageName]?.let { return it }
        val label = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        appLabelCache[packageName] = label
        return label
    }

    private fun inferCategory(
        packageName: String,
        notificationCategory: String?,
        title: String?,
        body: String?,
    ): String {
        val settings = settingsStore.current()
        settings.appCategoryOverrides[packageName]?.trimmedOrNull()?.let { return it }
        val lowerPackage = packageName.lowercase()
        val lowerText = "${title.orEmpty()} ${body.orEmpty()}".lowercase()
        return when {
            notificationCategory == Notification.CATEGORY_CALL || "dialer" in lowerPackage -> "Calls"
            notificationCategory == Notification.CATEGORY_EMAIL ||
                "gmail" in lowerPackage || "outlook" in lowerPackage || "mail" in lowerPackage -> "Email"
            notificationCategory == Notification.CATEGORY_MESSAGE ||
                "whatsapp" in lowerPackage || "telegram" in lowerPackage ||
                "signal" in lowerPackage || "messag" in lowerPackage -> "Messages"
            "instagram" in lowerPackage || "facebook" in lowerPackage || "social" in lowerText -> "Social"
            "bank" in lowerText || "upi" in lowerText || "payment" in lowerText || "wallet" in lowerText -> "Finance"
            notificationCategory == Notification.CATEGORY_SYSTEM || "android" in lowerPackage -> "System"
            "order" in lowerText || "delivery" in lowerText || "shopping" in lowerText -> "Shopping"
            "travel" in lowerText || "flight" in lowerText || "cab" in lowerText -> "Travel"
            else -> settings.fallbackCategory.ifBlank { DEFAULT_FALLBACK_CATEGORY }
        }
    }

    private fun buildExtrasJson(
        title: String?,
        body: String?,
        bigText: String?,
        subText: String?,
        conversationTitle: String?,
        senderName: String?,
        textLines: List<String>,
        messageCount: Int,
        isProgress: Boolean,
    ): String {
        return JSONObject()
            .put("title", title)
            .put("text", body)
            .put("bigText", bigText)
            .put("subText", subText)
            .put("conversationTitle", conversationTitle)
            .put("senderName", senderName)
            .put("textLines", JSONArray(textLines))
            .put("messageCount", messageCount)
            .put("isProgress", isProgress)
            .toString()
    }
}

internal fun sha256(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun String?.normalizedIdentityPart(): String? {
    return trimmedOrNull()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
}
