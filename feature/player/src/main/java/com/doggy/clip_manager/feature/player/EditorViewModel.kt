package com.doggy.clip_manager.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.EditJobs
import com.doggy.clip_manager.core.editor.EditState
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.InvalidEffectException
import com.doggy.clip_manager.core.editor.OverlayEditSession
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.editor.TimeRange
import com.doggy.clip_manager.core.model.ImageAsset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Session-scoped editing state: overlays live only as long as the process, which is the decided
 * Phase 4 behaviour. Nothing here is persisted.
 */
@UnstableApi
@HiltViewModel
class EditorViewModel @Inject constructor() : ViewModel() {

    private val session = OverlayEditSession()
    val overlays: StateFlow<List<OverlaySpec>> = session.overlays

    var editing: Boolean by mutableStateOf(false)
        private set
    var path: String? by mutableStateOf(null)
        private set
    var durationUs: Long by mutableStateOf(0L)
        private set
    var selection: TimeRange by mutableStateOf(TimeRange(0L, 0L))
        private set
    var cutMode: CutMode by mutableStateOf(CutMode.PRECISE)
        private set
    var selectedOverlayId: String? by mutableStateOf(null)
        private set
    var exportState: EditState by mutableStateOf(EditState.Idle)
        private set

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val jobStates = EditJobs.current.flatMapLatest { it?.state ?: flowOf(EditState.Idle) }

    init {
        viewModelScope.launch { jobStates.collect { exportState = it } }
    }

    fun begin(path: String, durationUs: Long) {
        if (this.path != path) {
            session.clear()
            selectedOverlayId = null
            this.path = path
            this.durationUs = durationUs
            selection = defaultSelection(durationUs)
        }
        editing = true
    }

    fun end() {
        editing = false
    }

    fun setSelection(startUs: Long, endUs: Long) {
        selection = clampSelection(durationUs, startUs, endUs)
    }

    fun chooseCutMode(mode: CutMode) {
        cutMode = mode
    }

    fun select(id: String?) {
        selectedOverlayId = id
    }

    /** [OverlayEditSession] rejects blank text, so a new overlay starts from caller-supplied text. */
    fun addTextOverlay(text: String): TextOverlay =
        TextOverlay(id = UUID.randomUUID().toString(), range = selection, text = text).also {
            session.add(it)
            selectedOverlayId = it.id
        }

    fun addImageOverlay(asset: ImageAsset): ImageOverlay =
        ImageOverlay(id = UUID.randomUUID().toString(), range = selection, source = asset.source)
            .also {
                session.add(it)
                selectedOverlayId = it.id
            }

    /** Rejects an edit that [OverlayEditSession] refuses rather than letting it crash the UI. */
    fun update(overlay: OverlaySpec) {
        runCatching { session.update(overlay) }.exceptionOrNull()?.let { if (it !is InvalidEffectException) throw it }
    }

    fun remove(id: String) {
        session.remove(id)
        if (selectedOverlayId == id) selectedOverlayId = null
    }

    fun exportSpec(): com.doggy.clip_manager.core.editor.EditSpec? {
        val input = path ?: return null
        return editorExportSpec(input, selection, cutMode, overlays.value)
    }
}
