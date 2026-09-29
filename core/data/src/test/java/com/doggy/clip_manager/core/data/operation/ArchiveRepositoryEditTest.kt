package com.doggy.clip_manager.core.data.operation

import java.io.File
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.ZipFile
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

/**
 * V7 (R9,C9,C10,Q5,Q8): ArchiveRepository.edit across six formats, exercising
 * rename/copy/move/delete/paste/createZip per format (36 coverage items) plus one
 * beforeReplace failure-injection scenario (37th item). A11 (spec v3): `list` only
 * returns direct children of the requested path, so nested-path presence/absence checks
 * (the move step) list the nested path's own parent directory rather than the root.
 * Reopening after each edit uses
 * ArchiveRepository.list/open (public API), and expected bytes come from the fixture
 * values chosen in this file, never from a prior call into the implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArchiveRepositoryEditTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val repository = ArchiveRepository()

    private val origBytes = "orig-content".toByteArray()
    private val nestedBytes = "nested-content".toByteArray()
    private val pastedBytes = "pasted-content".toByteArray()

    private fun initialEntries() = mapOf("orig.txt" to origBytes, "dir/nested.txt" to nestedBytes)

    @Test
    fun edit_V7_zip_allSixOperationsReflectedAndFormatUnchanged() = runTest {
        runFullScenario(ArchiveFixtures.buildZip(tempFolder.newFile("edit.zip"), initialEntries()), "zip")
    }

    @Test
    fun edit_V7_cbz_allSixOperationsReflectedAndFormatUnchanged() = runTest {
        runFullScenario(ArchiveFixtures.buildZip(tempFolder.newFile("edit.cbz"), initialEntries()), "zip")
    }

    @Test
    fun edit_V7_sevenZ_allSixOperationsReflectedAndFormatUnchanged() = runTest {
        runFullScenario(ArchiveFixtures.build7z(tempFolder.newFile("edit.7z"), initialEntries()), "7z")
    }

    @Test
    fun edit_V7_tarXz_allSixOperationsReflectedAndFormatUnchanged() = runTest {
        runFullScenario(ArchiveFixtures.buildTarXz(tempFolder.newFile("edit.tar.xz"), initialEntries()), "xz")
    }

    @Test
    fun edit_V7_tarGz_allSixOperationsReflectedAndFormatUnchanged() = runTest {
        runFullScenario(ArchiveFixtures.buildTarGz(tempFolder.newFile("edit.tar.gz"), initialEntries()), "gzip")
    }

    @Test
    fun edit_V7_tar_allSixOperationsReflectedAndFormatUnchanged() = runTest {
        runFullScenario(ArchiveFixtures.buildTar(tempFolder.newFile("edit.tar"), initialEntries()), "tar")
    }

    private suspend fun runFullScenario(archive: File, expectedMagic: String) {
        // rename
        var result = repository.edit(archive, listOf(ArchiveEdit.Rename("orig.txt", "renamed.txt")))
        assertTrue("rename failed: ${result.exceptionOrNull()}", result.isSuccess)
        var listing = repository.list(archive)
        assertFalse(listing.entries.any { it.path == "orig.txt" })
        assertTrue(listing.entries.any { it.path == "renamed.txt" })
        assertArrayEquals(origBytes, repository.open(archive, "renamed.txt"))

        // copy
        result = repository.edit(archive, listOf(ArchiveEdit.Copy("renamed.txt", "copy.txt")))
        assertTrue("copy failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertArrayEquals(origBytes, repository.open(archive, "copy.txt"))
        assertArrayEquals(origBytes, repository.open(archive, "renamed.txt"))

        // move
        // A11: `list` is one level deep, so "dir/nested.txt" is only visible by listing "dir",
        // never in a root listing (before or after the move).
        assertTrue(
            "setup sanity: dir/nested.txt must exist under dir/ before the move",
            repository.list(archive, "dir").entries.any { it.path == "dir/nested.txt" },
        )
        result = repository.edit(archive, listOf(ArchiveEdit.Move("dir/nested.txt", "moved.txt")))
        assertTrue("move failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertFalse(
            "dir/nested.txt must be gone from dir/ after the move",
            repository.list(archive, "dir").entries.any { it.path == "dir/nested.txt" },
        )
        assertArrayEquals(nestedBytes, repository.open(archive, "moved.txt"))

        // delete
        result = repository.edit(archive, listOf(ArchiveEdit.Delete("copy.txt")))
        assertTrue("delete failed: ${result.exceptionOrNull()}", result.isSuccess)
        listing = repository.list(archive)
        assertFalse(listing.entries.any { it.path == "copy.txt" })

        // paste
        result = repository.edit(archive, listOf(ArchiveEdit.Paste("", "pasted.txt", pastedBytes)))
        assertTrue("paste failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertArrayEquals(pastedBytes, repository.open(archive, "pasted.txt"))

        // createZip: independently re-parsed with zip4j, not trusted back through ArchiveRepository semantics
        result = repository.edit(archive, listOf(ArchiveEdit.CreateZip(listOf("renamed.txt", "moved.txt"), "bundle.zip")))
        assertTrue("createZip failed: ${result.exceptionOrNull()}", result.isSuccess)
        val bundleBytes = repository.open(archive, "bundle.zip")
        val bundleFile = tempFolder.newFile("bundle-${archive.name}.zip")
        bundleFile.writeBytes(bundleBytes)
        val bundleZip = ZipFile(bundleFile)
        val bundleNames = bundleZip.fileHeaders.map { it.fileName }.toSet()
        assertEquals(setOf("renamed.txt", "moved.txt"), bundleNames)
        assertArrayEquals(origBytes, bundleZip.getInputStream(bundleZip.getFileHeader("renamed.txt")).readBytes())
        assertArrayEquals(nestedBytes, bundleZip.getInputStream(bundleZip.getFileHeader("moved.txt")).readBytes())

        assertEquals("archive container format must be unchanged after edits", expectedMagic, detectMagic(archive))
    }

    @Test
    fun edit_V7_beforeReplaceFailureInjection_keepsOriginalBytesAndLeavesNoTempFiles() = runTest {
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("replace-fail.zip"), initialEntries())
        val originalBytes = archive.readBytes()
        val parentBefore = archive.parentFile!!.listFiles()!!.map { it.name }.toSet()

        val repositoryWithFailingReplace = object : ArchiveRepository() {
            public override fun beforeReplace(completed: File, original: File) {
                throw IllegalStateException("injected replace failure")
            }
        }

        val result = repositoryWithFailingReplace.edit(archive, listOf(ArchiveEdit.Delete("orig.txt")))

        assertTrue(result.isFailure)
        assertArrayEquals(originalBytes, archive.readBytes())
        val parentAfter = archive.parentFile!!.listFiles()!!.map { it.name }.toSet()
        assertEquals("no temp files should remain in the archive's directory", parentBefore, parentAfter)
    }

    private fun detectMagic(file: File): String {
        val head = file.inputStream().use { it.readNBytes(6) }
        return when {
            head.size >= 6 && head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() &&
                head[2] == 0xBC.toByte() && head[3] == 0xAF.toByte() -> "7z"
            head.size >= 2 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() -> "zip"
            head.size >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte() -> "gzip"
            head.size >= 6 && head[0] == 0xFD.toByte() && head[1] == 0x37.toByte() &&
                head[2] == 0x7A.toByte() && head[3] == 0x58.toByte() && head[4] == 0x5A.toByte() -> "xz"
            else -> "tar"
        }
    }
}
