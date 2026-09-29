package com.doggy.clip_manager.core.data.operation

import java.io.File
import kotlinx.coroutines.test.runTest
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
 * V6 (R8,R10,C7,C15): password-protected ZIP/CBZ/7z, corrupt archive, unsupported method,
 * and encrypted-archive edit rejection.
 *
 * The 7z fixture (`core/data/src/test/resources/archives/encrypted-aes.7z`, A10) cannot be
 * produced with the JVM libraries on this project's classpath (commons-compress cannot
 * write AES-encrypted 7z), so it was generated once with the `7zz` CLI (Homebrew sevenzip,
 * /opt/homebrew/bin/7zz) and committed as a binary fixture:
 * `7zz a -p'Sev3nZ-Pass!' -mhe=off encrypted-aes.7z a.txt dir/b.txt`
 * (`-mhe=off`: filenames/sizes stay listable without the password, matching ZIP/CBZ
 * behavior in the other cases below; content is still AES-encrypted). Contents: `a.txt` =
 * "seven-zip-secret-a" (18 bytes), `dir/b.txt` = "seven-zip-secret-b-nested" (25 bytes).
 *
 * V8 (R7,C8,Q6): unsafe archive entries (`../`, absolute path, symlink escaping the
 * destination) must be rejected with UNSAFE_ENTRY and must never write outside destination.
 *
 * A11 (spec v3): `list` returns only direct children of the requested path, with
 * intermediate folders synthesized as directory entries; see ArchiveRepositoryFormatsTest
 * for the full root/`dir` listing shape this fixture layout produces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArchiveRepositorySecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val repository = ArchiveRepository()
    private val entries = mapOf("a.txt" to "secret-a".toByteArray(), "dir/b.txt" to "secret-b".toByteArray())
    private val correctPassword = "correct-horse".toCharArray()
    private val wrongPassword = "wrong-password".toCharArray()

    // ---- V6: password success (readOnly, extracted bytes match) ----

    @Test
    fun list_V6_encryptedZip_correctPassword_readOnlyAndEntriesMatch() = runTest {
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("enc.zip"), entries, correctPassword)
        val originalBytes = archive.readBytes()

        val listing = repository.list(archive, password = correctPassword)

        assertEquals(null, listing.failureReason)
        assertTrue(listing.readOnly)
        // A11: `list` returns only direct children of the requested path.
        assertEquals(setOf("a.txt", "dir"), listing.entries.map { it.path }.toSet())
        val destination = tempFolder.newFolder("extract-enc-zip")
        val extracted = repository.extract(archive, "", destination, password = correctPassword)
        assertTrue(extracted.isSuccess)
        entries.forEach { (path, bytes) ->
            assertEquals(bytes.toList(), File(destination, path).readBytes().toList())
        }
        assertEquals("archive bytes must not change on read", originalBytes.toList(), archive.readBytes().toList())
    }

    @Test
    fun list_V6_encryptedCbz_correctPassword_readOnlyAndEntriesMatch() = runTest {
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("enc.cbz"), entries, correctPassword)

        val listing = repository.list(archive, password = correctPassword)

        assertEquals(null, listing.failureReason)
        assertTrue(listing.readOnly)
        assertEquals(setOf("a.txt", "dir"), listing.entries.map { it.path }.toSet())
    }

    // ---- V6: 7z password success (committed binary fixture, A10) ----

    private val sevenZPassword = "Sev3nZ-Pass!".toCharArray()
    private val sevenZWrongPassword = "wrong-7z-password".toCharArray()
    private val sevenZFixtureEntries = mapOf(
        "a.txt" to "seven-zip-secret-a".toByteArray(),
        "dir/b.txt" to "seven-zip-secret-b-nested".toByteArray(),
    )

    private fun copySevenZFixture(): File {
        val target = tempFolder.newFile("encrypted-aes.7z")
        javaClass.getResourceAsStream("/archives/encrypted-aes.7z")!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    @Test
    fun list_V6_encryptedSevenZ_correctPassword_readOnlyAndEntriesMatch() = runTest {
        val archive = copySevenZFixture()
        val originalBytes = archive.readBytes()

        val listing = repository.list(archive, password = sevenZPassword)

        assertEquals(null, listing.failureReason)
        assertTrue(listing.readOnly)
        assertEquals(setOf("a.txt", "dir"), listing.entries.map { it.path }.toSet())
        val destination = tempFolder.newFolder("extract-enc-7z")
        val extracted = repository.extract(archive, "", destination, password = sevenZPassword)
        assertTrue("extract should succeed: ${extracted.exceptionOrNull()}", extracted.isSuccess)
        sevenZFixtureEntries.forEach { (path, bytes) ->
            assertEquals(bytes.toList(), File(destination, path).readBytes().toList())
        }
        assertEquals(originalBytes.toList(), archive.readBytes().toList())
    }

    @Test
    fun list_V6_encryptedSevenZ_wrongPassword_reportsWrongPasswordAndKeepsBytes() = runTest {
        val archive = copySevenZFixture()
        val originalBytes = archive.readBytes()

        val listing = repository.list(archive, password = sevenZWrongPassword)

        assertEquals(ArchiveFailure.WRONG_PASSWORD, listing.failureReason)
        assertEquals(originalBytes.toList(), archive.readBytes().toList())
    }

    // ---- V6: wrong password (ZIP, CBZ); bytes unchanged ----

    @Test
    fun list_V6_encryptedZip_wrongPassword_reportsWrongPasswordAndKeepsBytes() = runTest {
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("wrong.zip"), entries, correctPassword)
        val originalBytes = archive.readBytes()

        val listing = repository.list(archive, password = wrongPassword)

        assertEquals(ArchiveFailure.WRONG_PASSWORD, listing.failureReason)
        assertEquals(originalBytes.toList(), archive.readBytes().toList())
    }

    @Test
    fun list_V6_encryptedCbz_wrongPassword_reportsWrongPasswordAndKeepsBytes() = runTest {
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("wrong.cbz"), entries, correctPassword)
        val originalBytes = archive.readBytes()

        val listing = repository.list(archive, password = wrongPassword)

        assertEquals(ArchiveFailure.WRONG_PASSWORD, listing.failureReason)
        assertEquals(originalBytes.toList(), archive.readBytes().toList())
    }

    // ---- V6: corrupt / unsupported method ----

    @Test
    fun list_V6_corruptZip_reportsCorruptAndKeepsBytesAndExtractsNothing() = runTest {
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("corrupt.zip"), entries)
        ArchiveFixtures.corrupt(archive)
        val corruptBytes = archive.readBytes()

        val listing = repository.list(archive)
        assertEquals(ArchiveFailure.CORRUPT, listing.failureReason)

        val destination = tempFolder.newFolder("extract-corrupt")
        val extracted = repository.extract(archive, "", destination)
        assertTrue(extracted.isFailure)
        assertTrue("no partial extraction allowed", destination.listFiles()?.isEmpty() != false)
        assertEquals(corruptBytes.toList(), archive.readBytes().toList())
    }

    @Test
    fun list_V6_unsupportedMethodZip_reportsUnsupportedMethodAndKeepsBytes() = runTest {
        val archive = ArchiveFixtures.buildUnsupportedMethodZip(tempFolder.newFile("unsupported.zip"))
        val originalBytes = archive.readBytes()

        val listing = repository.list(archive)

        assertEquals(ArchiveFailure.UNSUPPORTED_METHOD, listing.failureReason)
        assertEquals(originalBytes.toList(), archive.readBytes().toList())
    }

    // ---- V6: encrypted archive edit rejection (R8 blocks internal edit) ----

    @Test
    fun edit_V6_encryptedZip_editIsRejectedAndArchiveUnchanged() = runTest {
        val archive = ArchiveFixtures.buildZip(tempFolder.newFile("enc-edit.zip"), entries, correctPassword)
        val originalBytes = archive.readBytes()

        val result = repository.edit(archive, listOf(ArchiveEdit.Delete("a.txt")))

        assertTrue(result.isFailure)
        assertEquals(originalBytes.toList(), archive.readBytes().toList())
    }

    // ---- V8: unsafe entries (`../`, absolute path, symlink escaping destination) ----

    @Test
    fun extract_V8_dotDotEntry_reportsUnsafeEntryAndWritesNothingOutsideDestination() = runTest {
        val outsideMarker = tempFolder.newFolder("outside")
        val archive = ArchiveFixtures.buildZip(
            tempFolder.newFile("dotdot.zip"),
            mapOf("../escape.txt" to "payload".toByteArray()),
        )

        val listing = repository.list(archive)
        assertEquals(ArchiveFailure.UNSAFE_ENTRY, listing.failureReason)

        val destination = tempFolder.newFolder("extract-dotdot")
        val extracted = repository.extract(archive, "", destination)
        assertTrue(extracted.isFailure)
        assertFalse(File(outsideMarker, "escape.txt").exists())
        assertFalse(File(tempFolder.root, "escape.txt").exists())
    }

    @Test
    fun extract_V8_absolutePathEntry_reportsUnsafeEntryAndWritesNothingOutsideDestination() = runTest {
        val absoluteTarget = File(tempFolder.root, "absolute-escape.txt")
        val archive = ArchiveFixtures.buildZip(
            tempFolder.newFile("absolute.zip"),
            mapOf(absoluteTarget.absolutePath to "payload".toByteArray()),
        )

        val listing = repository.list(archive)
        assertEquals(ArchiveFailure.UNSAFE_ENTRY, listing.failureReason)

        val destination = tempFolder.newFolder("extract-absolute")
        val extracted = repository.extract(archive, "", destination)
        assertTrue(extracted.isFailure)
        assertFalse(absoluteTarget.exists())
    }

    @Test
    fun extract_V8_symlinkEntryEscapingDestination_reportsUnsafeEntryAndWritesNothingOutsideDestination() = runTest {
        val outsideTarget = tempFolder.newFile("symlink-target.txt")
        outsideTarget.writeText("outside-content")
        val tarWithSymlink = File(tempFolder.root, "symlink.tar")
        java.io.ByteArrayOutputStream().also { buffer ->
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(buffer).use { tar ->
                val symlinkEntry = org.apache.commons.compress.archivers.tar.TarArchiveEntry(
                    "escape-link",
                    org.apache.commons.compress.archivers.tar.TarConstants.LF_SYMLINK,
                )
                symlinkEntry.linkName = outsideTarget.absolutePath
                tar.putArchiveEntry(symlinkEntry)
                tar.closeArchiveEntry()
                tar.finish()
            }
            tarWithSymlink.writeBytes(buffer.toByteArray())
        }

        val listing = repository.list(tarWithSymlink)
        assertEquals(ArchiveFailure.UNSAFE_ENTRY, listing.failureReason)

        val destination = tempFolder.newFolder("extract-symlink")
        val extracted = repository.extract(tarWithSymlink, "", destination)
        assertTrue(extracted.isFailure)
        assertFalse(File(destination, "escape-link").let { it.exists() && it.canonicalFile != it })
    }
}
