package com.doggy.clip_manager.core.testing.repository

import com.doggy.clip_manager.core.data.repository.FileRepository
import com.doggy.clip_manager.core.model.FileEntry
import java.io.File

class TestFileRepository(
    override val rootDir: File = File("/storage"),
    private val entriesByDir: Map<File, List<FileEntry>> = emptyMap(),
) : FileRepository {
    override suspend fun list(dir: File): List<FileEntry> = entriesByDir[dir].orEmpty()
}
