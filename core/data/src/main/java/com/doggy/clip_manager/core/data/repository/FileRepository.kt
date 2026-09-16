package com.doggy.clip_manager.core.data.repository

import com.doggy.clip_manager.core.model.FileEntry
import java.io.File

interface FileRepository {
    val rootDir: File

    suspend fun list(dir: File): List<FileEntry>
}
