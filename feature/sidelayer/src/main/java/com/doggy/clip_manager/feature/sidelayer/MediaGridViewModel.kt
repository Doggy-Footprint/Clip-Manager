package com.doggy.clip_manager.feature.sidelayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.doggy.clip_manager.core.data.media.MediaRepository
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.core.model.MediaQuery
import com.doggy.clip_manager.core.ui.rememberStoragePermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MediaGridUiState {
    data object Loading : MediaGridUiState

    data class Success(val entries: List<MediaEntry>) : MediaGridUiState
}

@HiltViewModel
class MediaGridViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val kind = MutableStateFlow(MediaKind.VIDEO)
    private val reloadCount = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MediaGridUiState> = combine(kind, reloadCount) { kind, _ -> kind }
        .mapLatest { kind ->
            MediaGridUiState.Success(mediaRepository.query(MediaQuery(setOf(kind))))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaGridUiState.Loading)

    fun setKind(newKind: MediaKind) {
        kind.value = newKind
    }

    fun reload() {
        reloadCount.value++
    }
}

@Composable
fun MediaGridRoute(
    kind: MediaKind,
    onEntrySelected: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
    selectedUri: String? = null,
    onEntryLongClick: (MediaEntry) -> Unit = {},
    // R15 debug toggle: when true, every entry is treated as path-less so copy/move/delete/viewer
    // flows all go through the Content(URI) path instead of Local(File), for device verification.
    treatAsPathless: Boolean = false,
    viewModel: MediaGridViewModel = hiltViewModel(),
    operations: OperationViewModel = hiltViewModel(),
) {
    val permission = rememberStoragePermissionState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshTick by operations.refreshTick.collectAsStateWithLifecycle()
    val message by operations.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(kind) { viewModel.setKind(kind) }
    LaunchedEffect(permission.granted) {
        if (permission.granted) viewModel.reload()
    }
    LaunchedEffect(refreshTick) {
        if (refreshTick > 0) viewModel.reload()
    }
    // R16: shown once as a Snackbar then consumed so it isn't re-shown on recomposition.
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        operations.consumeMessage()
    }

    val effectiveUiState = (uiState as? MediaGridUiState.Success)?.let { success ->
        if (treatAsPathless) MediaGridUiState.Success(success.entries.map { it.copy(filePath = null) }) else success
    } ?: uiState

    Box(modifier.fillMaxSize()) {
        MediaGridScreen(
            uiState = effectiveUiState,
            permissionGranted = permission.granted,
            onRequestPermission = permission::request,
            onEntryClick = onEntrySelected,
            modifier = Modifier.fillMaxSize(),
            selectedUri = selectedUri,
            onEntryLongClick = onEntryLongClick,
            operations = operations,
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
