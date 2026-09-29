package com.doggy.clip_manager.feature.sidelayer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationRepository
import com.doggy.clip_manager.core.data.operation.TrashRepository
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V1 (R1,R2,R14,C1,C14,C16,Q4) - media grid tabs (VIDEO, AUDIO, IMAGE), same six
 * state-transition coverage items as BrowserScreenSelectionTest, run once per media kind
 * ("탭별 1회").
 *
 * Coverage item 7 (C16/R14) is NOT covered by this file for the same reason as
 * `BrowserScreenSelectionTest`: `MediaGridScreen` itself has no reload logic, so a test at
 * this layer can only prove the delete happened, not that the screen would actually refresh
 * in production (verifier F1). Item 7's media-grid variant now lives in
 * `MediaGridRouteDeleteTest.kt`, driving the real `MediaGridRoute` (real
 * `MediaGridViewModel`/`OperationViewModel`) so its `refreshTick`-driven reload is exercised
 * for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaGridScreenSelectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun newOperationsViewModel(): OperationViewModel {
        val archives = ArchiveRepository()
        val trash = TrashRepository(context, archives) { System.currentTimeMillis() }
        return OperationViewModel(FileOperationRepository(context, archives), archives, trash)
    }

    private fun entriesFor(kind: MediaKind) = listOf(
        MediaEntry(
            uri = "content://media/$kind/1",
            filePath = null,
            displayName = "entry-a-$kind",
            kind = kind,
            bucketName = null,
            dateModifiedSeconds = 0,
            durationMs = null,
        ),
        MediaEntry(
            uri = "content://media/$kind/2",
            filePath = null,
            displayName = "entry-b-$kind",
            kind = kind,
            bucketName = null,
            dateModifiedSeconds = 0,
            durationMs = null,
        ),
    )

    // A mutable delegate, not a second setContent call: composeTestRule.setContent may only be
    // invoked once per test (ComponentActivity rejects setting content twice), so the
    // long-press phase below swaps this var instead of recomposing with a new callback.
    private var onEntryLongClick: (MediaEntry) -> Unit = {}

    private fun ComposeContentTestRule.setMediaGrid(operations: OperationViewModel, entries: List<MediaEntry>) {
        setContent {
            MediaGridScreen(
                uiState = MediaGridUiState.Success(entries),
                permissionGranted = true,
                onRequestPermission = {},
                onEntryClick = {},
                onEntryLongClick = { onEntryLongClick(it) },
                operations = operations,
            )
        }
    }

    private fun runSixItemScenario(kind: MediaKind) {
        val operations = newOperationsViewModel()
        val entries = entriesFor(kind)
        onEntryLongClick = {}
        composeTestRule.setMediaGrid(operations, entries)

        // 선택 진입
        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()
        assertTrue(operations.selectionMode.value)
        composeTestRule.onNodeWithText("선택 해제 (0)").assertIsEnabled()

        // 빈 선택 비활성
        composeTestRule.onNodeWithText("복사").assertIsNotEnabled()
        composeTestRule.onNodeWithText("이동").assertIsNotEnabled()
        composeTestRule.onNodeWithText("ZIP").assertIsNotEnabled()
        composeTestRule.onNodeWithText("삭제").assertIsNotEnabled()

        // 토글
        composeTestRule.onNodeWithText(entries[0].displayName).performClick()
        composeTestRule.onNodeWithText(entries[1].displayName).performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, operations.selected.value.size)
        composeTestRule.onNodeWithText("선택 해제 (2)").assertIsEnabled()

        // 해제
        composeTestRule.onNodeWithText("선택 해제 (2)").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, operations.selected.value.size)
        assertTrue(!operations.selectionMode.value)

        // 화면 이탈
        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(entries[0].displayName).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, operations.selected.value.size)
        composeTestRule.runOnUiThread { operations.leaveScreen() }
        composeTestRule.waitForIdle()
        assertTrue(!operations.selectionMode.value)
        assertEquals(0, operations.selected.value.size)

        // 길게 누르기 메뉴: R1 (spec v3, T2) - opens the item's action menu (복사/이동/ZIP/삭제),
        // invokes onEntryLongClick, and must NOT enter selection mode.
        var longClicked: MediaEntry? = null
        onEntryLongClick = { longClicked = it }
        composeTestRule.onNodeWithText(entries[0].displayName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        assertEquals(entries[0].uri, longClicked?.uri)
        assertTrue("long-press must not enter selection mode", !operations.selectionMode.value)
        composeTestRule.onNodeWithText("선택").assertIsEnabled()
        composeTestRule.onNodeWithText("복사").assertIsDisplayed()
        composeTestRule.onNodeWithText("이동").assertIsDisplayed()
        composeTestRule.onNodeWithText("ZIP").assertIsDisplayed()
        composeTestRule.onNodeWithText("삭제").assertIsDisplayed()
    }

    @Test
    fun video_V1_sixCoverageItems() = runSixItemScenario(MediaKind.VIDEO)

    @Test
    fun audio_V1_sixCoverageItems() = runSixItemScenario(MediaKind.AUDIO)

    @Test
    fun image_V1_sixCoverageItems() = runSixItemScenario(MediaKind.IMAGE)
}
