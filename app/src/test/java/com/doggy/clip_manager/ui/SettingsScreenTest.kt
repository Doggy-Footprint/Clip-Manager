package com.doggy.clip_manager.ui

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.doggy.clip_manager.ui.fakes.FakeSettingsSection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VO6 (FR8, FR9, C10, C11, C12): SettingsScreen clicker state transitions,
 * state restoration, and section ordering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialLabel_isHi() {
        composeTestRule.setContent {
            SettingsScreen(sections = emptyList())
        }

        composeTestRule.onNodeWithText("hi").assertExists()
    }

    @Test
    fun oneClick_hiTransitionsToBye_c10() {
        composeTestRule.setContent {
            SettingsScreen(sections = emptyList())
        }

        composeTestRule.onNodeWithText("hi").performClick()

        composeTestRule.onNodeWithText("bye").assertExists()
    }

    @Test
    fun twoClicks_returnsToHi_c10() {
        composeTestRule.setContent {
            SettingsScreen(sections = emptyList())
        }

        composeTestRule.onNodeWithText("hi").performClick()
        composeTestRule.onNodeWithText("bye").performClick()

        composeTestRule.onNodeWithText("hi").assertExists()
    }

    @Test
    fun threeClicks_backToBye_c10() {
        composeTestRule.setContent {
            SettingsScreen(sections = emptyList())
        }

        composeTestRule.onNodeWithText("hi").performClick()
        composeTestRule.onNodeWithText("bye").performClick()
        composeTestRule.onNodeWithText("hi").performClick()

        composeTestRule.onNodeWithText("bye").assertExists()
    }

    @Test
    fun clickThenConfigurationChangeRestore_labelStaysBye_c11() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            SettingsScreen(sections = emptyList())
        }

        composeTestRule.onNodeWithText("hi").performClick()
        composeTestRule.onNodeWithText("bye").assertExists()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("bye").assertExists()
    }

    @Test
    fun sectionsRenderBelowClicker_inGivenOrder_c12() {
        // Sections are passed already ordered (orderedSettingsSections is
        // exercised independently by VO2); SettingsScreen itself must not reorder.
        val s1 = FakeSettingsSection(id = "s1", order = 1, titleText = "S1")
        val s2 = FakeSettingsSection(id = "s2", order = 2, titleText = "S2")

        composeTestRule.setContent {
            SettingsScreen(sections = listOf(s1, s2))
        }

        val clickerTop = composeTestRule.onNodeWithText("hi").getBoundsInRoot().top
        val s1Top = composeTestRule.onNodeWithText("S1", useUnmergedTree = true).getBoundsInRoot().top
        val s2Top = composeTestRule.onNodeWithText("S2", useUnmergedTree = true).getBoundsInRoot().top

        assertTrue("clicker ($clickerTop) should be above S1 ($s1Top)", clickerTop < s1Top)
        assertTrue("S1 ($s1Top) should be above S2 ($s2Top)", s1Top < s2Top)
    }
}
