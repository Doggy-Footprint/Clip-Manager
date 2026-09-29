package com.doggy.clip_manager.feature.sidelayer

import com.doggy.clip_manager.core.data.FileListing
import com.doggy.clip_manager.core.data.repository.FileRepository
import java.io.File

/**
 * A real temp-dir-backed `FileRepository`: `list` always re-reads the given directory from
 * disk (via the same `FileListing` the production `FileRepository` implementation uses), so
 * a `BrowserViewModel` built on top of it observes real filesystem changes - unlike
 * `core.testing.repository.TestFileRepository`, which serves a fixed, test-supplied map and
 * would not reflect a delete that actually happened on disk.
 */
class TestDirFileRepository(override val rootDir: File) : FileRepository {
    override suspend fun list(dir: File) = FileListing.list(dir)
}
