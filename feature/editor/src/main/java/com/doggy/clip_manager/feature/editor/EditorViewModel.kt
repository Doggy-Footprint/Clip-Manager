package com.doggy.clip_manager.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.EditJobs
import com.doggy.clip_manager.core.editor.EditSpec
import com.doggy.clip_manager.core.editor.EditState
import com.doggy.clip_manager.core.editor.FlipRange
import com.doggy.clip_manager.core.editor.FrameLayout
import com.doggy.clip_manager.core.editor.FrameMode
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.InvalidEffectException
import com.doggy.clip_manager.core.editor.OverlayEditSession
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.SpeedRange
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
    internal var ratioPreset: RatioPreset by mutableStateOf(RatioPreset.ORIGINAL)
        private set
    var frameMode: FrameMode by mutableStateOf(FrameMode.CROP)
        private set
    val frameLayout: FrameLayout
        get() = frameLayoutOf(ratioPreset, frameMode)
    var flips: List<FlipRange> by mutableStateOf(emptyList())
        private set
    var speeds: List<SpeedRange> by mutableStateOf(emptyList())
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
            ratioPreset = RatioPreset.ORIGINAL
            frameMode = FrameMode.CROP
            flips = emptyList()
            speeds = emptyList()
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

    internal fun chooseRatio(preset: RatioPreset) {
        ratioPreset = preset
    }

    fun chooseFrameMode(mode: FrameMode) {
        frameMode = mode
    }

    fun addSpeed(speed: Float): Boolean {
        if (speed !in SPEED_STEPS) return false
        val candidate = SpeedRange(clampSelection(durationUs, selection.startUs, selection.endUs), speed)
        val updated = speeds + candidate
        if (speedsOverlap(updated)) return false
        speeds = updated
        return true
    }

    fun updateSpeed(index: Int, value: SpeedRange): Boolean {
        if (index !in speeds.indices || value.speed !in SPEED_STEPS) return false
        val clamped = value.copy(range = clampSelection(durationUs, value.range.startUs, value.range.endUs))
        val updated = speeds.toMutableList().also { it[index] = clamped }
        if (speedsOverlap(updated)) return false
        speeds = updated
        return true
    }

    fun removeSpeed(index: Int) {
        if (index !in speeds.indices) return
        speeds = speeds.toMutableList().also { it.removeAt(index) }
    }

    fun addFlip(horizontal: Boolean, vertical: Boolean): Boolean {
        if (!horizontal && !vertical) return false
        flips = flips + FlipRange(clampSelection(durationUs, selection.startUs, selection.endUs), horizontal, vertical)
        return true
    }

    fun updateFlip(index: Int, value: FlipRange): Boolean {
        if (index !in flips.indices || (!value.horizontal && !value.vertical)) return false
        val clamped = value.copy(range = clampSelection(durationUs, value.range.startUs, value.range.endUs))
        flips = flips.toMutableList().also { it[index] = clamped }
        return true
    }

    fun removeFlip(index: Int) {
        if (index !in flips.indices) return
        flips = flips.toMutableList().also { it.removeAt(index) }
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

    fun exportSpec(): EditSpec? {
        val input = path ?: return null
        return editorExportSpec(input, selection, cutMode, overlays.value, frameLayout, flips, speeds)
    }
}
