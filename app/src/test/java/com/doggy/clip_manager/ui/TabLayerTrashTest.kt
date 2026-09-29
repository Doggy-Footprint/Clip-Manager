package com.doggy.clip_manager.ui

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V11 (R11) - part 1/2: the rail's 휴지통 item is positioned directly above 설정, and
 * clicking it invokes `onOpenTrash`. `TabLayer`'s new `onOpenTrash` parameter is from
 * spec v2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabLayerTrashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun trashIcon_V11_isPositionedAboveSettings() {
        composeTestRule.setContent {
            TabLayer(
                selected = TabKey.BuiltIn(MediaTab.FILES),
                onSelect = {},
                extensions = emptyList(),
                compact = false,
                onOpenTrash = {},
            )
        }

        val trashTop = composeTestRule.onNodeWithText("휴지통").getBoundsInRoot().top
        val settingsTop = composeTestRule.onNodeWithText("설정").getBoundsInRoot().top

        assertTrue("trash icon must sit above (smaller top offset than) settings", trashTop < settingsTop)
    }

    @Test
    fun trashIcon_V11_clickInvokesOnOpenTrash() {
        var opened = false
        composeTestRule.setContent {
            TabLayer(
                selected = TabKey.BuiltIn(MediaTab.FILES),
                onSelect = {},
                extensions = emptyList(),
                compact = false,
                onOpenTrash = { opened = true },
            )
        }

        composeTestRule.onNodeWithText("휴지통").performClick()

        assertTrue(opened)
    }
}
