package dev.d3v.notificationsaver

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["portableId"], unique = true),
        Index(value = ["packageName", "canonicalKey"], unique = true),
        Index(value = ["isPinned", "latestActivityAt"]),
        Index(value = ["latestActivityAt"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val portableId: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appLabel: String,
    val canonicalKey: String,
    val identitySource: String,
    val identityConfidence: Int,
    val sourceTitle: String?,
    val manualTitle: String?,
    val latestPreview: String?,
    val latestActivityAt: Long,
    val recordCount: Int,
    val messageCount: Int,
    val isPinned: Boolean,
) {
    val displayTitle: String
        get() = manualTitle.trimmedOrNull()
            ?: sourceTitle.trimmedOrNull()
            ?: appLabel
}

@Entity(
    tableName = "notification_records",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["portableId"], unique = true),
        Index(value = ["systemKey", "isActive"]),
        Index(value = ["lastUpdatedAt"]),
        Index(value = ["packageName", "lastUpdatedAt"]),
        Index(value = ["category", "lastUpdatedAt"]),
        Index(value = ["conversationId", "lastUpdatedAt"]),
    ],
)
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val portableId: String = UUID.randomUUID().toString(),
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
    val lifecycleStartedAt: Long,
    val lastUpdatedAt: Long,
    val endedAt: Long?,
    val endReason: Int?,
    val latestTitle: String?,
    val latestBody: String?,
    val latestBigText: String?,
    val latestSubText: String?,
    val senderName: String?,
    val conversationTitle: String?,
    val category: String,
    val tags: List<String>,
    val importance: Int?,
    val visibility: Int,
    val notificationFlags: Int,
    val revisionCount: Int,
    val conversationId: Long?,
    val isActive: Boolean,
    val isGroupSummary: Boolean,
    val wasRecovered: Boolean,
)

@Entity(
    tableName = "notification_revisions",
    foreignKeys = [
        ForeignKey(
            entity = NotificationRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["portableId"], unique = true),
        Index(value = ["recordId", "contentFingerprint"]),
        Index(value = ["recordId", "capturedAt"]),
    ],
)
data class NotificationRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val portableId: String = UUID.randomUUID().toString(),
    val recordId: Long,
    val contentFingerprint: String,
    val capturedAt: Long,
    val sourcePostedAt: Long,
    val title: String?,
    val body: String?,
    val bigText: String?,
    val subText: String?,
    val textLines: List<String>,
    val senderName: String?,
    val conversationTitle: String?,
    val category: String?,
    val importance: Int?,
    val extrasJson: String,
    val isRecovered: Boolean,
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NotificationRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NotificationRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["revisionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["portableId"], unique = true),
        Index(value = ["conversationId", "messageFingerprint"], unique = true),
        Index(value = ["conversationId", "sourceTimestamp"]),
        Index(value = ["recordId"]),
        Index(value = ["revisionId"]),
    ],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val portableId: String = UUID.randomUUID().toString(),
    val conversationId: Long,
    val recordId: Long,
    val revisionId: Long?,
    val messageFingerprint: String,
    val senderKey: String?,
    val senderName: String?,
    val text: String?,
    val sourceTimestamp: Long,
    val capturedAt: Long,
    val dataMimeType: String?,
    val dataUri: String?,
    val isHistoric: Boolean,
)

@Entity(
    tableName = "active_notifications",
    foreignKeys = [
        ForeignKey(
            entity = NotificationRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recordId"], unique = true)],
)
data class ActiveNotificationEntity(
    @PrimaryKey val systemKey: String,
    val recordId: Long,
    val lastContentFingerprint: String,
    val lastSeenAt: Long,
)

@Fts4
@Entity(tableName = "notification_records_fts")
data class NotificationRecordFts(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val recordId: Long,
    val appLabel: String,
    val title: String,
    val body: String,
    val bigText: String,
    val senderName: String,
    val conversationTitle: String,
)

data class AppSource(
    val packageName: String,
    val appLabel: String,
)

/** A lightweight, observable snapshot for the Home screen. */
data class HomeSummary(
    val savedToday: Int,
    val activeCount: Int,
    val latestActivityAt: Long?,
)

data class ConversationEntryRow(
    val stableId: String,
    val type: String,
    val timestamp: Long,
    val senderName: String?,
    val text: String?,
    val recordId: Long,
    val isHistoric: Boolean,
)

data class ConversationLatestRow(
    val preview: String?,
    val timestamp: Long,
)

class StringListConverter {
    @TypeConverter
    fun fromList(values: List<String>): String = JSONArray(values).toString()

    @TypeConverter
    fun toList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trimmedOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }
}

@Dao
interface NotificationDao {
    @Query(
        """
        SELECT notification_records.* FROM notification_records
        WHERE (:ftsQuery IS NULL OR id IN (
            SELECT rowid FROM notification_records_fts
            WHERE notification_records_fts MATCH :ftsQuery
        ))
            AND (:packageName IS NULL OR packageName = :packageName)
            AND (:category IS NULL OR category = :category)
            AND (:fromTimestamp IS NULL OR lastUpdatedAt >= :fromTimestamp)
        ORDER BY lastUpdatedAt DESC, id DESC
        """,
    )
    fun pageRecords(
        ftsQuery: String?,
        packageName: String?,
        category: String?,
        fromTimestamp: Long?,
    ): PagingSource<Int, NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE id = :id LIMIT 1")
    fun observeRecord(id: Long): Flow<NotificationRecordEntity?>

    @Query("SELECT * FROM notification_records WHERE id = :id LIMIT 1")
    suspend fun getRecord(id: Long): NotificationRecordEntity?

    @Query("SELECT * FROM notification_records WHERE portableId = :portableId LIMIT 1")
    suspend fun getRecordByPortableId(portableId: String): NotificationRecordEntity?

    @Query("SELECT * FROM notification_revisions WHERE recordId = :recordId ORDER BY capturedAt DESC, id DESC")
    fun observeRevisions(recordId: Long): Flow<List<NotificationRevisionEntity>>

    @Query("SELECT * FROM notification_revisions WHERE recordId = :recordId ORDER BY capturedAt DESC, id DESC")
    suspend fun getRevisions(recordId: Long): List<NotificationRevisionEntity>

    @Query("SELECT * FROM active_notifications WHERE systemKey = :systemKey LIMIT 1")
    suspend fun getActive(systemKey: String): ActiveNotificationEntity?

    @Query("SELECT systemKey FROM active_notifications")
    suspend fun getActiveKeys(): List<String>

    @Query("SELECT * FROM conversations WHERE packageName = :packageName AND canonicalKey = :canonicalKey LIMIT 1")
    suspend fun getConversation(packageName: String, canonicalKey: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE portableId = :portableId LIMIT 1")
    suspend fun getConversationByPortableId(portableId: String): ConversationEntity?

    @Query(
        """
        SELECT * FROM conversations
        WHERE (:searchText = '' OR COALESCE(manualTitle, sourceTitle, appLabel) LIKE '%' || :searchText || '%' COLLATE NOCASE)
          AND (:packageName IS NULL OR packageName = :packageName)
        ORDER BY isPinned DESC, latestActivityAt DESC, id DESC
        """,
    )
    fun pageConversations(searchText: String, packageName: String?): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeConversation(id: Long): Flow<ConversationEntity?>

    @Query(
        """
        SELECT
            'message:' || message.id AS stableId,
            'message' AS type,
            message.sourceTimestamp AS timestamp,
            message.senderName AS senderName,
            message.text AS text,
            message.recordId AS recordId,
            message.isHistoric AS isHistoric
        FROM chat_messages message
        WHERE message.conversationId = :conversationId
        UNION ALL
        SELECT
            'record:' || record.id AS stableId,
            'notification' AS type,
            record.lastUpdatedAt AS timestamp,
            record.senderName AS senderName,
            COALESCE(record.latestBody, record.latestBigText, record.latestTitle) AS text,
            record.id AS recordId,
            0 AS isHistoric
        FROM notification_records record
        WHERE record.conversationId = :conversationId
          AND NOT EXISTS (
              SELECT 1 FROM chat_messages message WHERE message.recordId = record.id
          )
        ORDER BY timestamp ASC, stableId ASC
        """,
    )
    fun pageConversationEntries(conversationId: Long): PagingSource<Int, ConversationEntryRow>

    @Query("SELECT COUNT(*) FROM notification_records WHERE conversationId = :conversationId")
    suspend fun conversationRecordCount(conversationId: Long): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun conversationMessageCount(conversationId: Long): Int

    @Query(
        """
        SELECT preview, timestamp FROM (
            SELECT text AS preview, sourceTimestamp AS timestamp
            FROM chat_messages
            WHERE conversationId = :conversationId
            UNION ALL
            SELECT COALESCE(latestBody, latestBigText, latestTitle) AS preview,
                   lastUpdatedAt AS timestamp
            FROM notification_records
            WHERE conversationId = :conversationId
        )
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun conversationLatest(conversationId: Long): ConversationLatestRow?

    @Query("SELECT packageName, MAX(appLabel) AS appLabel FROM notification_records GROUP BY packageName ORDER BY appLabel COLLATE NOCASE")
    fun observeAppSources(): Flow<List<AppSource>>

    @Query("SELECT DISTINCT category FROM notification_records ORDER BY category COLLATE NOCASE")
    fun observeCategories(): Flow<List<String>>

    @Query(
        """
        SELECT
            COUNT(CASE WHEN lastUpdatedAt >= :dayStart THEN 1 END) AS savedToday,
            COUNT(CASE WHEN isActive = 1 THEN 1 END) AS activeCount,
            MAX(lastUpdatedAt) AS latestActivityAt
        FROM notification_records
        """,
    )
    fun observeHomeSummary(dayStart: Long): Flow<HomeSummary>

    @Query("SELECT * FROM notification_records ORDER BY lastUpdatedAt ASC, id ASC")
    suspend fun getAllRecordsForExport(): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_revisions ORDER BY recordId ASC, capturedAt ASC, id ASC")
    suspend fun getAllRevisionsForExport(): List<NotificationRevisionEntity>

    @Query("SELECT * FROM conversations ORDER BY id ASC")
    suspend fun getAllConversationsForExport(): List<ConversationEntity>

    @Query("SELECT * FROM chat_messages ORDER BY conversationId ASC, sourceTimestamp ASC, id ASC")
    suspend fun getAllMessagesForExport(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(entity: ConversationEntity): Long

    @Insert
    suspend fun insertRecord(entity: NotificationRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRevision(entity: NotificationRevisionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(entity: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActive(entity: ActiveNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFts(entity: NotificationRecordFts)

    @Update
    suspend fun updateRecord(entity: NotificationRecordEntity)

    @Update
    suspend fun updateConversation(entity: ConversationEntity)

    @Query("DELETE FROM active_notifications WHERE systemKey = :systemKey")
    suspend fun deleteActive(systemKey: String)

    @Query("DELETE FROM active_notifications WHERE systemKey NOT IN (:activeKeys)")
    suspend fun deleteActiveExcept(activeKeys: List<String>)

    @Query("DELETE FROM active_notifications")
    suspend fun deleteAllActive()

    @Query("UPDATE notification_records SET isActive = 0, endedAt = :endedAt, endReason = :reason WHERE id = :recordId")
    suspend fun endRecord(recordId: Long, endedAt: Long, reason: Int)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :conversationId")
    suspend fun setConversationPinned(conversationId: Long, isPinned: Boolean)

    @Query("UPDATE conversations SET manualTitle = :manualTitle WHERE id = :conversationId")
    suspend fun renameConversation(conversationId: Long, manualTitle: String?)

    @Query("UPDATE notification_records SET category = :category, tags = :tags WHERE id = :recordId")
    suspend fun updateRecordMetadata(recordId: Long, category: String, tags: List<String>)

    @Query("DELETE FROM notification_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query(
        """
        DELETE FROM notification_records
        WHERE (:packageName IS NULL OR packageName = :packageName)
          AND (:fromTimestamp IS NULL OR lastUpdatedAt >= :fromTimestamp)
        """,
    )
    suspend fun deleteFiltered(packageName: String?, fromTimestamp: Long?)

    @Query("DELETE FROM notification_records WHERE lastUpdatedAt < :cutoffTimestamp AND isActive = 0")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM notification_records")
    suspend fun clearRecords()

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    @Query("DELETE FROM notification_records_fts WHERE rowid = :recordId")
    suspend fun deleteFts(recordId: Long)

    @Query("DELETE FROM notification_records_fts")
    suspend fun clearFts()

    @Query("SELECT COUNT(*) FROM notification_records")
    suspend fun recordCount(): Int

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun messageCount(): Int
}

@Database(
    entities = [
        ConversationEntity::class,
        NotificationRecordEntity::class,
        NotificationRevisionEntity::class,
        ChatMessageEntity::class,
        ActiveNotificationEntity::class,
        NotificationRecordFts::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(StringListConverter::class)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val FILE_NAME = "notification-saver.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN manualThreadLabel TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_manualThreadLabel ON notifications(manualThreadLabel)")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createVersion3Tables(db)
                migrateLegacyRows(db)
            }
        }

        fun build(context: Context): NotificationDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NotificationDatabase::class.java,
                FILE_NAME,
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createFtsTriggers(db)
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            createFtsTriggers(db)
                        }
                    },
                )
                .build()
        }

        private fun createVersion3Tables(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    portableId TEXT NOT NULL,
                    packageName TEXT NOT NULL,
                    appLabel TEXT NOT NULL,
                    canonicalKey TEXT NOT NULL,
                    identitySource TEXT NOT NULL,
                    identityConfidence INTEGER NOT NULL,
                    sourceTitle TEXT,
                    manualTitle TEXT,
                    latestPreview TEXT,
                    latestActivityAt INTEGER NOT NULL,
                    recordCount INTEGER NOT NULL,
                    messageCount INTEGER NOT NULL,
                    isPinned INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_conversations_portableId ON conversations(portableId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_conversations_packageName_canonicalKey ON conversations(packageName, canonicalKey)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_isPinned_latestActivityAt ON conversations(isPinned, latestActivityAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_latestActivityAt ON conversations(latestActivityAt)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notification_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    portableId TEXT NOT NULL,
                    systemKey TEXT NOT NULL,
                    packageName TEXT NOT NULL,
                    appLabel TEXT NOT NULL,
                    notificationId INTEGER NOT NULL,
                    notificationTag TEXT,
                    userId INTEGER NOT NULL,
                    channelId TEXT,
                    groupKey TEXT,
                    shortcutId TEXT,
                    locusId TEXT,
                    lifecycleStartedAt INTEGER NOT NULL,
                    lastUpdatedAt INTEGER NOT NULL,
                    endedAt INTEGER,
                    endReason INTEGER,
                    latestTitle TEXT,
                    latestBody TEXT,
                    latestBigText TEXT,
                    latestSubText TEXT,
                    senderName TEXT,
                    conversationTitle TEXT,
                    category TEXT NOT NULL,
                    tags TEXT NOT NULL,
                    importance INTEGER,
                    visibility INTEGER NOT NULL,
                    notificationFlags INTEGER NOT NULL,
                    revisionCount INTEGER NOT NULL,
                    conversationId INTEGER,
                    isActive INTEGER NOT NULL,
                    isGroupSummary INTEGER NOT NULL,
                    wasRecovered INTEGER NOT NULL,
                    FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_records_portableId ON notification_records(portableId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_records_systemKey_isActive ON notification_records(systemKey, isActive)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_records_lastUpdatedAt ON notification_records(lastUpdatedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_records_packageName_lastUpdatedAt ON notification_records(packageName, lastUpdatedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_records_category_lastUpdatedAt ON notification_records(category, lastUpdatedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_records_conversationId_lastUpdatedAt ON notification_records(conversationId, lastUpdatedAt)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notification_revisions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    portableId TEXT NOT NULL,
                    recordId INTEGER NOT NULL,
                    contentFingerprint TEXT NOT NULL,
                    capturedAt INTEGER NOT NULL,
                    sourcePostedAt INTEGER NOT NULL,
                    title TEXT,
                    body TEXT,
                    bigText TEXT,
                    subText TEXT,
                    textLines TEXT NOT NULL,
                    senderName TEXT,
                    conversationTitle TEXT,
                    category TEXT,
                    importance INTEGER,
                    extrasJson TEXT NOT NULL,
                    isRecovered INTEGER NOT NULL,
                    FOREIGN KEY(recordId) REFERENCES notification_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_revisions_portableId ON notification_revisions(portableId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_revisions_recordId_contentFingerprint ON notification_revisions(recordId, contentFingerprint)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_revisions_recordId_capturedAt ON notification_revisions(recordId, capturedAt)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    portableId TEXT NOT NULL,
                    conversationId INTEGER NOT NULL,
                    recordId INTEGER NOT NULL,
                    revisionId INTEGER,
                    messageFingerprint TEXT NOT NULL,
                    senderKey TEXT,
                    senderName TEXT,
                    text TEXT,
                    sourceTimestamp INTEGER NOT NULL,
                    capturedAt INTEGER NOT NULL,
                    dataMimeType TEXT,
                    dataUri TEXT,
                    isHistoric INTEGER NOT NULL,
                    FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(recordId) REFERENCES notification_records(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(revisionId) REFERENCES notification_revisions(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_messages_portableId ON chat_messages(portableId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_messages_conversationId_messageFingerprint ON chat_messages(conversationId, messageFingerprint)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_sourceTimestamp ON chat_messages(conversationId, sourceTimestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_recordId ON chat_messages(recordId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_revisionId ON chat_messages(revisionId)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS active_notifications (
                    systemKey TEXT NOT NULL PRIMARY KEY,
                    recordId INTEGER NOT NULL,
                    lastContentFingerprint TEXT NOT NULL,
                    lastSeenAt INTEGER NOT NULL,
                    FOREIGN KEY(recordId) REFERENCES notification_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_active_notifications_recordId ON active_notifications(recordId)")
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS notification_records_fts USING FTS4(
                    appLabel TEXT NOT NULL,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    bigText TEXT NOT NULL,
                    senderName TEXT NOT NULL,
                    conversationTitle TEXT NOT NULL
                )
                """.trimIndent(),
            )
            createFtsTriggers(db)
        }

        private fun createFtsTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS notification_records_fts_after_insert
                AFTER INSERT ON notification_records BEGIN
                    INSERT INTO notification_records_fts(
                        rowid, appLabel, title, body, bigText, senderName, conversationTitle
                    ) VALUES (
                        new.id, new.appLabel, COALESCE(new.latestTitle, ''),
                        COALESCE(new.latestBody, ''), COALESCE(new.latestBigText, ''),
                        COALESCE(new.senderName, ''), COALESCE(new.conversationTitle, '')
                    );
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS notification_records_fts_after_update
                AFTER UPDATE ON notification_records BEGIN
                    DELETE FROM notification_records_fts WHERE rowid = old.id;
                    INSERT INTO notification_records_fts(
                        rowid, appLabel, title, body, bigText, senderName, conversationTitle
                    ) VALUES (
                        new.id, new.appLabel, COALESCE(new.latestTitle, ''),
                        COALESCE(new.latestBody, ''), COALESCE(new.latestBigText, ''),
                        COALESCE(new.senderName, ''), COALESCE(new.conversationTitle, '')
                    );
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS notification_records_fts_after_delete
                AFTER DELETE ON notification_records BEGIN
                    DELETE FROM notification_records_fts WHERE rowid = old.id;
                END
                """.trimIndent(),
            )
        }

        private fun migrateLegacyRows(db: SupportSQLiteDatabase) {
            val resolvedThread = "COALESCE(NULLIF(TRIM(manualThreadLabel), ''), NULLIF(TRIM(conversationTitle), ''), NULLIF(TRIM(senderName), ''), NULLIF(TRIM(threadKey), ''))"
            db.execSQL(
                """
                INSERT OR IGNORE INTO conversations (
                    portableId, packageName, appLabel, canonicalKey, identitySource,
                    identityConfidence, sourceTitle, manualTitle, latestPreview,
                    latestActivityAt, recordCount, messageCount, isPinned
                )
                SELECT
                    'legacy-conversation:' || packageName || ':' || $resolvedThread,
                    packageName,
                    MAX(appLabel),
                    'legacy:' || $resolvedThread,
                    'legacy',
                    25,
                    $resolvedThread,
                    MAX(NULLIF(TRIM(manualThreadLabel), '')),
                    MAX(COALESCE(NULLIF(TRIM(body), ''), NULLIF(TRIM(bigText), ''), NULLIF(TRIM(title), ''))),
                    MAX(lastSeenAt),
                    COUNT(*),
                    0,
                    0
                FROM notifications
                WHERE $resolvedThread IS NOT NULL
                GROUP BY packageName, $resolvedThread
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO notification_records (
                    portableId, systemKey, packageName, appLabel, notificationId,
                    notificationTag, userId, channelId, groupKey, shortcutId, locusId,
                    lifecycleStartedAt, lastUpdatedAt, endedAt, endReason,
                    latestTitle, latestBody, latestBigText, latestSubText, senderName,
                    conversationTitle, category, tags, importance, visibility,
                    notificationFlags, revisionCount, conversationId, isActive,
                    isGroupSummary, wasRecovered
                )
                SELECT
                    'legacy-record:' || old.id,
                    old.sourceKey,
                    old.packageName,
                    old.appLabel,
                    old.notificationId,
                    old.notificationTag,
                    0,
                    NULL,
                    old.threadKey,
                    NULL,
                    NULL,
                    old.postedAt,
                    old.lastSeenAt,
                    old.removedAt,
                    CASE WHEN old.isRemoved = 1 THEN -1 ELSE NULL END,
                    old.title,
                    old.body,
                    old.bigText,
                    NULL,
                    old.senderName,
                    old.conversationTitle,
                    old.category,
                    old.tags,
                    old.importance,
                    0,
                    0,
                    1,
                    conversation.id,
                    CASE WHEN old.isRemoved = 1 THEN 0 ELSE 1 END,
                    0,
                    1
                FROM notifications old
                LEFT JOIN conversations conversation
                    ON conversation.packageName = old.packageName
                    AND conversation.canonicalKey = 'legacy:' || $resolvedThread
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO notification_revisions (
                    portableId, recordId, contentFingerprint, capturedAt,
                    sourcePostedAt, title, body, bigText, subText, textLines,
                    senderName, conversationTitle, category, importance,
                    extrasJson, isRecovered
                )
                SELECT
                    'legacy-revision:' || old.id,
                    record.id,
                    'legacy:' || old.id,
                    old.lastSeenAt,
                    old.postedAt,
                    old.title,
                    old.body,
                    old.bigText,
                    NULL,
                    '[]',
                    old.senderName,
                    old.conversationTitle,
                    old.category,
                    old.importance,
                    old.extrasJson,
                    1
                FROM notifications old
                JOIN notification_records record
                    ON record.portableId = 'legacy-record:' || old.id
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO active_notifications(systemKey, recordId, lastContentFingerprint, lastSeenAt)
                SELECT systemKey, id, 'legacy:' || SUBSTR(portableId, LENGTH('legacy-record:') + 1), lastUpdatedAt
                FROM notification_records
                WHERE isActive = 1
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE notifications")
        }
    }
}
