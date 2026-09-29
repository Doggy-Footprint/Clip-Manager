package com.doggy.clip_manager.core.data.operation

import android.content.ContentResolver
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import javax.inject.Inject

open class FileOperationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archives: ArchiveRepository,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun copyOrMove(
        sources: List<FileOperationSource>,
        destination: OperationDestination,
        conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
        move: Boolean = false,
    ): List<OperationOutcome> = withContext(Dispatchers.IO) {
        sources.map { source -> copyOrMoveOne(source, destination, conflictPolicy, move) }
    }

    private suspend fun copyOrMoveOne(
        source: FileOperationSource,
        destination: OperationDestination,
        conflictPolicy: ConflictPolicy,
        move: Boolean,
    ): OperationOutcome {
        val baseName = sourceName(source) ?: return OperationOutcome(source, error = "이름을 확인할 수 없습니다")
        val existingNames = existingNamesAt(destination)
        var finalName = baseName
        if (existingNames.contains(baseName)) {
            when (conflictPolicy) {
                ConflictPolicy.ASK -> return OperationOutcome(source, conflict = true)
                ConflictPolicy.SKIP -> return OperationOutcome(source, skipped = true)
                ConflictPolicy.AUTO_RENAME -> finalName = uniqueName(baseName, existingNames)
                ConflictPolicy.REPLACE -> Unit
            }
        }
        return try {
            writeToDestination(source, destination, finalName)
            val destinationFile = (destination as? OperationDestination.Directory)?.let { File(it.directory, finalName) }
            if (destinationFile != null && !verify(source, destinationFile)) {
                destinationFile.deleteRecursively()
                return OperationOutcome(source, error = "검증 실패")
            }
            if (move) deleteSource(source)
            OperationOutcome(source, destinationName = finalName)
        } catch (e: ConsentRequiredException) {
            throw e
        } catch (e: Exception) {
            OperationOutcome(source, error = e.message ?: "작업 실패")
        }
    }

    private fun sourceName(source: FileOperationSource): String? = when (source) {
        is FileOperationSource.Local -> source.file.name
        is FileOperationSource.Content -> source.name
        is FileOperationSource.Archived -> source.path.trimEnd('/').substringAfterLast('/')
    }

    private suspend fun existingNamesAt(destination: OperationDestination): Set<String> = when (destination) {
        is OperationDestination.Directory -> destination.directory.list()?.toSet() ?: emptySet()
        is OperationDestination.ArchiveDirectory ->
            archives.list(destination.archive, destination.path).entries.map { it.path.substringAfterLast('/') }.toSet()
    }

    private fun uniqueName(name: String, existing: Set<String>): String {
        if (!existing.contains(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 2
        var candidate = "$base ($index)$ext"
        while (existing.contains(candidate)) {
            index++
            candidate = "$base ($index)$ext"
        }
        return candidate
    }

    private suspend fun writeToDestination(source: FileOperationSource, destination: OperationDestination, name: String) {
        val bytesOrDir = readSource(source)
        when (destination) {
            is OperationDestination.Directory -> {
                destination.directory.mkdirs()
                writeLocal(bytesOrDir, File(destination.directory, name))
            }
            is OperationDestination.ArchiveDirectory -> {
                val edits = toArchiveEdits(bytesOrDir, destination.path, name)
                val result = archives.edit(destination.archive, edits)
                if (result.isFailure) throw result.exceptionOrNull() ?: IllegalStateException("압축 쓰기 실패")
            }
        }
    }

    private sealed interface StagedPayload {
        data class SingleFile(val bytes: ByteArray) : StagedPayload
        data class Directory(val root: File) : StagedPayload
    }

    private suspend fun readSource(source: FileOperationSource): StagedPayload = when (source) {
        is FileOperationSource.Local -> if (source.file.isDirectory) {
            StagedPayload.Directory(source.file)
        } else {
            StagedPayload.SingleFile(source.file.readBytes())
        }
        is FileOperationSource.Content -> {
            val stream = resolver.openInputStream(source.uri) ?: throw IllegalStateException("URI를 열 수 없습니다")
            StagedPayload.SingleFile(stream.use { it.readBytes() })
        }
        is FileOperationSource.Archived -> {
            val listing = archives.list(source.archive, source.path, source.password)
            if (listing.entries.isEmpty() && listing.failureReason != null) {
                throw ArchiveException(listing.failureReason)
            }
            val isDirectory = listing.entries.isNotEmpty() || listing.failureReason == null &&
                archives.list(source.archive, source.path.substringBeforeLast('/', ""), source.password)
                    .entries.firstOrNull { it.path == source.path }?.isDirectory == true
            if (isDirectory) {
                val tempDir = Files.createTempDirectory("clip_stage").toFile()
                val result = archives.extract(source.archive, source.path, tempDir, source.password)
                result.exceptionOrNull()?.let { throw it }
                StagedPayload.Directory(File(tempDir, source.path.substringAfterLast('/')))
            } else {
                StagedPayload.SingleFile(archives.open(source.archive, source.path, source.password))
            }
        }
    }

    private fun writeLocal(payload: StagedPayload, target: File) {
        when (payload) {
            is StagedPayload.SingleFile -> target.writeBytes(payload.bytes)
            is StagedPayload.Directory -> {
                target.mkdirs()
                payload.root.copyRecursively(target, overwrite = true)
            }
        }
    }

    private fun toArchiveEdits(payload: StagedPayload, targetDir: String, name: String): List<ArchiveEdit> = when (payload) {
        is StagedPayload.SingleFile -> listOf(ArchiveEdit.Paste(targetDir, name, payload.bytes))
        is StagedPayload.Directory -> {
            val edits = mutableListOf<ArchiveEdit>()
            fun walk(dir: File, relative: String) {
                dir.listFiles()?.forEach { child ->
                    val childRelative = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                    if (child.isDirectory) {
                        walk(child, childRelative)
                    } else {
                        val dest = if (targetDir.isEmpty()) "$name/$childRelative" else "$targetDir/$name/$childRelative"
                        val destDir = dest.substringBeforeLast('/')
                        edits += ArchiveEdit.Paste(destDir, dest.substringAfterLast('/'), child.readBytes())
                    }
                }
            }
            walk(payload.root, "")
            edits
        }
    }

    private suspend fun deleteSource(source: FileOperationSource) {
        when (source) {
            is FileOperationSource.Local -> source.file.deleteRecursively()
            is FileOperationSource.Content -> resolver.deleteWithConsent(source.uri)
            is FileOperationSource.Archived -> {
                val result = archives.edit(source.archive, listOf(ArchiveEdit.Delete(source.path)))
                result.exceptionOrNull()?.let { throw it }
            }
        }
    }

    suspend fun zip(sources: List<FileOperationSource>, target: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = mutableListOf<WritableArchiveEntry>()
            for (source in sources) {
                val name = sourceName(source) ?: throw IllegalArgumentException("이름을 확인할 수 없습니다")
                when (val payload = readSource(source)) {
                    is StagedPayload.SingleFile -> entries += WritableArchiveEntry(name, false, payload.bytes)
                    is StagedPayload.Directory -> {
                        entries += WritableArchiveEntry(name, true, null)
                        payload.root.walkTopDown().filter { it != payload.root }.forEach { file ->
                            val relative = payload.root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                            entries += WritableArchiveEntry("$name/$relative", file.isDirectory, if (file.isDirectory) null else file.readBytes())
                        }
                    }
                }
            }
            val tempFile = File.createTempFile("clip_zip", ".tmp", target.parentFile)
            try {
                ZipFormatHandler.write(tempFile, entries)
                if (!tempFile.renameTo(target)) {
                    Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
            target
        }
    }

    /** Injection point for tests simulating a post-copy verification failure (C4). */
    protected open fun verify(source: FileOperationSource, destination: File): Boolean {
        if (!destination.exists() || !destination.canRead()) return false
        return when (source) {
            is FileOperationSource.Local -> {
                if (source.file.isDirectory) return destination.isDirectory
                if (destination.length() != source.file.length()) return false
                crc32(source.file) == crc32(destination)
            }
            is FileOperationSource.Content -> true
            // `verify` must stay non-suspend per the binding Signature, so the archive checksum
            // lookup is bridged with runBlocking; this only runs from the IO-dispatched copy path.
            is FileOperationSource.Archived -> {
                val expected = kotlinx.coroutines.runBlocking {
                    archives.checksum(source.archive, source.path, source.password)
                }
                expected == null || expected == crc32(destination)
            }
        }
    }

    private fun crc32(file: File): Long {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }
}
