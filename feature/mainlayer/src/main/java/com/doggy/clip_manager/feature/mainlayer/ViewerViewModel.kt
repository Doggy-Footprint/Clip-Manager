package com.doggy.clip_manager.feature.mainlayer

import android.content.Context
import android.net.Uri
import com.doggy.clip_manager.core.data.FileListing
import com.doggy.clip_manager.core.data.operation.ArchiveFailure
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class ViewerKind { VIDEO, AUDIO, IMAGE, PDF, TEXT, CBZ, UNSUPPORTED }

fun classifyViewerKind(name: String): ViewerKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        FileListing.isVideo(name) -> ViewerKind.VIDEO
        ext in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus") -> ViewerKind.AUDIO
        ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> ViewerKind.IMAGE
        ext == "pdf" -> ViewerKind.PDF
        ext == "txt" -> ViewerKind.TEXT
        ext == "cbz" -> ViewerKind.CBZ
        else -> ViewerKind.UNSUPPORTED
    }
}

sealed interface ViewerContent {
    data class Video(val path: String) : ViewerContent
    data class Audio(val path: String) : ViewerContent
    data class ImageUri(val uri: String) : ViewerContent
    data class Pdf(val path: String) : ViewerContent
    data class Text(val content: String) : ViewerContent
    data class Cbz(val archive: File, val entries: List<String>, val password: CharArray?) : ViewerContent
    data class CbzPasswordRequired(val archive: File, val wrong: Boolean) : ViewerContent
    data class Error(val message: String) : ViewerContent
}

private fun sourceDisplayName(source: FileOperationSource): String = when (source) {
    is FileOperationSource.Local -> source.file.name
    is FileOperationSource.Content -> source.name
    is FileOperationSource.Archived -> source.path.trimEnd('/').substringAfterLast('/')
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archives: ArchiveRepository,
) : androidx.lifecycle.ViewModel() {

    private val cacheDir: File by lazy { File(context.cacheDir, "viewer_cache").apply { mkdirs() } }

    suspend fun resolve(source: FileOperationSource, cbzPassword: CharArray? = null): ViewerContent = withContext(Dispatchers.IO) {
        runCatching {
            val name = sourceDisplayName(source)
            when (classifyViewerKind(name)) {
                ViewerKind.VIDEO -> ViewerContent.Video(localPath(source, name))
                ViewerKind.AUDIO -> ViewerContent.Audio(localPath(source, name))
                ViewerKind.PDF -> ViewerContent.Pdf(localPath(source, name))
                ViewerKind.IMAGE -> ViewerContent.ImageUri(imageUri(source, name))
                ViewerKind.TEXT -> ViewerContent.Text(textContent(source))
                ViewerKind.CBZ -> cbzContent(source, name, cbzPassword)
                ViewerKind.UNSUPPORTED -> ViewerContent.Error("지원하지 않는 형식입니다")
            }
        }.getOrElse { ViewerContent.Error(it.message ?: "열 수 없습니다") }
    }

    suspend fun readEntryBytes(archive: File, path: String, password: CharArray?): ByteArray =
        withContext(Dispatchers.IO) { archives.open(archive, path, password) }

    private fun cacheFile(name: String): File = File(cacheDir, "${UUID.randomUUID()}_$name")

    /** Video/audio/PDF need a seekable real path; URI-only or archived sources are copied to cache first. */
    private fun localPath(source: FileOperationSource, name: String): String = when (source) {
        is FileOperationSource.Local -> source.file.absolutePath
        is FileOperationSource.Content -> {
            val target = cacheFile(name)
            context.contentResolver.openInputStream(source.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("URI를 열 수 없습니다")
            target.absolutePath
        }
        is FileOperationSource.Archived -> {
            val target = cacheFile(name)
            target.writeBytes(kotlinx.coroutines.runBlocking { archives.open(source.archive, source.path, source.password) })
            target.absolutePath
        }
    }

    private fun imageUri(source: FileOperationSource, name: String): String = when (source) {
        is FileOperationSource.Local -> Uri.fromFile(source.file).toString()
        is FileOperationSource.Content -> source.uri.toString()
        is FileOperationSource.Archived -> Uri.fromFile(File(localPath(source, name))).toString()
    }

    private suspend fun textContent(source: FileOperationSource): String = when (source) {
        is FileOperationSource.Local -> source.file.readText(Charsets.UTF_8)
        is FileOperationSource.Content -> context.contentResolver.openInputStream(source.uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("URI를 열 수 없습니다")
        is FileOperationSource.Archived -> archives.open(source.archive, source.path, source.password).toString(Charsets.UTF_8)
    }

    private suspend fun cbzContent(source: FileOperationSource, name: String, overridePassword: CharArray?): ViewerContent {
        val archiveFile = when (source) {
            is FileOperationSource.Local -> source.file
            is FileOperationSource.Content -> File(localPath(source, name))
            is FileOperationSource.Archived -> File(localPath(source, name))
        }
        val password = overridePassword ?: (source as? FileOperationSource.Archived)?.password
        val listing = archives.list(archiveFile, "", password)
        val reason = listing.failureReason
        when (reason) {
            ArchiveFailure.PASSWORD_REQUIRED -> return ViewerContent.CbzPasswordRequired(archiveFile, wrong = false)
            ArchiveFailure.WRONG_PASSWORD -> return ViewerContent.CbzPasswordRequired(archiveFile, wrong = true)
            null -> Unit
            else -> return ViewerContent.Error(reason.name)
        }
        val images = listing.entries
            .filter { !it.isDirectory && classifyViewerKind(it.path) == ViewerKind.IMAGE }
            .sortedBy { it.path.lowercase() }
            .map { it.path }
        return ViewerContent.Cbz(archiveFile, images, password)
    }
}
