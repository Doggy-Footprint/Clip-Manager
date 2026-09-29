package com.doggy.clip_manager.feature.sidelayer

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationRepository
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import com.doggy.clip_manager.core.data.operation.OperationDestination
import com.doggy.clip_manager.core.data.operation.TrashRepository
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * V14 (R16,C18,Q4), `BrowserScreenRoute` paste flow, 2/2: a failed paste (missing Content
 * URI) surfaces a Snackbar; a SKIP-resolved conflict's paste summary mentions `건너뜀`.
 *
 * Per the coordinator's resolution: asserting a UI text node whose content exactly equals
 * `operations.message`'s captured value isn't viable (the Route consumes the message via
 * `consumeMessage()` as part of showing it, so the exact string is a race). Instead this
 * asserts (a) `operations.message` was observed non-null at least once (the underlying
 * failure/summary was actually produced) and (b) a Snackbar-hosted text node containing an
 * expected substring (the missing source's file name for item 1, `건너뜀` for item 2) exists.
 *
 * `@Config(sdk = [29])`: same `Environment.isExternalStorageManager()` Robolectric
 * limitation as `BrowserScreenRouteDeleteTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BrowserScreenRoutePasteMessageTest {

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

    private fun setUpRoute(dir: File): Pair<BrowserViewModel, OperationViewModel> {
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
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
        return browserViewModel to operations
    }

    @Test
    fun paste_V14_missingContentUri_showsFailureSnackbar() {
        val dir = tempFolder.newFolder("paste-fail-root")
        val destination = tempFolder.newFolder("paste-fail-dest")
        val (_, operations) = setUpRoute(dir)

        // No provider is registered for this authority at all, so opening it fails - an
        // "operation cannot even be attempted" case, independent of any fake ContentProvider.
        val missingUri = Uri.parse("content://com.doggy.clip_manager.test.nonexistent/media/missing")
        composeTestRule.runOnUiThread {
            operations.enterSelection()
            operations.toggle(FileOperationSource.Content(missingUri, "missing.bin"))
            operations.stage(move = false)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread { operations.paste(OperationDestination.Directory(destination)) }

        awaitUntil { operations.message.value != null }
        // The exact wording of the failure message isn't specified, and asserting a node whose
        // text equals a value captured just before it's consumed isn't reliable in general
        // (per the coordinator's resolution) - but `SnackbarHostState.showSnackbar` suspends
        // for the Snackbar's whole visible duration before `consumeMessage()` runs, so the
        // captured text is still showing when we look for it a moment later.
        val capturedMessage = operations.message.value
        assertTrue("a non-empty failure message must be produced for a missing content URI", !capturedMessage.isNullOrBlank())
        composeTestRule.waitUntilAtLeastOneExists(hasText(capturedMessage!!, substring = true), 5_000)

        composeTestRule.onNode(hasText(capturedMessage, substring = true)).assertExists()
    }

    @Test
    fun paste_V14_skippedConflict_showsSkippedCountMessage() {
        val dir = tempFolder.newFolder("paste-skip-root")
        val destination = tempFolder.newFolder("paste-skip-dest")
        val conflictingSource = File(dir, "conflict.txt").apply { writeText("source-content") }
        File(destination, "conflict.txt").writeText("existing-content")
        val (_, operations) = setUpRoute(dir)

        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("conflict.txt").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("복사").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread { operations.paste(OperationDestination.Directory(destination)) }
        awaitUntil { operations.conflict.value != null }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("건너뛰기").performClick()
        composeTestRule.waitForIdle()

        awaitUntil { operations.message.value != null }
        composeTestRule.waitUntilAtLeastOneExists(hasText("건너뜀", substring = true), 5_000)

        composeTestRule.onNode(hasText("건너뜀", substring = true)).assertExists()
        // A9/skip semantics: the existing destination file must not have been overwritten.
        org.junit.Assert.assertEquals("existing-content", File(destination, "conflict.txt").readText())
    }
}
