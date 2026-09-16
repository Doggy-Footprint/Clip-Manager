package com.doggy.clip_manager.core.data.repository

import android.os.Environment
import com.doggy.clip_manager.core.data.FileListing
import com.doggy.clip_manager.core.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

internal class LocalFileRepository @Inject constructor() : FileRepository {
    override val rootDir: File = Environment.getExternalStorageDirectory()

    override suspend fun list(dir: File): List<FileEntry> =
        withContext(Dispatchers.IO) { FileListing.list(dir) }
}
