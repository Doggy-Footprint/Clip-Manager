package com.doggy.clip_manager.feature.sidelayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.ConflictPolicy
import com.doggy.clip_manager.core.data.operation.FileOperationRepository
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import com.doggy.clip_manager.core.data.operation.OperationDestination
import com.doggy.clip_manager.core.data.operation.TrashRepository
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V3 (R5,R13,C3,C13,Q4): OperationViewModel's per-item conflict flow (spec v1) plus the
 * URI write/delete consent flow (spec v2, 7/7 total). Expectations come directly from R5
 * ("이름 충돌은 건별 질의. `건너뛰기`ㆍ`자동 이름 변경`만 `남은 충돌에 적용` 가능"), R13, C3/C13
 * and Q4, not from reading the implementation.
 *
 * OperationViewModel's mutating methods are fire-and-forget (they launch coroutines on
 * viewModelScope rather than suspending), so assertions poll the exposed StateFlows with
 * [awaitUntil] instead of relying on virtual test-dispatcher time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OperationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val archives = ArchiveRepository()

    private lateinit var destinationDir: File
    private lateinit var sourceFiles: Map<String, File>
    private lateinit var viewModel: OperationViewModel

    private val existingContent = mapOf(
        "a.txt" to "existing-a",
        "b.txt" to "existing-b",
        "c.txt" to "existing-c",
    )
    private val newContent = mapOf(
        "a.txt" to "new-a",
        "b.txt" to "new-b",
        "c.txt" to "new-c",
    )

    @Before
    fun setUp() {
        val sourceDir = tempFolder.newFolder("sources")
        destinationDir = tempFolder.newFolder("destination")
        existingContent.forEach { (name, content) -> File(destinationDir, name).writeText(content) }
        sourceFiles = newContent.mapValues { (name, content) -> File(sourceDir, name).apply { writeText(content) } }

        val repository = FileOperationRepository(context, archives)
        val trash = TrashRepository(context, archives) { System.currentTimeMillis() }
        viewModel = OperationViewModel(repository, archives, trash)

        viewModel.enterSelection()
        sourceFiles.values.forEach { viewModel.toggle(FileOperationSource.Local(it)) }
        viewModel.stage(move = false)
    }

    private fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() <= deadline) { "condition not met within ${timeoutMs}ms" }
            Thread.sleep(5)
        }
    }

    @Test
    fun paste_V3_conflict_promptsPerItem() {
        viewModel.paste(OperationDestination.Directory(destinationDir))

        awaitUntil { viewModel.conflict.value != null }

        assertTrue("conflicting source must be one of the staged sources", sourceFiles.values.any {
            (viewModel.conflict.value as? FileOperationSource.Local)?.file == it
        })
    }

    @Test
    fun resolveConflict_V3_skipWithApplyRemaining_persistsAcrossRemainingConflicts() {
        viewModel.paste(OperationDestination.Directory(destinationDir))
        awaitUntil { viewModel.conflict.value != null }

        viewModel.resolveConflict(ConflictPolicy.SKIP, applyRemaining = true)

        awaitUntil { !viewModel.busy.value && viewModel.conflict.value == null }
        existingContent.forEach { (name, content) ->
            assertEquals("skip must leave the existing destination file untouched", content, File(destinationDir, name).readText())
        }
    }

    @Test
    fun resolveConflict_V3_autoRenameWithApplyRemaining_persistsAcrossRemainingConflicts() {
        viewModel.paste(OperationDestination.Directory(destinationDir))
        awaitUntil { viewModel.conflict.value != null }

        viewModel.resolveConflict(ConflictPolicy.AUTO_RENAME, applyRemaining = true)

        awaitUntil { !viewModel.busy.value && viewModel.conflict.value == null }
        existingContent.forEach { (name, content) ->
            assertEquals("auto-rename must not overwrite the existing file", content, File(destinationDir, name).readText())
        }
        val renamedFiles = destinationDir.listFiles().orEmpty().filter { it.name !in existingContent.keys }
        assertEquals(
            "all three conflicting sources must have been auto-renamed without further prompts",
            3,
            renamedFiles.size,
        )
        assertEquals(newContent.values.sorted(), renamedFiles.map { it.readText() }.sorted())
    }

    @Test
    fun resolveConflict_V3_replace_doesNotApplyToRemainingConflicts() {
        viewModel.paste(OperationDestination.Directory(destinationDir))
        awaitUntil { viewModel.conflict.value != null }
        val firstConflictSource = viewModel.conflict.value

        viewModel.resolveConflict(ConflictPolicy.REPLACE, applyRemaining = true)

        // R5: only SKIP/AUTO_RENAME can carry `applyRemaining`; REPLACE must resolve just the
        // current item and re-prompt for the next conflicting source instead of persisting.
        awaitUntil { viewModel.conflict.value != null && viewModel.conflict.value != firstConflictSource }
        assertTrue(
            "a second, distinct conflict prompt is required after REPLACE despite applyRemaining=true",
            viewModel.conflict.value != firstConflictSource,
        )
    }

    @Test
    fun cancelConflict_V3_leavesRemainingSourcesUnprocessedAndNothingOverwritten() {
        viewModel.paste(OperationDestination.Directory(destinationDir))
        awaitUntil { viewModel.conflict.value != null }

        viewModel.cancelConflict()

        awaitUntil { !viewModel.busy.value }
        assertNull("conflict dialog must be dismissed", viewModel.conflict.value)
        existingContent.forEach { (name, content) ->
            assertEquals("cancel must not overwrite any destination file", content, File(destinationDir, name).readText())
        }
    }

    // ---- V3 (spec v2): URI write/delete consent flow ----

    @After
    fun tearDownConsentProvider() {
        TestConsentContentProvider.reset()
    }

    private fun newConsentViewModel(): OperationViewModel {
        val repository = FileOperationRepository(context, archives)
        val trash = TrashRepository(context, archives) { System.currentTimeMillis() }
        return OperationViewModel(repository, archives, trash)
    }

    @Test
    fun delete_V3_consentGranted_retriesAndSucceeds() {
        Robolectric.buildContentProvider(TestConsentContentProvider::class.java).create(TestConsentContentProvider.AUTHORITY)
        TestConsentContentProvider.alwaysDenied = false
        val uri = TestConsentContentProvider.register("granted-1", "consent-payload".toByteArray())
        val consentViewModel = newConsentViewModel()

        consentViewModel.delete(listOf(FileOperationSource.Content(uri, "granted.bin")))
        awaitUntil { consentViewModel.consentRequest.value != null }

        consentViewModel.onConsentResult(granted = true)

        awaitUntil { consentViewModel.consentRequest.value == null && !consentViewModel.busy.value }
        assertFalse("the delete must have been retried and succeeded after consent", TestConsentContentProvider.stillPresent("granted-1"))
    }

    @Test
    fun delete_V3_consentDenied_failsAndKeepsSource() {
        Robolectric.buildContentProvider(TestConsentContentProvider::class.java).create(TestConsentContentProvider.AUTHORITY)
        TestConsentContentProvider.alwaysDenied = true
        val uri = TestConsentContentProvider.register("denied-1", "consent-payload".toByteArray())
        val consentViewModel = newConsentViewModel()

        consentViewModel.delete(listOf(FileOperationSource.Content(uri, "denied.bin")))
        awaitUntil { consentViewModel.consentRequest.value != null }

        consentViewModel.onConsentResult(granted = false)

        awaitUntil { consentViewModel.consentRequest.value == null && !consentViewModel.busy.value }
        assertTrue("a denied consent must be reported as a failure/message, not silently dropped", consentViewModel.message.value != null)
        assertTrue("the source must be kept when consent is denied", TestConsentContentProvider.stillPresent("denied-1"))
    }
}
