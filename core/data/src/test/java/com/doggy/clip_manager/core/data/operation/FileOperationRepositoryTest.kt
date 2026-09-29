package com.doggy.clip_manager.core.data.operation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * V2 (R3,R4,C2,C4,C13,Q5,Q8): FileOperationRepository.copyOrMove decision table across
 * Local, Content, and Archived sources (spec v2, 6/6) - copy success, move success,
 * verification failure (injected via a `verify` override), permission denied / missing
 * content URI, archived-source move with a CRC checksum mismatch (injected via an
 * `ArchiveRepository.checksum` override, A7), and a SKIP-resolved conflict reporting
 * `skipped = true` / `succeeded == false` (A9). Expected bytes always come from fixtures
 * constructed in this file, never from a prior repository call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileOperationRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val archives = ArchiveRepository()

    @Before
    fun setUp() {
        TestMediaContentProvider.reset()
        Robolectric.buildContentProvider(TestMediaContentProvider::class.java)
            .create(TestMediaContentProvider.AUTHORITY)
    }

    @After
    fun tearDown() {
        TestMediaContentProvider.reset()
    }

    @Test
    fun copyOrMove_V2_localCopySuccess_destinationMatchesAndSourceKept() = runTest {
        val repository = FileOperationRepository(context, archives)
        val sourceBytes = "copy-me".toByteArray()
        val source = tempFolder.newFile("source.bin").apply { writeBytes(sourceBytes) }
        val destinationDir = tempFolder.newFolder("dest-copy")

        val outcomes = repository.copyOrMove(
            sources = listOf(FileOperationSource.Local(source)),
            destination = OperationDestination.Directory(destinationDir),
            move = false,
        )

        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single().succeeded)
        assertTrue(source.exists())
        assertArrayEquals(sourceBytes, source.readBytes())
        val destFile = File(destinationDir, outcomes.single().destinationName ?: source.name)
        assertTrue(destFile.exists())
        assertArrayEquals(sourceBytes, destFile.readBytes())
    }

    @Test
    fun copyOrMove_V2_localMoveSuccess_destinationMatchesAndSourceRemoved() = runTest {
        val repository = FileOperationRepository(context, archives)
        val sourceBytes = "move-me".toByteArray()
        val source = tempFolder.newFile("source-move.bin").apply { writeBytes(sourceBytes) }
        val destinationDir = tempFolder.newFolder("dest-move")

        val outcomes = repository.copyOrMove(
            sources = listOf(FileOperationSource.Local(source)),
            destination = OperationDestination.Directory(destinationDir),
            move = true,
        )

        assertTrue(outcomes.single().succeeded)
        assertFalse("source must be removed after a verified move", source.exists())
        val destFile = File(destinationDir, outcomes.single().destinationName ?: source.name)
        assertTrue(destFile.exists())
        assertArrayEquals(sourceBytes, destFile.readBytes())
    }

    @Test
    fun copyOrMove_V2_verificationFailureInjected_sourceKeptAndOutcomeFails() = runTest {
        val failingRepository = object : FileOperationRepository(context, archives) {
            public override fun verify(source: FileOperationSource, destination: File): Boolean = false
        }
        val sourceBytes = "must-survive".toByteArray()
        val source = tempFolder.newFile("source-verify-fail.bin").apply { writeBytes(sourceBytes) }
        val destinationDir = tempFolder.newFolder("dest-verify-fail")

        val outcomes = failingRepository.copyOrMove(
            sources = listOf(FileOperationSource.Local(source)),
            destination = OperationDestination.Directory(destinationDir),
            move = true,
        )

        val outcome = outcomes.single()
        assertFalse(outcome.succeeded)
        assertTrue(outcome.error != null)
        assertTrue("source must be kept when verification fails", source.exists())
        assertArrayEquals(sourceBytes, source.readBytes())
    }

    @Test
    fun copyOrMove_V2_contentSourcePermissionDenied_failsAndSourceUnaffected() = runTest {
        val repository = FileOperationRepository(context, archives)
        val uri = TestMediaContentProvider.registerPermissionDenied("denied-1")
        val destinationDir = tempFolder.newFolder("dest-denied")

        val outcomes = repository.copyOrMove(
            sources = listOf(FileOperationSource.Content(uri, "denied.bin")),
            destination = OperationDestination.Directory(destinationDir),
            move = false,
        )

        val outcome = outcomes.single()
        assertFalse(outcome.succeeded)
        assertTrue(outcome.error != null)
        assertTrue("no file should have been written for a denied source", destinationDir.listFiles()?.isEmpty() != false)
    }

    @Test
    fun copyOrMove_V2_contentSourceMissingUri_failsAndSourceUnaffected() = runTest {
        val repository = FileOperationRepository(context, archives)
        val uri = TestMediaContentProvider.missingUri("never-registered")
        val destinationDir = tempFolder.newFolder("dest-missing")

        val outcomes = repository.copyOrMove(
            sources = listOf(FileOperationSource.Content(uri, "missing.bin")),
            destination = OperationDestination.Directory(destinationDir),
            move = false,
        )

        val outcome = outcomes.single()
        assertFalse(outcome.succeeded)
        assertTrue(outcome.error != null)
        assertTrue("no file should have been written for a missing content URI", destinationDir.listFiles()?.isEmpty() != false)
    }

    @Test
    fun copyOrMove_V2_archivedSourceMoveChecksumMismatch_sourceKeptAndOutcomeFails() = runTest {
        // spec v3: `checksum` is now declared `open suspend fun` specifically as a test
        // injection seam (no behavior change), so A7's CRC-mismatch path is exercised here
        // directly rather than via the generic `verify` override used in the prior run.
        val archiveBytes = "archived-bytes".toByteArray()
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("crc-mismatch.zip"), mapOf("inside.txt" to archiveBytes))
        val originalArchiveBytes = archive.readBytes()
        val mismatchedArchives = object : ArchiveRepository() {
            override suspend fun checksum(archive: File, path: String, password: CharArray?): Long? = 0xDEADBEEFL
        }
        val repository = FileOperationRepository(context, mismatchedArchives)
        val destinationDir = tempFolder.newFolder("dest-archived-move")

        val outcomes = repository.copyOrMove(
            sources = listOf(FileOperationSource.Archived(archive, "inside.txt")),
            destination = OperationDestination.Directory(destinationDir),
            move = true,
        )

        val outcome = outcomes.single()
        assertFalse("a checksum mismatch must fail verification", outcome.succeeded)
        assertTrue(outcome.error != null)
        assertEquals(
            "the archive (the archived source's origin) must be untouched when verification fails",
            originalArchiveBytes.toList(),
            archive.readBytes().toList(),
        )
    }

    @Test
    fun copyOrMove_V2_skipConflictPolicy_marksOutcomeSkippedAndNotSucceeded() = runTest {
        val repository = FileOperationRepository(context, archives)
        val existingBytes = "already-here".toByteArray()
        val destinationDir = tempFolder.newFolder("dest-skip")
        File(destinationDir, "conflict.bin").writeBytes(existingBytes)
        val sourceBytes = "would-overwrite".toByteArray()
        val source = tempFolder.newFile("conflict.bin").apply { writeBytes(sourceBytes) }

        val outcomes = repository.copyOrMove(
            sources = listOf(FileOperationSource.Local(source)),
            destination = OperationDestination.Directory(destinationDir),
            conflictPolicy = ConflictPolicy.SKIP,
            move = false,
        )

        val outcome = outcomes.single()
        assertTrue("A9: a SKIP-resolved conflict is reported via `skipped`, not `succeeded`", outcome.skipped)
        assertFalse(outcome.succeeded)
        assertEquals(existingBytes.toList(), File(destinationDir, "conflict.bin").readBytes().toList())
        assertTrue(source.exists())
    }

    @Test
    fun copyOrMove_V2_contentSourceCopySuccess_destinationMatchesFixtureBytes() = runTest {
        val repository = FileOperationRepository(context, archives)
        val bytes = "content-copy".toByteArray()
        val uri = TestMediaContentProvider.register("ok-1", "ok.bin", bytes)
        val destinationDir = tempFolder.newFolder("dest-content-copy")

        val outcomes = repository.copyOrMove(
            sources = listOf(FileOperationSource.Content(uri, "ok.bin")),
            destination = OperationDestination.Directory(destinationDir),
            move = false,
        )

        val outcome = outcomes.single()
        assertTrue(outcome.succeeded)
        val destFile = File(destinationDir, outcome.destinationName ?: "ok.bin")
        assertTrue(destFile.exists())
        assertArrayEquals(bytes, destFile.readBytes())
    }
}
