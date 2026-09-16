package com.doggy.clip_manager.browser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileListingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun isVideo_C1_normal_recognizesVideoExtensionsCaseInsensitively() {
        val names = listOf(
            "a.ts", "a.m2ts", "a.mts", "a.mp4", "a.mkv", "a.avi",
            "a.mov", "a.webm", "a.flv", "a.wmv", "a.3gp", "A.TS"
        )

        val results = names.map { FileListing.isVideo(it) }

        assertEquals(List(names.size) { true }, results)
    }

    @Test
    fun isVideo_C2_normal_rejectsNonVideoNames() {
        val names = listOf("a.txt", "a.jpg", "ts", "a.ts.bak", "")

        val results = names.map { FileListing.isVideo(it) }

        assertEquals(List(names.size) { false }, results)
    }

    @Test
    fun list_C3_normal_ordersDirectoriesFirstThenCaseInsensitiveName() {
        val dir = tempFolder.newFolder("root")
        File(dir, "b.ts").createNewFile()
        File(dir, "a.txt").createNewFile()
        File(dir, "Z").mkdir()
        File(dir, "a").mkdir()

        val entries = FileListing.list(dir)

        assertEquals(listOf("a", "Z", "a.txt", "b.ts"), entries.map { it.file.name })
        assertEquals(listOf(true, true, false, false), entries.map { it.isDirectory })
        assertEquals(listOf(false, false, false, true), entries.map { it.isVideo })
    }

    @Test
    fun list_C4_edge_excludesHiddenFilesAndDirectories() {
        val dir = tempFolder.newFolder("withHidden")
        File(dir, ".nomedia").createNewFile()
        File(dir, ".hidden").mkdir()
        File(dir, "visible.mp4").createNewFile()

        val entries = FileListing.list(dir)

        assertEquals(listOf("visible.mp4"), entries.map { it.file.name })
    }

    @Test
    fun list_C5_error_nonexistentPathReturnsEmptyListWithoutThrowing() {
        val nonexistent = File(tempFolder.root, "does-not-exist")

        val entries = FileListing.list(nonexistent)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun list_C5_error_regularFileReturnsEmptyListWithoutThrowing() {
        val file = tempFolder.newFile("regular.txt")

        val entries = FileListing.list(file)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun list_C5_error_unreadableDirectoryReturnsEmptyListWithoutThrowing() {
        val dir = tempFolder.newFolder("unreadable")
        File(dir, "child.txt").createNewFile()
        val madeUnreadable = dir.setReadable(false, false)
        try {
            Assume.assumeTrue(madeUnreadable)
            Assume.assumeTrue(dir.listFiles() == null)

            val entries = FileListing.list(dir)

            assertTrue(entries.isEmpty())
        } finally {
            dir.setReadable(true, false)
        }
    }
}
