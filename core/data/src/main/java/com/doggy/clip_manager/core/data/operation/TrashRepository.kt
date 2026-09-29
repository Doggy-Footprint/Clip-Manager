package com.doggy.clip_manager.core.data.operation

import android.content.ContentResolver
import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.UUID
import javax.inject.Inject

data class TrashEntry(
    val id: String,
    val name: String,
    val originalParent: String,
    val archiveOrigin: String?,
    val deletedAtMillis: Long,
)

/** Age threshold for automatic expiry: 7 days in milliseconds (R11). */
private const val TRASH_MAX_AGE_MILLIS = 604_800_000L

class TrashRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archives: ArchiveRepository,
    private val clock: () -> Long,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** `.clip_trash/` lives under the shared storage root so it stays a hidden sibling of browsable folders (A3). */
    private val trashDir: File = File(Environment.getExternalStorageDirectory(), ".clip_trash")
    private val metadataFile: File = File(trashDir, "metadata.json")

    suspend fun list(): List<TrashEntry> = withContext(Dispatchers.IO) { readMetadata() }

    suspend fun delete(sources: List<FileOperationSource>): List<OperationOutcome> = withContext(Dispatchers.IO) {
        trashDir.mkdirs()
        val metadata = readMetadata().toMutableList()
        try {
            sources.map { source -> deleteOne(source, metadata) }
        } finally {
            // Persists whatever completed before a ConsentRequiredException interrupts the batch.
            writeMetadata(metadata)
        }
    }

    private suspend fun deleteOne(source: FileOperationSource, metadata: MutableList<TrashEntry>): OperationOutcome {
        val name = when (source) {
            is FileOperationSource.Local -> source.file.name
            is FileOperationSource.Content -> source.name
            is FileOperationSource.Archived -> source.path.trimEnd('/').substringAfterLast('/')
        }
        val id = UUID.randomUUID().toString()
        val payload = File(trashDir, "${id}_$name")
        return try {
            when (source) {
                is FileOperationSource.Local -> {
                    source.file.copyRecursively(payload, overwrite = true)
                    source.file.deleteRecursively()
                }
                is FileOperationSource.Content -> {
                    val input = resolver.openInputStream(source.uri) ?: throw IllegalStateException("URI를 열 수 없습니다")
                    input.use { it.copyTo(payload.outputStream()) }
                    resolver.deleteWithConsent(source.uri)
                }
                is FileOperationSource.Archived -> {
                    val result = archives.extract(source.archive, source.path, trashDir, source.password)
                    val produced = result.getOrElse { throw it }
                    val extracted = produced.firstOrNull() ?: throw IllegalStateException("추출 실패")
                    if (extracted != payload) {
                        extracted.copyRecursively(payload, overwrite = true)
                        extracted.deleteRecursively()
                    }
                    val editResult = archives.edit(source.archive, listOf(ArchiveEdit.Delete(source.path)))
                    editResult.exceptionOrNull()?.let { throw it }
                }
            }
            val originalParent = when (source) {
                is FileOperationSource.Local -> source.file.parentFile?.absolutePath ?: ""
                is FileOperationSource.Content -> ""
                is FileOperationSource.Archived -> source.archive.absolutePath
            }
            val archiveOrigin = (source as? FileOperationSource.Archived)?.archive?.absolutePath
            metadata += TrashEntry(id, name, originalParent, archiveOrigin, clock())
            OperationOutcome(source, destinationName = name)
        } catch (e: ConsentRequiredException) {
            // The trashed copy stays so a granted retry only has to redo the source deletion.
            throw e
        } catch (e: Exception) {
            payload.deleteRecursively()
            OperationOutcome(source, error = e.message ?: "삭제 실패")
        }
    }

    suspend fun restore(id: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = readMetadata().toMutableList()
            val entry = metadata.firstOrNull { it.id == id } ?: throw NoSuchElementException("휴지통 항목을 찾을 수 없습니다")
            val payload = File(trashDir, "${entry.id}_${entry.name}")
            if (!payload.exists()) throw IllegalStateException("복원할 파일이 없습니다")
            val destDir = if (entry.archiveOrigin != null) File(entry.archiveOrigin).parentFile else File(entry.originalParent)
            if (destDir == null) throw IllegalStateException("복원 대상 폴더를 확인할 수 없습니다")
            destDir.mkdirs()
            val name = restoreName(entry.name, destDir)
            val target = File(destDir, name)
            payload.copyRecursively(target, overwrite = false)
            payload.deleteRecursively()
            metadata.remove(entry)
            writeMetadata(metadata)
            target
        }
    }

    private fun restoreName(name: String, destDir: File): String {
        if (!File(destDir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var candidate = "${base}_restore$ext"
        var index = 2
        while (File(destDir, candidate).exists()) {
            candidate = "${base}_restore$index$ext"
            index++
        }
        return candidate
    }

    suspend fun empty(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            trashDir.listFiles()?.forEach { file -> if (file != metadataFile) file.deleteRecursively() }
            writeMetadata(emptyList())
        }
    }

    suspend fun expire(): Int = withContext(Dispatchers.IO) {
        val metadata = readMetadata().toMutableList()
        val now = clock()
        val expired = metadata.filter { now - it.deletedAtMillis >= TRASH_MAX_AGE_MILLIS }
        for (entry in expired) {
            File(trashDir, "${entry.id}_${entry.name}").deleteRecursively()
            metadata.remove(entry)
        }
        if (expired.isNotEmpty()) writeMetadata(metadata)
        expired.size
    }

    private fun readMetadata(): List<TrashEntry> {
        if (!metadataFile.exists()) return emptyList()
        val array = try {
            JSONArray(metadataFile.readText())
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            TrashEntry(
                id = obj.getString("id"),
                name = obj.getString("name"),
                originalParent = obj.getString("originalParent"),
                archiveOrigin = obj.optString("archiveOrigin", "").ifEmpty { null },
                deletedAtMillis = obj.getLong("deletedAtMillis"),
            )
        }
    }

    private fun writeMetadata(entries: List<TrashEntry>) {
        trashDir.mkdirs()
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("originalParent", entry.originalParent)
                    .put("archiveOrigin", entry.archiveOrigin ?: "")
                    .put("deletedAtMillis", entry.deletedAtMillis),
            )
        }
        val tempFile = Files.createTempFile(trashDir.toPath(), "metadata", ".tmp").toFile()
        tempFile.writeText(array.toString())
        if (!tempFile.renameTo(metadataFile)) {
            tempFile.copyTo(metadataFile, overwrite = true)
            tempFile.delete()
        }
    }
}
