package dev.d3v.notificationsaver

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingDismissal_revealsRootTabs() {
        dismissOnboardingIfNeeded()

        assertTextPresent("Home")
        assertTextPresent("Chats")
        assertTextPresent("Settings")
    }

    @Test
    fun inboxFilters_openFromToolbarButton() {
        dismissOnboardingIfNeeded()

        composeRule.onNodeWithContentDescription("Filters").performClick()
        composeRule.waitForIdle()
        assertTextPresent("Date range")
        assertTextPresent("All time")
    }

    @Test
    fun settingsScreen_exposesAdvancedEntryPoint() {
        dismissOnboardingIfNeeded()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Appearance"))
        assertTextPresent("Appearance")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Advanced and support"))
        composeRule.onNodeWithText("Advanced and support").performClick()
        assertTextPresent("Capture health")
    }

    private fun dismissOnboardingIfNeeded() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            hasTextNode("Home") || hasTextNode("Your notifications, saved privately")
        }
        if (hasTextNode("Your notifications, saved privately")) {
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Continue without access"))
            composeRule.onNodeWithText("Continue without access").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { hasTextNode("Home") }
    }

    private fun hasTextNode(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun assertTextPresent(text: String) {
        assertTrue(
            "Expected to find text '$text' in the current UI tree.",
            composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
