package com.doggy.clip_manager.browser

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.File

class BrowserViewModel : ViewModel() {
    private val rootDir: File = Environment.getExternalStorageDirectory()

    var currentDir by mutableStateOf(rootDir)
        private set

    val isAtRoot: Boolean
        get() = currentDir.absolutePath == rootDir.absolutePath

    val entries: List<FileEntry>
        get() = FileListing.list(currentDir)

    fun open(entry: FileEntry) {
        if (entry.isDirectory) currentDir = entry.file
    }

    fun navigateUp(): Boolean {
        if (isAtRoot) return false
        currentDir = currentDir.parentFile ?: rootDir
        return true
    }
}
