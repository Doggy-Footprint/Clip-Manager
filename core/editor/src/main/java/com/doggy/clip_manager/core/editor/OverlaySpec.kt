package com.doggy.clip_manager.core.editor

import com.doggy.clip_manager.core.model.ImageSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface OverlaySpec {
    val id: String
    val range: TimeRange
}

data class TextOverlay(
    override val id: String,
    override val range: TimeRange,
    val text: String,
    val style: TextOverlayStyle = TextOverlayStyle.Default,
) : OverlaySpec

data class TextOverlayStyle(
    val positionX: Float = 0.5f,
    val positionY: Float = 1f,
    val fontSizePt: Float = 20f,
    val colorArgb: Long = 0xffffffff,
    val backgroundArgb: Long? = null,
    val centerAligned: Boolean = true,
) {
    fun validate() {
        if (!positionX.isFinite() || positionX !in 0f..1f || !positionY.isFinite() || positionY !in 0f..1f) {
            throw InvalidEffectException("text overlay position must be within 0..1")
        }
        if (!fontSizePt.isFinite() || fontSizePt <= 0f) {
            throw InvalidEffectException("text overlay font size must be positive and finite")
        }
    }

    companion object {
        val Default = TextOverlayStyle()
    }
}

data class OverlayTransform(
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val rotationDegrees: Float = 0f,
) {
    fun validate() {
        if (!positionX.isFinite() || positionX !in 0f..1f || !positionY.isFinite() || positionY !in 0f..1f) {
            throw InvalidEffectException("overlay position must be within 0..1")
        }
        if (!scale.isFinite() || scale <= 0f) throw InvalidEffectException("overlay scale must be positive and finite")
        if (!alpha.isFinite() || alpha !in 0f..1f) throw InvalidEffectException("overlay alpha must be within 0..1")
        if (!rotationDegrees.isFinite()) throw InvalidEffectException("overlay rotation must be finite")
    }
}

data class ImageOverlay(
    override val id: String,
    override val range: TimeRange,
    val source: ImageSource,
    val transform: OverlayTransform = OverlayTransform(),
) : OverlaySpec

internal fun OverlaySpec.withRange(range: TimeRange): OverlaySpec = when (this) {
    is TextOverlay -> copy(range = range)
    is ImageOverlay -> copy(range = range)
}

class OverlayEditSession {
    private val mutableOverlays = MutableStateFlow<List<OverlaySpec>>(emptyList())
    val overlays: StateFlow<List<OverlaySpec>> = mutableOverlays.asStateFlow()

    fun add(overlay: OverlaySpec) {
        EditPlanner.validateOverlays(mutableOverlays.value + overlay)
        mutableOverlays.value += overlay
    }

    fun update(overlay: OverlaySpec) {
        val index = mutableOverlays.value.indexOfFirst { it.id == overlay.id }
        if (index < 0) throw InvalidEffectException("unknown overlay id: ${overlay.id}")
        val next = mutableOverlays.value.toMutableList().also { it[index] = overlay }
        EditPlanner.validateOverlays(next)
        mutableOverlays.value = next
    }

    fun remove(id: String) {
        mutableOverlays.value = mutableOverlays.value.filterNot { it.id == id }
    }

    fun clear() {
        mutableOverlays.value = emptyList()
    }
}
