package dev.d3v.notificationsaver

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import org.json.JSONArray

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["sourceKey"], unique = true),
        Index(value = ["packageName"]),
        Index(value = ["category"]),
        Index(value = ["threadKey"]),
        Index(value = ["manualThreadLabel"]),
        Index(value = ["lastSeenAt"]),
    ],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceKey: String,
    val packageName: String,
    val appLabel: String,
    val notificationId: Int,
    val notificationTag: String?,
    val postedAt: Long,
    val lastSeenAt: Long,
    val removedAt: Long?,
    val title: String?,
    val body: String?,
    val bigText: String?,
    val category: String,
    val tags: List<String>,
    val manualThreadLabel: String?,
    val threadKey: String?,
    val senderName: String?,
    val conversationTitle: String?,
    val importance: Int?,
    val extrasJson: String,
    val isRemoved: Boolean,
)

data class AppSource(
    val packageName: String,
    val appLabel: String,
)

class TagListConverter {
    @TypeConverter
    fun fromTags(tags: List<String>): String {
        return JSONArray(tags).toString()
    }

    @TypeConverter
    fun toTags(value: String): List<String> {
        if (value.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.optString(index))
                }
            }
        }.getOrDefault(emptyList())
    }
}

@Dao
interface NotificationDao {
    @Query(
        """
        SELECT * FROM notifications
        WHERE
            (:searchText = '' OR
                COALESCE(title, '') LIKE '%' || :searchText || '%' OR
                COALESCE(body, '') LIKE '%' || :searchText || '%' OR
                COALESCE(bigText, '') LIKE '%' || :searchText || '%' OR
                COALESCE(appLabel, '') LIKE '%' || :searchText || '%'
            )
            AND (:packageName IS NULL OR packageName = :packageName)
            AND (:category IS NULL OR category = :category)
            AND (:fromTimestamp IS NULL OR postedAt >= :fromTimestamp)
        ORDER BY lastSeenAt DESC, id DESC
        """,
    )
    fun observeNotifications(
        searchText: String,
        packageName: String?,
        category: String?,
        fromTimestamp: Long?,
    ): kotlinx.coroutines.flow.Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    fun observeNotification(id: Long): kotlinx.coroutines.flow.Flow<NotificationEntity?>

    @Query(
        """
        SELECT * FROM notifications
        WHERE packageName = :packageName
            AND COALESCE(
                NULLIF(TRIM(manualThreadLabel), ''),
                NULLIF(TRIM(conversationTitle), ''),
                NULLIF(TRIM(senderName), ''),
                NULLIF(TRIM(threadKey), '')
            ) = :resolvedThreadId
        ORDER BY postedAt ASC, id ASC
        """,
    )
    fun observeThread(
        packageName: String,
        resolvedThreadId: String,
    ): kotlinx.coroutines.flow.Flow<List<NotificationEntity>>

    @Query(
        """
        WITH threaded AS (
            SELECT
                id,
                packageName,
                appLabel,
                COALESCE(
                    NULLIF(TRIM(manualThreadLabel), ''),
                    NULLIF(TRIM(conversationTitle), ''),
                    NULLIF(TRIM(senderName), ''),
                    NULLIF(TRIM(threadKey), '')
                ) AS resolvedThreadId,
                COALESCE(
                    NULLIF(TRIM(manualThreadLabel), ''),
                    NULLIF(TRIM(conversationTitle), ''),
                    NULLIF(TRIM(senderName), ''),
                    NULLIF(TRIM(threadKey), ''),
                    NULLIF(TRIM(title), ''),
                    appLabel
                ) AS displayTitle,
                COALESCE(NULLIF(TRIM(body), ''), NULLIF(TRIM(bigText), ''), NULLIF(TRIM(title), '')) AS previewText,
                lastSeenAt,
                CASE WHEN lastSeenAt >= :recentCutoff THEN 1 ELSE 0 END AS isRecent,
                COALESCE(importance, 0) AS importanceValue
            FROM notifications
            WHERE COALESCE(
                NULLIF(TRIM(manualThreadLabel), ''),
                NULLIF(TRIM(conversationTitle), ''),
                NULLIF(TRIM(senderName), ''),
                NULLIF(TRIM(threadKey), '')
            ) IS NOT NULL
        ),
        thread_stats AS (
            SELECT
                packageName,
                resolvedThreadId,
                MAX(lastSeenAt) AS latestTimestamp,
                COUNT(*) AS totalCount,
                SUM(isRecent) AS recentCount,
                MAX(importanceValue) AS maxImportance
            FROM threaded
            GROUP BY packageName, resolvedThreadId
        )
        SELECT
            stats.packageName AS packageName,
            MAX(threaded.appLabel) AS appLabel,
            stats.resolvedThreadId AS resolvedThreadId,
            COALESCE(
                MAX(CASE WHEN threaded.lastSeenAt = stats.latestTimestamp THEN threaded.displayTitle END),
                MAX(threaded.displayTitle)
            ) AS displayTitle,
            COALESCE(
                MAX(CASE WHEN threaded.lastSeenAt = stats.latestTimestamp THEN threaded.previewText END),
                MAX(threaded.previewText)
            ) AS latestPreview,
            stats.latestTimestamp AS latestTimestamp,
            stats.totalCount AS totalCount,
            stats.recentCount AS recentCount,
            stats.maxImportance AS maxImportance
        FROM thread_stats stats
        JOIN threaded
            ON threaded.packageName = stats.packageName
            AND threaded.resolvedThreadId = stats.resolvedThreadId
        GROUP BY
            stats.packageName,
            stats.resolvedThreadId,
            stats.latestTimestamp,
            stats.totalCount,
            stats.recentCount,
            stats.maxImportance
        ORDER BY stats.latestTimestamp DESC, stats.totalCount DESC
        """,
    )
    fun observeThreadSummaries(
        recentCutoff: Long,
    ): kotlinx.coroutines.flow.Flow<List<ThreadSummaryRow>>

    @Query("SELECT * FROM notifications WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getBySourceKey(sourceKey: String): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NotificationEntity?

    @Query("SELECT packageName, MAX(appLabel) AS appLabel FROM notifications GROUP BY packageName ORDER BY appLabel COLLATE NOCASE")
    fun observeAppSources(): kotlinx.coroutines.flow.Flow<List<AppSource>>

    @Query("SELECT DISTINCT category FROM notifications ORDER BY category COLLATE NOCASE")
    fun observeCategories(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("SELECT * FROM notifications ORDER BY lastSeenAt DESC, id DESC")
    suspend fun getAllForExport(): List<NotificationEntity>

    @Insert
    suspend fun insert(entity: NotificationEntity): Long

    @Update
    suspend fun update(entity: NotificationEntity)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        UPDATE notifications
        SET manualThreadLabel = :manualThreadLabel
        WHERE packageName = :packageName
            AND COALESCE(
                NULLIF(TRIM(manualThreadLabel), ''),
                NULLIF(TRIM(conversationTitle), ''),
                NULLIF(TRIM(senderName), ''),
                NULLIF(TRIM(threadKey), '')
            ) = :resolvedThreadId
        """,
    )
    suspend fun updateManualThreadLabelByResolvedThread(
        packageName: String,
        resolvedThreadId: String,
        manualThreadLabel: String?,
    )

    @Query(
        """
        DELETE FROM notifications
        WHERE (:packageName IS NULL OR packageName = :packageName)
            AND (:fromTimestamp IS NULL OR postedAt >= :fromTimestamp)
        """,
    )
    suspend fun deleteFiltered(
        packageName: String?,
        fromTimestamp: Long?,
    )

    @Query("DELETE FROM notifications WHERE postedAt < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

@Database(
    entities = [NotificationEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(TagListConverter::class)
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

        fun build(context: Context): NotificationDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NotificationDatabase::class.java,
                FILE_NAME,
            ).addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
