package com.doggy.clip_manager.feature.sidelayer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationRepository
import com.doggy.clip_manager.core.data.operation.TrashRepository
import com.doggy.clip_manager.core.model.FileEntry
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V1 (R1,R2,R14,C1,C14,C16,Q4) - file tab (BrowserScreen). Six of the seven state-transition
 * coverage items (spec v4/v6, item numbering below): 선택 진입, 토글, 해제, 화면 이탈, 빈 선택
 * 비활성, 길게 누르기 메뉴. Observation is by the exact labels declared in the spec's UI 관찰
 * 라벨 list, not by inspecting internal state.
 *
 * `onEntryLongClick`/`operations` are new optional BrowserScreen parameters from spec v2;
 * this file targets `internal fun BrowserScreen(...)` directly since it is internal to
 * feature.sidelayer.
 *
 * Coverage item 7 (C16/R14, "삭제 후 목록 갱신·선택 해제") is NOT covered by this file: the
 * verifier's F1 finding was that a `BrowserScreen`-level test can only observe the delete
 * itself, and would still pass if the production reload wiring were removed, because
 * `BrowserScreen` only renders whatever `uiState` its caller supplies - it has no reload
 * logic of its own. Item 7 now lives in `BrowserScreenRouteDeleteTest.kt`, which drives the
 * real `BrowserScreenRoute` (real `BrowserViewModel`/`OperationViewModel`) so the
 * `refreshTick`-driven reload is exercised for real, without the test updating any state
 * itself. See that file for the full rationale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserScreenSelectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var operations: OperationViewModel
    private lateinit var entryA: File
    private lateinit var entryB: File
    private lateinit var uiState: BrowserUiState.Success

    // A mutable delegate, not a second setContent call: composeTestRule.setContent may only be
    // invoked once per test (ComponentActivity rejects setting content twice), so per-test
    // customization of onEntryLongClick is threaded through this var instead.
    private var onEntryLongClick: (FileEntry) -> Unit = {}

    @Before
    fun setUp() {
        val archives = ArchiveRepository()
        val trash = TrashRepository(context, archives) { System.currentTimeMillis() }
        operations = OperationViewModel(FileOperationRepository(context, archives), archives, trash)

        val dir = tempFolder.newFolder("browser-root")
        entryA = File(dir, "a.mp4").apply { writeBytes(ByteArray(1)) }
        entryB = File(dir, "b.mp4").apply { writeBytes(ByteArray(1)) }
        uiState = BrowserUiState.Success(
            currentDir = dir,
            isAtRoot = true,
            entries = listOf(
                FileEntry(entryA, isDirectory = false, isVideo = true),
                FileEntry(entryB, isDirectory = false, isVideo = true),
            ),
        )

        composeTestRule.setContent {
            BrowserScreen(
                uiState = uiState,
                permissionGranted = true,
                onRequestPermission = {},
                onEntryClick = {},
                onNavigateUp = {},
                onEntryLongClick = { onEntryLongClick(it) },
                operations = operations,
            )
        }
    }

    @Test
    fun selectButton_V1_entersSelectionMode() {
        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()

        assertTrue(operations.selectionMode.value)
        composeTestRule.onNodeWithText("선택 해제 (0)").assertIsEnabled()
    }

    @Test
    fun tappingItems_V1_togglesSelectionCount() {
        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(entryA.name).performClick()
        composeTestRule.onNodeWithText(entryB.name).performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, operations.selected.value.size)
        composeTestRule.onNodeWithText("선택 해제 (2)").assertIsEnabled()
    }

    @Test
    fun clearButton_V1_deselectsAndExitsSelectionMode() {
        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(entryA.name).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("선택 해제 (1)").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, operations.selected.value.size)
        assertTrue(!operations.selectionMode.value)
        composeTestRule.onNodeWithText("선택").assertIsEnabled()
    }

    @Test
    fun leaveScreen_V1_clearsSelection() {
        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(entryA.name).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, operations.selected.value.size)

        composeTestRule.runOnUiThread { operations.leaveScreen() }
        composeTestRule.waitForIdle()

        assertTrue(!operations.selectionMode.value)
        assertEquals(0, operations.selected.value.size)
    }

    @Test
    fun emptySelection_V1_disablesBottomBarActions() {
        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("복사").assertIsNotEnabled()
        composeTestRule.onNodeWithText("이동").assertIsNotEnabled()
        composeTestRule.onNodeWithText("ZIP").assertIsNotEnabled()
        composeTestRule.onNodeWithText("삭제").assertIsNotEnabled()
    }

    @Test
    fun longClickItem_V1_opensItemActionMenuWithoutEnteringSelectionMode() {
        // R1 (spec v3, T2): long-press opens the item's own three-dot action menu (복사/이동/
        // ZIP/삭제) and invokes `onEntryLongClick`; it must NOT enter selection mode.
        var longClicked: FileEntry? = null
        onEntryLongClick = { longClicked = it }

        composeTestRule.onNodeWithText(entryA.name).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        assertEquals(entryA, longClicked?.file)
        assertTrue("long-press must not enter selection mode", !operations.selectionMode.value)
        composeTestRule.onNodeWithText("선택").assertIsEnabled()
        composeTestRule.onNodeWithText("복사").assertIsDisplayed()
        composeTestRule.onNodeWithText("이동").assertIsDisplayed()
        composeTestRule.onNodeWithText("ZIP").assertIsDisplayed()
        composeTestRule.onNodeWithText("삭제").assertIsDisplayed()
    }
}
