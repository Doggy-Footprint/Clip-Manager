package com.doggy.clip_manager.core.data.operation

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V5 (R7,C6): ArchiveRepository.list/extract across ZIP, CBZ, 7z, tar.xz, tar.gz, tar.
 * Fixtures are built directly with zip4j/commons-compress/xz (ArchiveFixtures), independent
 * of ArchiveRepository, so the expected names/bytes never come from the implementation.
 *
 * A11 (spec v3): `list(archive, path)` returns only the direct children of `path`, with
 * intermediate folders synthesized as directory entries even when the underlying archive
 * has no explicit directory entry for them - so listing the root of a fixture containing
 * `a.txt` and `dir/b.txt` yields `a.txt` (file) and `dir` (directory), not `dir/b.txt`
 * itself; `dir/b.txt` only shows up when listing `dir`. `extract`/`open` are unaffected
 * (still full recursive content), so their assertions are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArchiveRepositoryFormatsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val repository = ArchiveRepository()

    private val fixtureEntries = mapOf(
        "a.txt" to "hello-a".toByteArray(),
        "dir/b.txt" to "hello-b-in-dir".toByteArray(),
    )

    @Test
    fun listAndExtract_V5_zip_namesAndBytesMatchFixture() = runTest {
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("case.zip"), fixtureEntries)
        assertListingAndExtraction(archive)
    }

    @Test
    fun listAndExtract_V5_cbz_namesAndBytesMatchFixture() = runTest {
        // A1: CBZ is treated as a ZIP container.
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("case.cbz"), fixtureEntries)
        assertListingAndExtraction(archive)
    }

    @Test
    fun listAndExtract_V5_sevenZ_namesAndBytesMatchFixture() = runTest {
        val archive = ArchiveFixtures.build7z(tempFolder.newFile("case.7z"), fixtureEntries)
        assertListingAndExtraction(archive)
    }

    @Test
    fun listAndExtract_V5_tarXz_namesAndBytesMatchFixture() = runTest {
        val archive = ArchiveFixtures.buildTarXz(tempFolder.newFile("case.tar.xz"), fixtureEntries)
        assertListingAndExtraction(archive)
    }

    @Test
    fun listAndExtract_V5_tarGz_namesAndBytesMatchFixture() = runTest {
        val archive = ArchiveFixtures.buildTarGz(tempFolder.newFile("case.tar.gz"), fixtureEntries)
        assertListingAndExtraction(archive)
    }

    @Test
    fun listAndExtract_V5_tar_namesAndBytesMatchFixture() = runTest {
        val archive = ArchiveFixtures.buildTar(tempFolder.newFile("case.tar"), fixtureEntries)
        assertListingAndExtraction(archive)
    }

    private suspend fun assertListingAndExtraction(archive: File) {
        val rootListing = repository.list(archive)
        assertEquals(null, rootListing.failureReason)
        assertEquals(setOf("a.txt", "dir"), rootListing.entries.map { it.path }.toSet())
        assertEquals(false, rootListing.entries.single { it.path == "a.txt" }.isDirectory)
        assertEquals(true, rootListing.entries.single { it.path == "dir" }.isDirectory)

        val dirListing = repository.list(archive, "dir")
        assertEquals(null, dirListing.failureReason)
        assertEquals(setOf("dir/b.txt"), dirListing.entries.map { it.path }.toSet())
        assertEquals(false, dirListing.entries.single().isDirectory)

        val destination = tempFolder.newFolder("extract-${archive.name}")
        val result = repository.extract(archive, "", destination)
        assertTrue("extract should succeed for ${archive.name}: ${result.exceptionOrNull()}", result.isSuccess)

        fixtureEntries.forEach { (path, bytes) ->
            val extracted = File(destination, path)
            assertTrue("expected $path to be extracted under ${destination}", extracted.exists())
            assertEquals(bytes.toList(), extracted.readBytes().toList())
        }
    }
}
