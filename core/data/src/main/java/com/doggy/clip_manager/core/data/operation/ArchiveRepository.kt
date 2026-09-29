package com.doggy.clip_manager.core.data.operation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

open class ArchiveRepository @Inject constructor() {

    fun isArchive(file: File): Boolean = ArchiveFormats.detect(file.name) != null

    suspend fun list(archive: File, path: String = "", password: CharArray? = null): ArchiveListing =
        withContext(Dispatchers.IO) {
            val format = ArchiveFormats.detect(archive.name)
                ?: return@withContext ArchiveListing(emptyList(), readOnly = true, failureReason = ArchiveFailure.UNSUPPORTED_METHOD)
            val handler = ArchiveFormats.handlerFor(format)
            try {
                val raw = handler.readEntries(archive, password)
                val safe = raw.filter {
                    ArchivePaths.isSafe(it.path) &&
                        (it.symlinkTarget == null || ArchivePaths.resolveSymlink(it.path, it.symlinkTarget) != null)
                }
                val hasUnsafe = safe.size != raw.size
                val dirPrefix = ArchivePaths.normalize(path)
                val direct = LinkedHashMap<String, ArchiveEntry>()
                for (entry in safe) {
                    val entryPath = ArchivePaths.normalize(entry.path)
                    if (entryPath.isEmpty() || entryPath == dirPrefix || !ArchivePaths.isUnder(entryPath, dirPrefix)) continue
                    val childName = ArchivePaths.childName(entryPath, dirPrefix)
                    if (childName.isEmpty()) continue
                    val childPath = if (dirPrefix.isEmpty()) childName else "$dirPrefix/$childName"
                    val isDirectChild = childPath == entryPath
                    val existing = direct[childPath]
                    if (existing != null) {
                        if (!isDirectChild && !existing.isDirectory) direct[childPath] = ArchiveEntry(childPath, true, 0)
                        continue
                    }
                    direct[childPath] = if (isDirectChild) {
                        ArchiveEntry(childPath, entry.isDirectory, entry.size)
                    } else {
                        ArchiveEntry(childPath, true, 0)
                    }
                }
                val encrypted = handler.isEncrypted(archive)
                ArchiveListing(direct.values.toList(), readOnly = encrypted || hasUnsafe, failureReason = if (hasUnsafe) ArchiveFailure.UNSAFE_ENTRY else null)
            } catch (e: ArchiveException) {
                ArchiveListing(emptyList(), readOnly = true, failureReason = e.failure)
            }
        }

    suspend fun open(archive: File, path: String, password: CharArray? = null): ByteArray =
        withContext(Dispatchers.IO) {
            if (!ArchivePaths.isSafe(path)) throw ArchiveException(ArchiveFailure.UNSAFE_ENTRY)
            val format = ArchiveFormats.detect(archive.name) ?: throw ArchiveException(ArchiveFailure.UNSUPPORTED_METHOD)
            ArchiveFormats.handlerFor(format).readBytes(archive, path, password)
        }

    suspend fun extract(archive: File, path: String, destination: File, password: CharArray? = null): Result<List<File>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val format = ArchiveFormats.detect(archive.name) ?: throw ArchiveException(ArchiveFailure.UNSUPPORTED_METHOD)
                val handler = ArchiveFormats.handlerFor(format)
                val raw = handler.readEntries(archive, password)
                val safe = raw.filter {
                    ArchivePaths.isSafe(it.path) &&
                        (it.symlinkTarget == null || ArchivePaths.resolveSymlink(it.path, it.symlinkTarget) != null)
                }
                if (safe.size != raw.size) throw ArchiveException(ArchiveFailure.UNSAFE_ENTRY)
                val prefix = ArchivePaths.normalize(path)
                val selected = safe.filter { ArchivePaths.isUnder(ArchivePaths.normalize(it.path), prefix) }
                if (selected.isEmpty()) throw ArchiveException(ArchiveFailure.CORRUPT)
                val tempDir = Files.createTempDirectory(archive.parentFile?.toPath() ?: destination.toPath(), "clip_extract").toFile()
                try {
                    val produced = mutableListOf<File>()
                    for (entry in selected) {
                        val entryPath = ArchivePaths.normalize(entry.path)
                        val relative = if (entryPath == prefix) entryPath.substringAfterLast('/') else entryPath.removePrefix("$prefix/")
                        val outFile = File(tempDir, relative)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.writeBytes(handler.readBytes(archive, entry.path, password))
                            produced += outFile
                        }
                    }
                    destination.mkdirs()
                    val moved = mutableListOf<File>()
                    tempDir.listFiles()?.forEach { child ->
                        val target = File(destination, child.name)
                        Files.move(child.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        moved += target
                    }
                    if (moved.isEmpty()) produced else moved
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

    open suspend fun checksum(archive: File, path: String, password: CharArray? = null): Long? =
        withContext(Dispatchers.IO) {
            if (!ArchivePaths.isSafe(path)) return@withContext null
            val format = ArchiveFormats.detect(archive.name) ?: return@withContext null
            ArchiveFormats.handlerFor(format).crc(archive, path, password)
        }

    suspend fun edit(archive: File, edits: List<ArchiveEdit>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val format = ArchiveFormats.detect(archive.name) ?: throw ArchiveException(ArchiveFailure.UNSUPPORTED_METHOD)
                val handler = ArchiveFormats.handlerFor(format)
                if (handler.isEncrypted(archive)) throw ArchiveException(ArchiveFailure.PASSWORD_REQUIRED)
                val raw = handler.readEntries(archive, null)
                if (raw.any { !ArchivePaths.isSafe(it.path) }) throw ArchiveException(ArchiveFailure.UNSAFE_ENTRY)

                data class Node(var isDirectory: Boolean, var bytes: ByteArray?)
                val entries = LinkedHashMap<String, Node>()
                for (entry in raw) {
                    val normalized = ArchivePaths.normalize(entry.path)
                    val bytes = if (entry.isDirectory) null else handler.readBytes(archive, entry.path, null)
                    entries[normalized] = Node(entry.isDirectory, bytes)
                }

                fun descendantsOf(dir: String): List<String> = entries.keys.filter { ArchivePaths.isUnder(it, dir) && it != dir }

                for (edit in edits) {
                    when (edit) {
                        is ArchiveEdit.Delete -> {
                            val target = ArchivePaths.normalize(edit.path)
                            entries.remove(target)
                            descendantsOf(target).forEach { entries.remove(it) }
                        }
                        is ArchiveEdit.Rename -> {
                            val from = ArchivePaths.normalize(edit.path)
                            val parent = from.substringBeforeLast('/', "")
                            val to = if (parent.isEmpty()) edit.newName else "$parent/${edit.newName}"
                            renameSubtree(entries, from, to)
                        }
                        is ArchiveEdit.Copy -> {
                            val from = ArchivePaths.normalize(edit.path)
                            val to = ArchivePaths.normalize(edit.target)
                            copySubtree(entries, from, to)
                        }
                        is ArchiveEdit.Move -> {
                            val from = ArchivePaths.normalize(edit.path)
                            val to = ArchivePaths.normalize(edit.target)
                            copySubtree(entries, from, to)
                            entries.remove(from)
                            descendantsOf(from).forEach { entries.remove(it) }
                        }
                        is ArchiveEdit.Paste -> {
                            val dir = ArchivePaths.normalize(edit.targetDir)
                            val target = if (dir.isEmpty()) edit.name else "$dir/${edit.name}"
                            entries[target] = Node(false, edit.bytes)
                        }
                        is ArchiveEdit.CreateZip -> {
                            val selectedPaths = edit.paths.map { ArchivePaths.normalize(it) }
                            val nested = selectedPaths.flatMap { p -> (listOf(p) + descendantsOf(p)) }.distinct()
                            val nestedEntries = nested.mapNotNull { p ->
                                val node = entries[p] ?: return@mapNotNull null
                                WritableArchiveEntry(p, node.isDirectory, node.bytes)
                            }
                            val nestedFile = Files.createTempFile(archive.parentFile?.toPath() ?: archive.toPath().parent, "nested", ".zip").toFile()
                            try {
                                ZipFormatHandler.write(nestedFile, nestedEntries)
                                entries[ArchivePaths.normalize(edit.target)] = Node(false, nestedFile.readBytes())
                            } finally {
                                nestedFile.delete()
                            }
                        }
                    }
                }

                val finalEntries = entries.map { (path, node) -> WritableArchiveEntry(path, node.isDirectory, node.bytes) }
                val tempFile = File.createTempFile(archive.nameWithoutExtension, ".tmp", archive.parentFile)
                try {
                    handler.write(tempFile, finalEntries)
                    beforeReplace(tempFile, archive)
                    if (!tempFile.renameTo(archive)) {
                        Files.move(tempFile.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    throw if (e is ArchiveException) e else ArchiveException(ArchiveFailure.UNSUPPORTED_METHOD)
                }
                Unit
            }
        }

    /** Injection point for tests simulating a failure right before the atomic replace (C10). */
    protected open fun beforeReplace(completed: File, original: File) = Unit
}

private fun <V> renameSubtree(entries: LinkedHashMap<String, V>, from: String, to: String) {
    val keys = entries.keys.filter { it == from || ArchivePaths.isUnder(it, from) }
    for (key in keys) {
        val suffix = key.removePrefix(from)
        val newKey = to + suffix
        val value = entries.remove(key)
        if (value != null) entries[newKey] = value
    }
}

private fun <V> copySubtree(entries: LinkedHashMap<String, V>, from: String, to: String) {
    val keys = entries.keys.filter { it == from || ArchivePaths.isUnder(it, from) }.toList()
    for (key in keys) {
        val suffix = key.removePrefix(from)
        val newKey = to + suffix
        entries[key]?.let { entries[newKey] = it }
    }
}
