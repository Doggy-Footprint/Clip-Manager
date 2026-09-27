package com.doggy.clip_manager.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
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
 * Session-scoped editing state, restored from [SavedStateHandle] after process death (F8, I4):
 * every mutator writes its field's saved-state entry, and [init] rebuilds the overlay session from
 * its saved entry the same way. A missing or corrupted saved value falls back to that field's
 * normal default instead of throwing (see the `decode*` helpers in `EditorPersistence.kt`).
 */
@UnstableApi
@HiltViewModel
class EditorViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val session = OverlayEditSession()
    val overlays: StateFlow<List<OverlaySpec>> = session.overlays

    var editing: Boolean by savedStateHandle.persisted(KEY_EDITING, false, { it }, { it as? Boolean ?: false })
        private set
    var path: String? by savedStateHandle.persisted<String?>(KEY_PATH, null, { it }, { it as? String })
        private set
    var durationUs: Long by savedStateHandle.persisted(KEY_DURATION, 0L, { it }, { it as? Long ?: 0L })
        private set
    // durationUs (above) is already restored by the time this initializer runs, so a corrupt or
    // missing saved selection falls back to the same defaultSelection(durationUs) begin() would
    // produce, not an unconditional TimeRange(0, 0); a valid saved selection is still clamped to it.
    var selection: TimeRange by savedStateHandle.persisted(
        key = KEY_SELECTION,
        default = TimeRange(0L, 0L),
        encode = ::encodeTimeRange,
        decode = { value ->
            val decoded = decodeTimeRange(value)
            if (decoded != null) clampSelection(durationUs, decoded.startUs, decoded.endUs) else defaultSelection(durationUs)
        },
    )
        private set
    var cutMode: CutMode by savedStateHandle.persisted(KEY_CUT_MODE, CutMode.PRECISE, ::encodeCutMode, ::decodeCutMode)
        private set
    var selectedOverlayId: String? by savedStateHandle.persisted<String?>(KEY_SELECTED, null, { it }, { it as? String })
        private set
    var exportState: EditState by mutableStateOf(EditState.Idle)
        private set
    internal var ratioPreset: RatioPreset by savedStateHandle.persisted(
        KEY_RATIO,
        RatioPreset.ORIGINAL,
        ::encodeRatioPreset,
        ::decodeRatioPreset,
    )
        private set
    var frameMode: FrameMode by savedStateHandle.persisted(KEY_FRAME_MODE, FrameMode.CROP, ::encodeFrameMode, ::decodeFrameMode)
        private set
    val frameLayout: FrameLayout
        get() = frameLayoutOf(ratioPreset, frameMode)
    var flips: List<FlipRange> by savedStateHandle.persisted(KEY_FLIPS, emptyList(), ::encodeFlips, ::decodeFlips)
        private set
    var speeds: List<SpeedRange> by savedStateHandle.persisted(KEY_SPEEDS, emptyList(), ::encodeSpeeds, ::decodeSpeeds)
        private set
    var stowedOverlayIds: Set<String> by savedStateHandle.persisted(KEY_STOWED, emptySet(), ::encodeStringSet, ::decodeStringSet)
        private set
    var fullscreen: Boolean by savedStateHandle.persisted(KEY_FULLSCREEN, false, { it }, { it as? Boolean ?: false })
        private set

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val jobStates = EditJobs.current.flatMapLatest { it?.state ?: flowOf(EditState.Idle) }

    init {
        // OverlayEditSession has no bulk setter, only add() (which re-validates the cumulative
        // list); replaying every saved overlay through it and clearing on the first failure keeps
        // restore all-or-nothing instead of leaving a partially validated session (F8 errors, C10).
        val restoredOverlays = decodeOverlays(savedStateHandle.get<Any?>(KEY_OVERLAYS))
        runCatching { restoredOverlays.forEach(session::add) }.onFailure { session.clear() }

        // D1: each field above decodes independently, so a value that is a *syntactically* valid
        // saved value (e.g. a plausible-looking id string) can still be invalid relative to another
        // restored field. Re-run the same checks the mutators use before trusting these values.
        val overlayIds = overlays.value.map { it.id }.toSet()
        if (stowedOverlayIds.any { it !in overlayIds }) {
            stowedOverlayIds = stowedOverlayIds.intersect(overlayIds)
        }
        if (selectedOverlayId != null && (selectedOverlayId !in overlayIds || selectedOverlayId in stowedOverlayIds)) {
            selectedOverlayId = null
        }
        // selection's own decode already resolves durationUs-relative validity (see its property
        // declaration above), so no re-clamp is needed here.
        //
        // D2: a flip/speed whose range does not already fit the restored durationUs is invalid,
        // not merely out of range: silently clamping it (as the D1 pass did) can turn a stored
        // entry into a degenerate one (e.g. TimeRange(0, 0)) that is neither what was saved nor a
        // mutator-producible default. Such an entry is dropped instead of clamped; durationUs <= 0
        // makes every range invalid, so both lists become empty.
        flips = if (durationUs <= 0) {
            emptyList()
        } else {
            flips.filter { (it.horizontal || it.vertical) && fitsDuration(durationUs, it.range) }
        }
        val validSpeeds = if (durationUs <= 0) {
            emptyList()
        } else {
            speeds.filter { it.speed in SPEED_STEPS && fitsDuration(durationUs, it.range) }
        }
        speeds = if (speedsOverlap(validSpeeds)) emptyList() else validSpeeds

        viewModelScope.launch { jobStates.collect { exportState = it } }
        viewModelScope.launch { overlays.collect { savedStateHandle[KEY_OVERLAYS] = encodeOverlays(it) } }
    }

    fun begin(path: String, durationUs: Long) {
        if (this.path != path) {
            session.clear()
            selectedOverlayId = null
            stowedOverlayIds = emptySet()
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
        if (id != null && id in stowedOverlayIds) return
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
        stowedOverlayIds = stowedOverlayIds - id
        if (selectedOverlayId == id) selectedOverlayId = null
    }

    fun stow(id: String) {
        if (overlays.value.none { it.id == id }) return
        stowedOverlayIds = stowedOverlayIds + id
        if (selectedOverlayId == id) selectedOverlayId = null
    }

    fun unstow(id: String) {
        if (id !in stowedOverlayIds) return
        stowedOverlayIds = stowedOverlayIds - id
        selectedOverlayId = id
    }

    fun toggleFullscreen() {
        fullscreen = !fullscreen
    }

    fun exportSpec(): EditSpec? {
        val input = path ?: return null
        return editorExportSpec(input, selection, cutMode, overlays.value, frameLayout, flips, speeds)
    }
}

private const val KEY_EDITING = "editor.editing"
private const val KEY_PATH = "editor.path"
private const val KEY_DURATION = "editor.durationUs"
private const val KEY_SELECTION = "editor.selection"
private const val KEY_CUT_MODE = "editor.cutMode"
private const val KEY_SELECTED = "editor.selectedOverlayId"
private const val KEY_RATIO = "editor.ratioPreset"
private const val KEY_FRAME_MODE = "editor.frameMode"
private const val KEY_FLIPS = "editor.flips"
private const val KEY_SPEEDS = "editor.speeds"
private const val KEY_STOWED = "editor.stowedOverlayIds"
private const val KEY_FULLSCREEN = "editor.fullscreen"
private const val KEY_OVERLAYS = "editor.overlays"
