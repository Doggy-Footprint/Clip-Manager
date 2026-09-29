package com.doggy.clip_manager.feature.sidelayer

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.media.MediaRepository
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationRepository
import com.doggy.clip_manager.core.data.operation.TrashRepository
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.core.model.MediaQuery
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * V1 (R1,R2,R14,C1,C14,C16,Q4) coverage item 7/7, media-grid variant (VIDEO only - see the
 * class doc on `MediaGridScreenSelectionTest` for why the other kinds aren't repeated),
 * through the real `MediaGridRoute`, mirroring `BrowserScreenRouteDeleteTest`'s fix for F1.
 * `@Config(sdk = [29])` for the same `Environment.isExternalStorageManager()` Robolectric
 * limitation documented on that file.
 *
 * `MediaRepository` (core.data.media) is a public interface, so this fakes it directly rather
 * than reaching for a real MediaStore-backed implementation: it re-derives its result set from
 * `TestPlainMediaContentProvider`'s live storage on every `query()` call, so a real
 * `MediaGridViewModel.reload()` triggered by the Route's `refreshTick` effect observes the
 * deletion for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MediaGridRouteDeleteTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        TestPlainMediaContentProvider.reset()
    }

    private fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() <= deadline) { "condition not met within ${timeoutMs}ms" }
            Thread.sleep(5)
        }
    }

    @Test
    fun delete_V1_viaMediaGridRoute_removesItemsAndClearsSelectionWithoutTestRefresh() {
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        Robolectric.buildContentProvider(TestPlainMediaContentProvider::class.java)
            .create(TestPlainMediaContentProvider.AUTHORITY)
        val uriA = TestPlainMediaContentProvider.register("route-del-a", "a".toByteArray())
        val uriB = TestPlainMediaContentProvider.register("route-del-b", "b".toByteArray())
        val entryA = MediaEntry(
            uri = uriA.toString(), filePath = null, displayName = "route-del-a-video",
            kind = MediaKind.VIDEO, bucketName = null, dateModifiedSeconds = 0, durationMs = null,
        )
        val entryB = MediaEntry(
            uri = uriB.toString(), filePath = null, displayName = "route-del-b-video",
            kind = MediaKind.VIDEO, bucketName = null, dateModifiedSeconds = 0, durationMs = null,
        )
        val mediaRepository = object : MediaRepository {
            override suspend fun query(query: MediaQuery): List<MediaEntry> =
                listOf(entryA, entryB).filter {
                    it.kind in query.kinds && TestPlainMediaContentProvider.stillPresent(it.uri.substringAfterLast('/'))
                }
        }
        val mediaGridViewModel = MediaGridViewModel(mediaRepository)
        val archives = ArchiveRepository()
        val trash = TrashRepository(context, archives) { System.currentTimeMillis() }
        val operations = OperationViewModel(FileOperationRepository(context, archives), archives, trash)

        composeTestRule.setContent {
            MediaGridRoute(
                kind = MediaKind.VIDEO,
                onEntrySelected = {},
                viewModel = mediaGridViewModel,
                operations = operations,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("선택").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(entryA.displayName).performClick()
        composeTestRule.onNodeWithText(entryB.displayName).performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, operations.selected.value.size)

        composeTestRule.onNodeWithText("삭제").performClick()
        composeTestRule.waitForIdle()

        // Only waits for the operation + Route's own refreshTick-driven reload to settle; this
        // test never assigns to any Compose state or re-queries the fake provider itself.
        awaitUntil {
            !operations.busy.value &&
                !TestPlainMediaContentProvider.stillPresent("route-del-a") &&
                !TestPlainMediaContentProvider.stillPresent("route-del-b")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(entryA.displayName).assertDoesNotExist()
        composeTestRule.onNodeWithText(entryB.displayName).assertDoesNotExist()
        assertTrue("selection must be cleared after delete completes", !operations.selectionMode.value)
        assertEquals(0, operations.selected.value.size)
        composeTestRule.onNodeWithText("선택").assertIsEnabled()
    }
}
