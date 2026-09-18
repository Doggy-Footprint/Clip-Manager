package com.doggy.clip_manager.core.editor

/** [startUs, endUs) */
data class TimeRange(val startUs: Long, val endUs: Long)

data class NormalizedPoint(val x: Float = 0.5f, val y: Float = 0.5f)

enum class FrameMode { CROP, STRETCH, FIT }

sealed interface FrameLayout {
    data object Original : FrameLayout

    data class Ratio(
        val width: Int,
        val height: Int,
        val mode: FrameMode,
        val cropCenter: NormalizedPoint = NormalizedPoint(),
    ) : FrameLayout
}

data class FlipRange(val range: TimeRange, val horizontal: Boolean, val vertical: Boolean)

data class SpeedRange(val range: TimeRange, val speed: Float)

data class EditEffects(
    val frameLayout: FrameLayout = FrameLayout.Original,
    val flips: List<FlipRange> = emptyList(),
    val speeds: List<SpeedRange> = emptyList(),
    val overlays: List<OverlaySpec> = emptyList(),
) {
    val isPresent: Boolean
        get() = frameLayout !is FrameLayout.Original || flips.isNotEmpty() || speeds.isNotEmpty() || overlays.isNotEmpty()
}

enum class CutMode { FAST, PRECISE }

enum class ConcatStrategy { SINGLE_COMPOSITION, SEGMENT_CONCAT }

data class EditSpec(
    val inputPath: String,
    val keepRanges: List<TimeRange>,
    val cutMode: CutMode,
    val effects: EditEffects = EditEffects(),
    val concatStrategy: ConcatStrategy = ConcatStrategy.SINGLE_COMPOSITION,
)
