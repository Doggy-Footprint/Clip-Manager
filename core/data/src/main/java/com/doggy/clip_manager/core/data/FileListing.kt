package com.doggy.clip_manager.core.data

import com.doggy.clip_manager.core.model.FileEntry
import java.io.File

object FileListing {
    private val VIDEO_EXTENSIONS = setOf(
        "ts", "m2ts", "mts", "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp"
    )

    fun isVideo(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        val ext = name.substring(dot + 1).lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    fun list(dir: File): List<FileEntry> {
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { !it.name.startsWith(".") }
            .map { FileEntry(it, it.isDirectory, if (it.isDirectory) false else isVideo(it.name)) }
            .sortedWith(
                compareByDescending<FileEntry> { it.isDirectory }
                    .thenBy { it.file.name.lowercase() }
            )
    }
}
