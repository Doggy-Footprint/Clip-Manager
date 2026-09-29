package com.doggy.clip_manager.core.data.operation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEVEN_DAYS_MS = 604_800_000L

/**
 * V9 (R11,R12,C11,C12,Q8): TrashRepository with a fake clock (three-value boundary
 * analysis around the 7-day retention window) plus regular/archive-origin restore,
 * `_restore` name collisions, and an unavailable restore destination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrashRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val archives = ArchiveRepository()

    private var currentTime = 0L
    private val clock: () -> Long = { currentTime }

    private fun newRepository() = TrashRepository(context, archives, clock)

    @Test
    fun expire_V9_boundaryAges_retainsUnderSevenDaysAndExpiresAtAndOverSevenDays() = runTest {
        val repository = newRepository()
        val base = 10_000_000_000L

        currentTime = base + 1 // deleted 7d-1ms before the expire check below
        val underFile = tempFolder.newFile("under.bin").apply { writeBytes("under".toByteArray()) }
        repository.delete(listOf(FileOperationSource.Local(underFile)))
        val underId = repository.list().single { it.name == "under.bin" }.id

        currentTime = base // deleted exactly 7d before the expire check below
        val exactFile = tempFolder.newFile("exact.bin").apply { writeBytes("exact".toByteArray()) }
        repository.delete(listOf(FileOperationSource.Local(exactFile)))

        currentTime = base - 1 // deleted 7d+1ms before the expire check below
        val overFile = tempFolder.newFile("over.bin").apply { writeBytes("over".toByteArray()) }
        repository.delete(listOf(FileOperationSource.Local(overFile)))

        assertEquals(3, repository.list().size)

        currentTime = base + SEVEN_DAYS_MS
        val expiredCount = repository.expire()

        assertEquals(2, expiredCount)
        val remaining = repository.list()
        assertEquals(listOf("under.bin"), remaining.map { it.name })
        assertEquals(underId, remaining.single().id)
    }

    @Test
    fun restore_V9_regularEntry_restoresToOriginalParentAndRemovesFromTrash() = runTest {
        val repository = newRepository()
        val originalDir = tempFolder.newFolder("original-parent")
        val bytes = "regular-restore".toByteArray()
        val source = File(originalDir, "regular.bin").apply { writeBytes(bytes) }

        repository.delete(listOf(FileOperationSource.Local(source)))
        assertFalse(source.exists())
        val entry = repository.list().single { it.name == "regular.bin" }

        val result = repository.restore(entry.id)

        assertTrue("restore failed: ${result.exceptionOrNull()}", result.isSuccess)
        val restored = result.getOrThrow()
        assertEquals(originalDir.absolutePath, restored.parentFile?.absolutePath)
        assertArrayEquals(bytes, restored.readBytes())
        assertTrue(repository.list().none { it.id == entry.id })
    }

    @Test
    fun restore_V9_archiveOriginEntry_restoresIntoArchiveParentAsOrdinaryFile() = runTest {
        val repository = newRepository()
        val archiveDir = tempFolder.newFolder("archive-parent")
        val entryBytes = "archived-content".toByteArray()
        val archive = ArchiveFixtures.buildZip(File(archiveDir, "source.zip"), mapOf("inside.txt" to entryBytes))

        val outcomes = repository.delete(listOf(FileOperationSource.Archived(archive, "inside.txt")))
        assertTrue(outcomes.single().succeeded)
        val entry = repository.list().single()
        assertTrue("archive-origin entries must record their archive", entry.archiveOrigin != null)

        val result = repository.restore(entry.id)

        assertTrue("restore failed: ${result.exceptionOrNull()}", result.isSuccess)
        val restored = result.getOrThrow()
        assertEquals(archiveDir.absolutePath, restored.parentFile?.absolutePath)
        assertArrayEquals(entryBytes, restored.readBytes())
    }

    @Test
    fun restore_V9_nameCollisionAtDestination_usesRestoreSuffix() = runTest {
        val repository = newRepository()
        val originalDir = tempFolder.newFolder("collision-parent")
        val bytes = "collide-me".toByteArray()
        val source = File(originalDir, "collide.bin").apply { writeBytes(bytes) }
        repository.delete(listOf(FileOperationSource.Local(source)))
        val entry = repository.list().single()

        // Occupy the original name before restoring so the repository must rename on restore.
        File(originalDir, "collide.bin").writeBytes("someone-else-wrote-here".toByteArray())

        val result = repository.restore(entry.id)

        assertTrue("restore failed: ${result.exceptionOrNull()}", result.isSuccess)
        val restored = result.getOrThrow()
        assertTrue(
            "restored file name must carry a _restore suffix on collision, was ${restored.name}",
            restored.name.contains("_restore"),
        )
        assertArrayEquals(bytes, restored.readBytes())
        assertEquals("someone-else-wrote-here".toByteArray().toList(), File(originalDir, "collide.bin").readBytes().toList())
    }

    @Test
    fun restore_V9_unavailableDestination_keepsTrashEntryAndReportsFailure() = runTest {
        val repository = newRepository()
        val originalDir = tempFolder.newFolder("vanishing-parent")
        val bytes = "orphaned".toByteArray()
        val source = File(originalDir, "orphan.bin").apply { writeBytes(bytes) }
        repository.delete(listOf(FileOperationSource.Local(source)))
        val entry = repository.list().single()

        // Make the original parent directory unavailable as a restore destination: replace it
        // with a regular file of the same path so the directory can never be recreated.
        originalDir.deleteRecursively()
        originalDir.writeBytes(ByteArray(0))

        val result = repository.restore(entry.id)

        assertTrue(result.isFailure)
        val remaining = repository.list().singleOrNull { it.id == entry.id }
        assertTrue("trash entry and metadata must be kept when restore destination is unavailable", remaining != null)
        assertEquals(entry, remaining)
    }
}
