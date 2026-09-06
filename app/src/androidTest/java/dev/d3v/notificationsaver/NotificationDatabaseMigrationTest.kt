package dev.d3v.notificationsaver

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotificationDatabase::class.java,
    )

    @Test
    fun migrate2To3_preservesHistoryAndBuildsConversationSearch() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO notifications (
                    sourceKey, packageName, appLabel, notificationId, notificationTag,
                    postedAt, lastSeenAt, removedAt, title, body, bigText, category,
                    tags, manualThreadLabel, threadKey, senderName, conversationTitle,
                    importance, extrasJson, isRemoved
                ) VALUES (
                    'source-1', 'dev.test.chat', 'Test Chat', 7, 'family',
                    100, 200, NULL, 'Family', 'Dinner at seven', NULL, 'Messages',
                    '["important"]', NULL, 'family', 'Alex', 'Family', 4, '{}', 0
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            NotificationDatabase.MIGRATION_2_3,
        )

        assertEquals(1, migrated.count("notification_records"))
        assertEquals(1, migrated.count("notification_revisions"))
        assertEquals(1, migrated.count("conversations"))
        assertEquals(1, migrated.count("active_notifications"))
        migrated.query(
            "SELECT COUNT(*) FROM notification_records_fts WHERE notification_records_fts MATCH 'Dinner'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int {
        return query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    companion object {
        private const val DATABASE_NAME = "migration-test"
    }
}
