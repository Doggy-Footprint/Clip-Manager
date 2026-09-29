package com.doggy.clip_manager.core.data.operation

data class ArchiveEntry(val path: String, val isDirectory: Boolean, val size: Long)

data class ArchiveListing(
    val entries: List<ArchiveEntry>,
    val readOnly: Boolean,
    val failureReason: ArchiveFailure?,
)

enum class ArchiveFailure { PASSWORD_REQUIRED, WRONG_PASSWORD, CORRUPT, UNSUPPORTED_METHOD, UNSAFE_ENTRY }

class ArchiveException(val failure: ArchiveFailure) : Exception(failure.name)

sealed interface ArchiveEdit {
    data class Rename(val path: String, val newName: String) : ArchiveEdit
    data class Copy(val path: String, val target: String) : ArchiveEdit
    data class Move(val path: String, val target: String) : ArchiveEdit
    data class Delete(val path: String) : ArchiveEdit
    data class Paste(val targetDir: String, val name: String, val bytes: ByteArray) : ArchiveEdit
    data class CreateZip(val paths: List<String>, val target: String) : ArchiveEdit
}
