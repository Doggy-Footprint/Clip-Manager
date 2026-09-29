package com.doggy.clip_manager.core.data.operation

import android.net.Uri
import java.io.File

sealed interface FileOperationSource {
    data class Local(val file: File) : FileOperationSource
    data class Content(val uri: Uri, val name: String) : FileOperationSource
    data class Archived(val archive: File, val path: String, val password: CharArray? = null) : FileOperationSource
}

sealed interface OperationDestination {
    data class Directory(val directory: File) : OperationDestination
    data class ArchiveDirectory(val archive: File, val path: String) : OperationDestination
}

enum class ConflictPolicy { ASK, SKIP, AUTO_RENAME, REPLACE }

data class OperationOutcome(
    val source: FileOperationSource,
    val destinationName: String? = null,
    val error: String? = null,
    val conflict: Boolean = false,
    val skipped: Boolean = false,
) {
    val succeeded: Boolean get() = error == null && !conflict && !skipped
}
