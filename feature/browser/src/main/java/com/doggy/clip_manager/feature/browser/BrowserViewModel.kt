package com.doggy.clip_manager.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doggy.clip_manager.core.data.repository.FileRepository
import com.doggy.clip_manager.core.model.FileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import javax.inject.Inject

sealed interface BrowserUiState {
    data object Loading : BrowserUiState

    data class Success(
        val currentDir: File,
        val isAtRoot: Boolean,
        val entries: List<FileEntry>,
    ) : BrowserUiState
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val fileRepository: FileRepository,
) : ViewModel() {
    private val currentDir = MutableStateFlow(fileRepository.rootDir)
    private val reloadCount = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BrowserUiState> = combine(currentDir, reloadCount) { dir, _ -> dir }
        .mapLatest { dir ->
            BrowserUiState.Success(
                currentDir = dir,
                isAtRoot = dir.absolutePath == fileRepository.rootDir.absolutePath,
                entries = fileRepository.list(dir),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserUiState.Loading)

    fun open(entry: FileEntry) {
        if (entry.isDirectory) currentDir.value = entry.file
    }

    fun navigateUp(): Boolean {
        if (currentDir.value.absolutePath == fileRepository.rootDir.absolutePath) return false
        currentDir.value = currentDir.value.parentFile ?: fileRepository.rootDir
        return true
    }

    fun reload() {
        reloadCount.value++
    }
}
