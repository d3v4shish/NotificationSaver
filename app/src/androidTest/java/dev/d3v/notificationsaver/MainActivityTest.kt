package dev.d3v.notificationsaver

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        assertTextPresent("Timeline")
        assertTextPresent("Threads")
        assertTextPresent("Priority")
        assertTextPresent("Settings")
    }

    @Test
    fun timelineFilters_openFromToolbarButton() {
        dismissOnboardingIfNeeded()

        composeRule.onNodeWithText("Filters").performClick()
        composeRule.waitForIdle()
        assertTextPresent("Date range")
        assertTextPresent("Done")
    }

    @Test
    fun settingsScreen_exposesAdvancedEntryPoint() {
        dismissOnboardingIfNeeded()

        composeRule.onAllNodesWithText("Settings", useUnmergedTree = true).onLast().performClick()
        composeRule.waitForIdle()
        assertTextPresent("Appearance")
        assertTextPresent("Advanced")
        assertTextPresent("Show")
    }

    private fun dismissOnboardingIfNeeded() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("Continue", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Continue", useUnmergedTree = true).performClick()
        }
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "Expected to find text '$text' in the current UI tree.",
            composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
