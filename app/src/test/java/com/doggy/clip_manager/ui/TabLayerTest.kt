package com.doggy.clip_manager.ui

import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.R
import com.doggy.clip_manager.ui.fakes.FakeTabExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VO5 (FR2, FR3, FR7, C8, C9): TabLayer scenario — extension label rendering,
 * clicking an extension tab, clicking the Settings item, extension-tab
 * placement (spec v3 F1/FR2) and Settings-selected state (spec v3 F2/FR7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabLayerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun extensionLabelDisplayed_andClickReportsExtensionKey_c8() {
        val extension = FakeTabExtension(id = "ext-a", order = 0, labelText = "ExtA")
        var received: TabKey? = null

        composeTestRule.setContent {
            TabLayer(
                selected = TabKey.BuiltIn(MediaTab.FILES),
                onSelect = { received = it },
                extensions = listOf(extension),
                compact = false,
            )
        }

        composeTestRule.onNodeWithText("ExtA", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("ExtA", useUnmergedTree = true).performClick()

        assertEquals(TabKey.Extension("ext-a"), received)
    }

    @Test
    fun clickSettingsItem_reportsSettingsKey_c9() {
        var received: TabKey? = null

        composeTestRule.setContent {
            TabLayer(
                selected = TabKey.BuiltIn(MediaTab.FILES),
                onSelect = { received = it },
                extensions = emptyList(),
                compact = false,
            )
        }

        // Independent oracle: the app's own string resource, not a hardcoded label.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsLabel = context.getString(R.string.tab_settings)

        composeTestRule.onNodeWithText(settingsLabel, useUnmergedTree = true).performClick()

        assertEquals(TabKey.Settings, received)
    }

    @Test
    fun extensionTab_placedAfterLastBuiltInAndBeforeDividerFollowUp_f1() {
        val extension = FakeTabExtension(id = "ext-a", order = 0, labelText = "ExtA")

        composeTestRule.setContent {
            // Force a tall viewport so every rail item is laid out (and thus has a
            // real, unclipped position) even if the item list scrolls under the
            // Robolectric window's default (short) height.
            TabLayer(
                selected = TabKey.BuiltIn(MediaTab.FILES),
                onSelect = {},
                extensions = listOf(extension),
                compact = false,
                modifier = Modifier.requiredHeight(2000.dp),
            )
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val lastBuiltInLabel = context.getString(R.string.tab_images)
        val afterDividerLabel = context.getString(R.string.tab_dummy_tag)

        // positionInRoot (unlike getBoundsInRoot) is not clamped to the visible
        // viewport, so it stays accurate even for content laid out just below the
        // fold of a scrollable container.
        val lastBuiltInTop =
            composeTestRule.onNodeWithText(lastBuiltInLabel, useUnmergedTree = true)
                .fetchSemanticsNode().positionInRoot.y
        val extensionTop =
            composeTestRule.onNodeWithText("ExtA", useUnmergedTree = true)
                .fetchSemanticsNode().positionInRoot.y
        val afterDividerTop =
            composeTestRule.onNodeWithText(afterDividerLabel, useUnmergedTree = true)
                .fetchSemanticsNode().positionInRoot.y

        assertTrue(
            "last built-in ($lastBuiltInTop) should be above the extension tab ($extensionTop)",
            lastBuiltInTop < extensionTop,
        )
        assertTrue(
            "extension tab ($extensionTop) should be above the item after the divider ($afterDividerTop)",
            extensionTop < afterDividerTop,
        )
    }

    @Test
    fun settingsSelected_settingsItemIsSelected_f2() {
        composeTestRule.setContent {
            TabLayer(
                selected = TabKey.Settings,
                onSelect = {},
                extensions = emptyList(),
                compact = false,
            )
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsLabel = context.getString(R.string.tab_settings)

        composeTestRule
            .onNode(hasText(settingsLabel) and isSelectable())
            .assertIsSelected()
    }

    @Test
    fun builtInSelected_settingsItemIsNotSelected_f2() {
        composeTestRule.setContent {
            TabLayer(
                selected = TabKey.BuiltIn(MediaTab.FILES),
                onSelect = {},
                extensions = emptyList(),
                compact = false,
            )
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsLabel = context.getString(R.string.tab_settings)

        composeTestRule
            .onNode(hasText(settingsLabel) and isSelectable())
            .assertIsNotSelected()
    }
}
