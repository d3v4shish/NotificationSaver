package dev.d3v.notificationsaver

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val conversations: List<ConversationEntity>,
    val records: List<NotificationRecordEntity>,
    val revisions: List<NotificationRevisionEntity>,
    val messages: List<ChatMessageEntity>,
    val settings: AppSettings,
)

data class ImportSummary(
    val conversationCount: Int,
    val recordCount: Int,
    val revisionCount: Int,
    val messageCount: Int,
)

enum class ImportMode {
    Merge,
    Replace,
}

class BackupManager(
    private val context: Context,
    private val repository: NotificationRepository,
    private val settingsStore: SettingsStore,
    private val operationalMetrics: OperationalMetricsStore,
    private val logger: AppLogger,
) {
    suspend fun exportEncrypted(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        require(password.size >= MIN_PASSWORD_LENGTH) { "Backup password must be at least $MIN_PASSWORD_LENGTH characters" }
        val snapshot = repository.backupSnapshot()
        context.contentResolver.openOutputStream(uri, "w")?.use { raw ->
            writeEncrypted(snapshot, raw, password)
        } ?: error("Could not open backup destination")
        password.fill('\u0000')
        logger.info("BackupManager", "Exported encrypted backup")
    }

    suspend fun exportReadableJson(uri: Uri) = withContext(Dispatchers.IO) {
        val snapshot = repository.backupSnapshot()
        context.contentResolver.openOutputStream(uri, "w")?.buffered()?.use { output ->
            writeJson(snapshot, output)
        } ?: error("Could not open export destination")
        logger.info("BackupManager", "Exported readable notification JSON")
    }

    suspend fun import(
        uri: Uri,
        password: CharArray?,
        mode: ImportMode,
        restoreSettings: Boolean,
    ): ImportSummary = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = context.contentResolver.openInputStream(uri)?.use { raw ->
                readMaybeEncrypted(raw, password)
            } ?: error("Could not open backup source")
            when (mode) {
                ImportMode.Merge -> repository.mergeImportedRows(
                    snapshot.conversations,
                    snapshot.records,
                    snapshot.revisions,
                    snapshot.messages,
                )
                ImportMode.Replace -> repository.replaceAllForImport(
                    snapshot.conversations,
                    snapshot.records,
                    snapshot.revisions,
                    snapshot.messages,
                )
            }
            if (restoreSettings) settingsStore.applyImportedNonSecretSettings(snapshot.settings)
            password?.fill('\u0000')
            ImportSummary(
                conversationCount = snapshot.conversations.size,
                recordCount = snapshot.records.size,
                revisionCount = snapshot.revisions.size,
                messageCount = snapshot.messages.size,
            )
        }.onFailure { error ->
            operationalMetrics.recordExportFailure()
            logger.error("BackupManager", "Backup import failed", error)
        }.getOrThrow()
    }

    private fun writeEncrypted(snapshot: BackupSnapshot, output: OutputStream, password: CharArray) {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val data = DataOutputStream(BufferedOutputStream(output))
        data.write(MAGIC)
        data.writeInt(ENCRYPTED_FORMAT_VERSION)
        data.writeInt(PBKDF2_ITERATIONS)
        data.writeInt(salt.size)
        data.write(salt)
        data.writeInt(iv.size)
        data.write(iv)
        data.flush()
        CipherOutputStream(data, cipher).use { encrypted -> writeJson(snapshot, encrypted) }
    }

    private fun readMaybeEncrypted(input: InputStream, password: CharArray?): BackupSnapshot {
        val buffered = BufferedInputStream(input)
        buffered.mark(MAGIC.size + 1)
        val prefix = ByteArray(MAGIC.size)
        val read = buffered.read(prefix)
        buffered.reset()
        if (read == MAGIC.size && prefix.contentEquals(MAGIC)) {
            require(password != null && password.size >= MIN_PASSWORD_LENGTH) { "Password required for encrypted backup" }
            return readEncrypted(buffered, password)
        }
        return readJson(buffered)
    }

    private fun readEncrypted(input: InputStream, password: CharArray): BackupSnapshot {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also(data::readFully)
        require(magic.contentEquals(MAGIC)) { "Invalid backup header" }
        require(data.readInt() == ENCRYPTED_FORMAT_VERSION) { "Unsupported encrypted backup version" }
        val iterations = data.readInt()
        require(iterations in 100_000..1_000_000) { "Invalid key derivation parameters" }
        val saltSize = data.readInt()
        require(saltSize in 16..64) { "Invalid backup salt" }
        val salt = ByteArray(saltSize).also(data::readFully)
        val ivSize = data.readInt()
        require(ivSize in 12..32) { "Invalid backup IV" }
        val iv = ByteArray(ivSize).also(data::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return CipherInputStream(data, cipher).use(::readJson)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, AES_KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun writeJson(snapshot: BackupSnapshot, output: OutputStream) {
        val root = JSONObject()
            .put("format", FORMAT_NAME)
            .put("formatVersion", JSON_FORMAT_VERSION)
            .put("exportedAt", Instant.now().toString())
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("settings", snapshot.settings.toJson())
            .put("conversations", JSONArray(snapshot.conversations.map(ConversationEntity::toJson)))
            .put("records", JSONArray(snapshot.records.map(NotificationRecordEntity::toJson)))
            .put("revisions", JSONArray(snapshot.revisions.map(NotificationRevisionEntity::toJson)))
            .put("messages", JSONArray(snapshot.messages.map(ChatMessageEntity::toJson)))
        output.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(root.toString()) }
    }

    private fun readJson(input: InputStream): BackupSnapshot {
        val root = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        val isLegacy = !root.has("formatVersion") && root.has("notifications")
        if (isLegacy) return readLegacyJson(root)
        require(root.optString("format") == FORMAT_NAME) { "Not a Notification Saver backup" }
        require(root.optInt("formatVersion") in 1..JSON_FORMAT_VERSION) { "Unsupported backup version" }
        return BackupSnapshot(
            conversations = root.optJSONArray("conversations").toObjectList(::conversationFromJson),
            records = root.optJSONArray("records").toObjectList(::recordFromJson),
            revisions = root.optJSONArray("revisions").toObjectList(::revisionFromJson),
            messages = root.optJSONArray("messages").toObjectList(::messageFromJson),
            settings = settingsFromJson(root.optJSONObject("settings")),
        )
    }

    private fun readLegacyJson(root: JSONObject): BackupSnapshot {
        val conversations = LinkedHashMap<String, ConversationEntity>()
        val records = ArrayList<NotificationRecordEntity>()
        val revisions = ArrayList<NotificationRevisionEntity>()
        val notifications = root.optJSONArray("notifications") ?: JSONArray()
        for (index in 0 until notifications.length()) {
            val value = notifications.optJSONObject(index) ?: continue
            val packageName = value.optString("packageName", "unknown")
            val resolved = value.stringOrNull("manualThreadLabel")
                ?: value.stringOrNull("conversationTitle")
                ?: value.stringOrNull("senderName")
                ?: value.stringOrNull("threadKey")
            val conversation = resolved?.let { title ->
                val key = "$packageName\u001f$title"
                conversations.getOrPut(key) {
                    ConversationEntity(
                        id = conversations.size.toLong() + 1L,
                        portableId = "legacy-import-conversation:${sha256(key)}",
                        packageName = packageName,
                        appLabel = value.optString("appLabel", packageName),
                        canonicalKey = "legacy:$title",
                        identitySource = "legacy_import",
                        identityConfidence = 25,
                        sourceTitle = title,
                        manualTitle = value.stringOrNull("manualThreadLabel"),
                        latestPreview = value.stringOrNull("body") ?: value.stringOrNull("bigText"),
                        latestActivityAt = value.optLong("lastSeenAt", value.optLong("postedAt")),
                        recordCount = 0,
                        messageCount = 0,
                        isPinned = false,
                    )
                }
            }
            val legacyId = value.optLong("id", index.toLong() + 1L)
            val portableId = "legacy-import-record:$legacyId:${sha256(value.optString("sourceKey"))}"
            val record = NotificationRecordEntity(
                id = legacyId,
                portableId = portableId,
                systemKey = value.optString("sourceKey", portableId),
                packageName = packageName,
                appLabel = value.optString("appLabel", packageName),
                notificationId = value.optInt("notificationId"),
                notificationTag = value.stringOrNull("notificationTag"),
                userId = 0,
                channelId = null,
                groupKey = value.stringOrNull("threadKey"),
                shortcutId = null,
                locusId = null,
                lifecycleStartedAt = value.optLong("postedAt"),
                lastUpdatedAt = value.optLong("lastSeenAt", value.optLong("postedAt")),
                endedAt = value.longOrNull("removedAt"),
                endReason = null,
                latestTitle = value.stringOrNull("title"),
                latestBody = value.stringOrNull("body"),
                latestBigText = value.stringOrNull("bigText"),
                latestSubText = null,
                senderName = value.stringOrNull("senderName"),
                conversationTitle = value.stringOrNull("conversationTitle"),
                category = value.optString("category", DEFAULT_FALLBACK_CATEGORY),
                tags = value.optJSONArray("tags").toStringList(),
                importance = value.intOrNull("importance"),
                visibility = 0,
                notificationFlags = 0,
                revisionCount = 1,
                conversationId = conversation?.id,
                isActive = false,
                isGroupSummary = false,
                wasRecovered = true,
            )
            records += record
            revisions += NotificationRevisionEntity(
                id = legacyId,
                portableId = "legacy-import-revision:$legacyId:${sha256(portableId)}",
                recordId = legacyId,
                contentFingerprint = sha256(value.toString()),
                capturedAt = record.lastUpdatedAt,
                sourcePostedAt = record.lifecycleStartedAt,
                title = record.latestTitle,
                body = record.latestBody,
                bigText = record.latestBigText,
                subText = null,
                textLines = emptyList(),
                senderName = record.senderName,
                conversationTitle = record.conversationTitle,
                category = record.category,
                importance = record.importance,
                extrasJson = value.optString("extrasJson", "{}"),
                isRecovered = true,
            )
        }
        return BackupSnapshot(
            conversations = conversations.values.toList(),
            records = records,
            revisions = revisions,
            messages = emptyList(),
            settings = settingsFromJson(root.optJSONObject("settings")),
        )
    }

    companion object {
        private val MAGIC = byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'B'.code.toByte(), '2'.code.toByte())
        private const val FORMAT_NAME = "notification-saver-backup"
        private const val JSON_FORMAT_VERSION = 2
        private const val ENCRYPTED_FORMAT_VERSION = 1
        private const val PBKDF2_ITERATIONS = 310_000
        private const val SALT_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val AES_KEY_BITS = 256
        const val MIN_PASSWORD_LENGTH = 8
    }
}

private fun AppSettings.toJson(): JSONObject = JSONObject()
    .put("hideNotificationPreviews", hideNotificationPreviews)
    .put("retentionDays", retentionDays)
    .put("fallbackCategory", fallbackCategory)
    .put("appCategoryOverrides", JSONObject(appCategoryOverrides))
    .put("themeMode", themeMode.name)
    .put("excludedPackages", JSONArray(excludedPackages.sorted()))

private fun ConversationEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("portableId", portableId).put("packageName", packageName)
    .put("appLabel", appLabel).put("canonicalKey", canonicalKey).put("identitySource", identitySource)
    .put("identityConfidence", identityConfidence).putNullable("sourceTitle", sourceTitle)
    .putNullable("manualTitle", manualTitle).putNullable("latestPreview", latestPreview)
    .put("latestActivityAt", latestActivityAt).put("recordCount", recordCount)
    .put("messageCount", messageCount).put("isPinned", isPinned)

private fun NotificationRecordEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("portableId", portableId).put("systemKey", systemKey)
    .put("packageName", packageName).put("appLabel", appLabel).put("notificationId", notificationId)
    .putNullable("notificationTag", notificationTag).put("userId", userId).putNullable("channelId", channelId)
    .putNullable("groupKey", groupKey).putNullable("shortcutId", shortcutId).putNullable("locusId", locusId)
    .put("lifecycleStartedAt", lifecycleStartedAt).put("lastUpdatedAt", lastUpdatedAt)
    .putNullable("endedAt", endedAt).putNullable("endReason", endReason)
    .putNullable("latestTitle", latestTitle).putNullable("latestBody", latestBody)
    .putNullable("latestBigText", latestBigText).putNullable("latestSubText", latestSubText)
    .putNullable("senderName", senderName).putNullable("conversationTitle", conversationTitle)
    .put("category", category).put("tags", JSONArray(tags)).putNullable("importance", importance)
    .put("visibility", visibility).put("notificationFlags", notificationFlags)
    .put("revisionCount", revisionCount).putNullable("conversationId", conversationId)
    .put("isActive", isActive).put("isGroupSummary", isGroupSummary).put("wasRecovered", wasRecovered)

private fun NotificationRevisionEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("portableId", portableId).put("recordId", recordId)
    .put("contentFingerprint", contentFingerprint).put("capturedAt", capturedAt)
    .put("sourcePostedAt", sourcePostedAt).putNullable("title", title).putNullable("body", body)
    .putNullable("bigText", bigText).putNullable("subText", subText).put("textLines", JSONArray(textLines))
    .putNullable("senderName", senderName).putNullable("conversationTitle", conversationTitle)
    .putNullable("category", category).putNullable("importance", importance)
    .put("extrasJson", extrasJson).put("isRecovered", isRecovered)

private fun ChatMessageEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("portableId", portableId).put("conversationId", conversationId)
    .put("recordId", recordId).putNullable("revisionId", revisionId)
    .put("messageFingerprint", messageFingerprint).putNullable("senderKey", senderKey)
    .putNullable("senderName", senderName).putNullable("text", text)
    .put("sourceTimestamp", sourceTimestamp).put("capturedAt", capturedAt)
    .putNullable("dataMimeType", dataMimeType).putNullable("dataUri", dataUri).put("isHistoric", isHistoric)

private fun settingsFromJson(value: JSONObject?): AppSettings {
    if (value == null) return AppSettings()
    val overrides = value.optJSONObject("appCategoryOverrides")?.let { json ->
        buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }
    }.orEmpty()
    return AppSettings(
        hideNotificationPreviews = value.optBoolean("hideNotificationPreviews", false),
        retentionDays = value.optInt("retentionDays", 0),
        fallbackCategory = value.optString("fallbackCategory", DEFAULT_FALLBACK_CATEGORY),
        appCategoryOverrides = overrides,
        themeMode = decodeThemeMode(value.optString("themeMode", ThemeMode.System.name)),
        excludedPackages = value.optJSONArray("excludedPackages").toStringList().toSet(),
    )
}

private fun conversationFromJson(value: JSONObject) = ConversationEntity(
    id = value.getLong("id"), portableId = value.getString("portableId"),
    packageName = value.getString("packageName"), appLabel = value.getString("appLabel"),
    canonicalKey = value.getString("canonicalKey"), identitySource = value.getString("identitySource"),
    identityConfidence = value.getInt("identityConfidence"), sourceTitle = value.stringOrNull("sourceTitle"),
    manualTitle = value.stringOrNull("manualTitle"), latestPreview = value.stringOrNull("latestPreview"),
    latestActivityAt = value.getLong("latestActivityAt"), recordCount = value.getInt("recordCount"),
    messageCount = value.getInt("messageCount"), isPinned = value.getBoolean("isPinned"),
)

private fun recordFromJson(value: JSONObject) = NotificationRecordEntity(
    id = value.getLong("id"), portableId = value.getString("portableId"), systemKey = value.getString("systemKey"),
    packageName = value.getString("packageName"), appLabel = value.getString("appLabel"),
    notificationId = value.getInt("notificationId"), notificationTag = value.stringOrNull("notificationTag"),
    userId = value.getInt("userId"), channelId = value.stringOrNull("channelId"), groupKey = value.stringOrNull("groupKey"),
    shortcutId = value.stringOrNull("shortcutId"), locusId = value.stringOrNull("locusId"),
    lifecycleStartedAt = value.getLong("lifecycleStartedAt"), lastUpdatedAt = value.getLong("lastUpdatedAt"),
    endedAt = value.longOrNull("endedAt"), endReason = value.intOrNull("endReason"),
    latestTitle = value.stringOrNull("latestTitle"), latestBody = value.stringOrNull("latestBody"),
    latestBigText = value.stringOrNull("latestBigText"), latestSubText = value.stringOrNull("latestSubText"),
    senderName = value.stringOrNull("senderName"), conversationTitle = value.stringOrNull("conversationTitle"),
    category = value.getString("category"), tags = value.optJSONArray("tags").toStringList(),
    importance = value.intOrNull("importance"), visibility = value.getInt("visibility"),
    notificationFlags = value.getInt("notificationFlags"), revisionCount = value.getInt("revisionCount"),
    conversationId = value.longOrNull("conversationId"), isActive = value.getBoolean("isActive"),
    isGroupSummary = value.getBoolean("isGroupSummary"), wasRecovered = value.getBoolean("wasRecovered"),
)

private fun revisionFromJson(value: JSONObject) = NotificationRevisionEntity(
    id = value.getLong("id"), portableId = value.getString("portableId"), recordId = value.getLong("recordId"),
    contentFingerprint = value.getString("contentFingerprint"), capturedAt = value.getLong("capturedAt"),
    sourcePostedAt = value.getLong("sourcePostedAt"), title = value.stringOrNull("title"), body = value.stringOrNull("body"),
    bigText = value.stringOrNull("bigText"), subText = value.stringOrNull("subText"),
    textLines = value.optJSONArray("textLines").toStringList(), senderName = value.stringOrNull("senderName"),
    conversationTitle = value.stringOrNull("conversationTitle"), category = value.stringOrNull("category"),
    importance = value.intOrNull("importance"), extrasJson = value.optString("extrasJson", "{}"),
    isRecovered = value.optBoolean("isRecovered", false),
)

private fun messageFromJson(value: JSONObject) = ChatMessageEntity(
    id = value.getLong("id"), portableId = value.getString("portableId"),
    conversationId = value.getLong("conversationId"), recordId = value.getLong("recordId"),
    revisionId = value.longOrNull("revisionId"), messageFingerprint = value.getString("messageFingerprint"),
    senderKey = value.stringOrNull("senderKey"), senderName = value.stringOrNull("senderName"), text = value.stringOrNull("text"),
    sourceTimestamp = value.getLong("sourceTimestamp"), capturedAt = value.getLong("capturedAt"),
    dataMimeType = value.stringOrNull("dataMimeType"), dataUri = value.stringOrNull("dataUri"),
    isHistoric = value.optBoolean("isHistoric", false),
)

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).trimmedOrNull()
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList { for (index in 0 until length()) optString(index).trimmedOrNull()?.let(::add) }
}
private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList { for (index in 0 until length()) optJSONObject(index)?.let { add(transform(it)) } }
}
