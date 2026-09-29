package com.doggy.clip_manager.feature.sidelayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doggy.clip_manager.core.data.operation.ArchiveFailure
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.OperationDestination
import com.doggy.clip_manager.core.data.repository.FileRepository
import com.doggy.clip_manager.core.model.FileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ArchiveLocation(val archive: File, val path: String, val password: CharArray?, val readOnly: Boolean)

sealed interface BrowserUiState {
    data object Loading : BrowserUiState

    data class Success(
        val currentDir: File,
        val isAtRoot: Boolean,
        val entries: List<FileEntry>,
        val archive: ArchiveLocation? = null,
    ) : BrowserUiState
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {
    private val currentDir = MutableStateFlow(fileRepository.rootDir)
    private val archiveState = MutableStateFlow<ArchiveLocation?>(null)
    private val reloadCount = MutableStateFlow(0)

    private val _passwordRequest = MutableStateFlow<File?>(null)
    val passwordRequest: StateFlow<File?> = _passwordRequest.asStateFlow()

    private val _passwordError = MutableStateFlow(false)
    val passwordError: StateFlow<Boolean> = _passwordError.asStateFlow()

    private val _archiveError = MutableStateFlow<String?>(null)
    val archiveError: StateFlow<String?> = _archiveError.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BrowserUiState> = combine(currentDir, archiveState, reloadCount) { dir, archive, _ -> dir to archive }
        .mapLatest { (dir, archive) ->
            if (archive != null) {
                BrowserUiState.Success(
                    currentDir = dir,
                    isAtRoot = false,
                    entries = archiveRepository.list(archive.archive, archive.path, archive.password).entries.map {
                        FileEntry(File(archive.archive, it.path), it.isDirectory, isVideo = false)
                    },
                    archive = archive,
                )
            } else {
                BrowserUiState.Success(
                    currentDir = dir,
                    isAtRoot = dir.absolutePath == fileRepository.rootDir.absolutePath,
                    entries = fileRepository.list(dir),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserUiState.Loading)

    fun open(entry: FileEntry) {
        val archive = archiveState.value
        if (archive != null) {
            if (entry.isDirectory) archiveState.value = archive.copy(path = internalPath(entry.file, archive.archive))
            return
        }
        if (entry.isDirectory) currentDir.value = entry.file
    }

    fun openArchive(file: File, password: CharArray? = null) {
        viewModelScope.launch {
            val listing = archiveRepository.list(file, "", password)
            val reason = listing.failureReason
            when (reason) {
                ArchiveFailure.WRONG_PASSWORD -> {
                    _passwordError.value = true
                    _passwordRequest.value = file
                }
                ArchiveFailure.PASSWORD_REQUIRED -> {
                    _passwordError.value = false
                    _passwordRequest.value = file
                }
                null -> {
                    _passwordError.value = false
                    _passwordRequest.value = null
                    archiveState.value = ArchiveLocation(file, "", password, listing.readOnly)
                }
                else -> _archiveError.value = reason.name
            }
        }
    }

    fun submitArchivePassword(password: CharArray) {
        val file = _passwordRequest.value ?: return
        openArchive(file, password)
    }

    /** R17: cancelling the password dialog leaves whatever was browsed before it unchanged. */
    fun cancelArchivePassword() {
        _passwordRequest.value = null
        _passwordError.value = false
    }

    fun dismissArchiveError() {
        _archiveError.value = null
    }

    fun navigateUp(): Boolean {
        val archive = archiveState.value
        if (archive != null) {
            archiveState.value = if (archive.path.isEmpty()) null else archive.copy(path = archive.path.substringBeforeLast('/', ""))
            return true
        }
        if (currentDir.value.absolutePath == fileRepository.rootDir.absolutePath) return false
        currentDir.value = currentDir.value.parentFile ?: fileRepository.rootDir
        return true
    }

    fun reload() {
        reloadCount.value++
    }

    private fun internalPath(entryFile: File, archive: File): String =
        entryFile.path.removePrefix(archive.path).trim('/')
}

internal fun BrowserUiState.Success.currentDestination(): OperationDestination =
    archive?.let { OperationDestination.ArchiveDirectory(it.archive, it.path) } ?: OperationDestination.Directory(currentDir)
