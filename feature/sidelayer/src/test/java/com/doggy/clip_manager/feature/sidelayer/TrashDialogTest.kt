package com.doggy.clip_manager.feature.sidelayer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.doggy.clip_manager.core.data.operation.TrashEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V11 (R11) - part 2/2: TrashDialog's 복원/비우기 actions, per spec v2's
 * `TrashDialog(entries, onRestore, onEmpty, onDismiss)` signature.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrashDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val entries = listOf(
        TrashEntry(id = "id-1", name = "one.txt", originalParent = "/storage/one", archiveOrigin = null, deletedAtMillis = 1L),
        TrashEntry(id = "id-2", name = "two.txt", originalParent = "/storage/two", archiveOrigin = null, deletedAtMillis = 2L),
    )

    @Test
    fun restore_V11_invokesOnRestoreWithEntryId() {
        // A single-entry list keeps the "복원" affordance unambiguous for this assertion;
        // the two-entry list above is reserved for display/empty coverage.
        val singleEntry = listOf(entries[0])
        var restoredId: String? = null
        composeTestRule.setContent {
            TrashDialog(entries = singleEntry, onRestore = { restoredId = it }, onEmpty = {}, onDismiss = {})
        }

        composeTestRule.onNodeWithText("one.txt").assertExists()
        composeTestRule.onNodeWithText("복원").performClick()

        assertEquals("id-1", restoredId)
    }

    @Test
    fun empty_V11_invokesOnEmpty() {
        var emptied = false
        composeTestRule.setContent {
            TrashDialog(entries = entries, onRestore = {}, onEmpty = { emptied = true }, onDismiss = {})
        }

        composeTestRule.onNodeWithText("비우기").performClick()

        assertTrue(emptied)
    }
}
