package dev.d3v.notificationsaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadModelsTest {
    @Test
    fun trimmedOrNull_normalizesOnlyOuterWhitespace() {
        assertEquals("Family chat", "  Family chat  ".trimmedOrNull())
        assertEquals("Family   chat", " Family   chat ".trimmedOrNull())
        assertNull(" \n\t ".trimmedOrNull())
        assertNull((null as String?).trimmedOrNull())
    }

    @Test
    fun displayTitle_prefersManualThenSourceThenApp() {
        assertEquals("My family", conversation(manualTitle = "My family").displayTitle)
        assertEquals("Family", conversation(manualTitle = null).displayTitle)
        assertEquals("Messenger", conversation(manualTitle = null, sourceTitle = null).displayTitle)
    }

    @Test
    fun activityCount_usesMessagesWhenAvailable() {
        assertEquals(8, conversation(recordCount = 3, messageCount = 8).activityCount())
        assertEquals(3, conversation(recordCount = 3, messageCount = 0).activityCount())
    }

    private fun conversation(
        manualTitle: String? = "My family",
        sourceTitle: String? = "Family",
        recordCount: Int = 1,
        messageCount: Int = 1,
    ) = ConversationEntity(
        id = 1,
        packageName = "dev.test.messenger",
        appLabel = "Messenger",
        canonicalKey = "shortcut:family",
        identitySource = "shortcut",
        identityConfidence = 100,
        sourceTitle = sourceTitle,
        manualTitle = manualTitle,
        latestPreview = "On my way",
        latestActivityAt = 100,
        recordCount = recordCount,
        messageCount = messageCount,
        isPinned = false,
    )
}
