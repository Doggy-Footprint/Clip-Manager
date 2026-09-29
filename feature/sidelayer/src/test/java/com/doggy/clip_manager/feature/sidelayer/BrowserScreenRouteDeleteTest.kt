package com.doggy.clip_manager.feature.sidelayer

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationRepository
import com.doggy.clip_manager.core.data.operation.TrashRepository
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * V1 (R1,R2,R14,C1,C14,C16,Q4) coverage item 7/7: "삭제 후 목록 갱신·선택 해제", observed
 * through the real `BrowserScreenRoute` (spec v6 correction, F1). The verifier's F1 finding
 * was that a `BrowserScreen`-level test which refreshes its own `uiState` after a delete
 * would still pass even if the production reload wiring (`OperationViewModel.refreshTick` ->
 * `BrowserViewModel.reload()`, R14) were removed. This test builds the real
 * `BrowserScreenRoute` with a real `BrowserViewModel` (over `TestDirFileRepository`, a
 * temp-dir-backed `FileRepository`), a real `ArchiveRepository`, and a real
 * `OperationViewModel`, and never touches any Compose state itself after `setContent` - the
 * list refresh and selection clear are entirely Route-owned.
 *
 * `@Config(sdk = [29])`, not 35: `BrowserScreenRoute` calls `rememberStoragePermissionState`,
 * which on API 30+ calls `android.os.Environment.isExternalStorageManager()`. Under
 * Robolectric 4.16.1 with `sdk = [35]` that real (unshadowed) framework method throws
 * `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0` at
 * `Environment.java:1492` on every call (verified empirically: a throwaway probe test hit
 * this before any of this file's logic ran) - Robolectric has no public shadow API to
 * configure it (no `ShadowEnvironment`/`ShadowStorageManager` setter for storage-manager
 * status exists in this version). There is no way to build `BrowserScreenRoute` under
 * `sdk = [35]` in this Robolectric version. `sdk = [29]` takes the pre-R permission branch
 * (`READ_EXTERNAL_STORAGE` via `ContextCompat.checkSelfPermission`), which Robolectric does
 * support granting via `ShadowApplication.grantPermissions`. Device-level API 35 behavior for
 * this flow remains covered separately by the review procedure (V4/V10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BrowserScreenRouteDeleteTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() <= deadline) { "condition not met within ${timeoutMs}ms" }
            Thread.sleep(5)
        }
    }

    @Test
    fun delete_V1_viaBrowserScreenRoute_removesItemsAndClearsSelectionWithoutTestRefresh() {
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)

        val dir = tempFolder.newFolder("route-root")
        val fileA = File(dir, "a.mp4").apply { writeBytes(ByteArray(1)) }
        val fileB = File(dir, "b.mp4").apply { writeBytes(ByteArray(1)) }

        val archives = ArchiveRepository()
        val browserViewModel = BrowserViewModel(TestDirFileRepository(dir), archives)
        val trash = TrashRepository(context, archives) { System.currentTimeMillis() }
        val operations = OperationViewModel(FileOperationRepository(context, archives), archives, trash)

        composeTestRule.setContent {
            BrowserScreenRoute(
                onOpenVideo = {},
                viewModel = browserViewModel,
                operations = operations,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("a.mp4").performClick()
        composeTestRule.onNodeWithText("b.mp4").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, operations.selected.value.size)

        composeTestRule.onNodeWithText("삭제").performClick()
        composeTestRule.waitForIdle()

        // Only waits for the operation + Route's own refreshTick-driven reload to settle; this
        // test never assigns to any Compose state or re-reads the directory itself.
        awaitUntil { !operations.busy.value && !fileA.exists() && !fileB.exists() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("a.mp4").assertDoesNotExist()
        composeTestRule.onNodeWithText("b.mp4").assertDoesNotExist()
        assertTrue("selection must be cleared after delete completes", !operations.selectionMode.value)
        assertEquals(0, operations.selected.value.size)
        composeTestRule.onNodeWithText("선택").assertIsEnabled()
    }
}
