package com.doggy.clip_manager.feature.editor

import com.doggy.clip_manager.core.editor.ConcatStrategy
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.EditEffects
import com.doggy.clip_manager.core.editor.EditSpec
import com.doggy.clip_manager.core.editor.EditState
import com.doggy.clip_manager.core.editor.FlipRange
import com.doggy.clip_manager.core.editor.FrameLayout
import com.doggy.clip_manager.core.editor.FrameMode
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.NormalizedPoint
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.SpeedRange
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.editor.TimeRange

internal const val MIN_SELECTION_US = 100_000L

/** The 7 speeds the export engine accepts (`EditPlanner.allowedSpeeds`); the UI only offers these. */
internal val SPEED_STEPS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

internal enum class RatioPreset(val width: Int, val height: Int) {
    ORIGINAL(0, 0),
    SQUARE(1, 1),
    WIDE(16, 9),
    TALL(9, 16),
    CLASSIC(4, 3),
}

/**
 * The tool UI authors overlay and effect ranges on the source timeline so the preview, which
 * plays the uncut source through the same effects as export, shows them exactly where they were
 * placed. [EditSpec] however takes overlay ranges on the output timeline, so
 * [toOutputOverlays] converts at export time, accounting for per-segment speed (F7).
 */
internal fun frameLayoutOf(preset: RatioPreset, mode: FrameMode): FrameLayout =
    if (preset == RatioPreset.ORIGINAL) {
        FrameLayout.Original
    } else {
        FrameLayout.Ratio(preset.width, preset.height, mode, NormalizedPoint(0.5f, 0.5f))
    }

/** Overlap rule matches `EditPlanner.validateEffects`: a shared boundary (end == start) does not count. */
internal fun speedsOverlap(speeds: List<SpeedRange>): Boolean =
    speeds.sortedBy { it.range.startUs }.zipWithNext().any { (left, right) -> right.range.startUs < left.range.endUs }

internal fun defaultSelection(durationUs: Long): TimeRange =
    TimeRange(0L, maxOf(durationUs, 0L))

internal fun clampSelection(durationUs: Long, startUs: Long, endUs: Long): TimeRange {
    val duration = maxOf(durationUs, 0L)
    if (duration < MIN_SELECTION_US) return TimeRange(0L, duration)
    val start = startUs.coerceIn(0L, duration - MIN_SELECTION_US)
    val end = endUs.coerceIn(start + MIN_SELECTION_US, duration)
    return TimeRange(start, end)
}

internal fun withRange(overlay: OverlaySpec, id: String, range: TimeRange): OverlaySpec = when (overlay) {
    is TextOverlay -> overlay.copy(id = id, range = range)
    is ImageOverlay -> overlay.copy(id = id, range = range)
}

/**
 * Splits [keep] into the (range, speed) pieces `EditPlanner.plan` would derive for it: each speed
 * range intersected with [keep], gaps left at speed 1 (unspecified speed).
 */
private fun speedPiecesOf(keep: TimeRange, speeds: List<SpeedRange>): List<Pair<TimeRange, Float>> {
    val overlapping = speeds.mapNotNull { speed ->
        val start = maxOf(speed.range.startUs, keep.startUs)
        val end = minOf(speed.range.endUs, keep.endUs)
        if (end > start) TimeRange(start, end) to speed.speed else null
    }.sortedBy { it.first.startUs }
    val pieces = mutableListOf<Pair<TimeRange, Float>>()
    var cursor = keep.startUs
    for ((range, speed) in overlapping) {
        if (range.startUs > cursor) pieces += TimeRange(cursor, range.startUs) to 1f
        pieces += range to speed
        cursor = maxOf(cursor, range.endUs)
    }
    if (cursor < keep.endUs) pieces += TimeRange(cursor, keep.endUs) to 1f
    return pieces
}

/**
 * Output-local offset of original time [t] within one keep range's [pieces]: `floor(len/speed)`
 * for every piece fully before [t], plus the same truncation applied to the partial piece [t]
 * falls in (same truncation `EditPlanner.outputDurationUs` uses for full pieces, per F7).
 */
private fun localOffsetUs(pieces: List<Pair<TimeRange, Float>>, t: Long): Long {
    var offset = 0L
    for ((range, speed) in pieces) {
        when {
            range.endUs <= t -> offset += ((range.endUs - range.startUs) / speed).toLong()
            range.startUs < t -> {
                offset += ((t - range.startUs) / speed).toLong()
                return offset
            }
            else -> return offset
        }
    }
    return offset
}

/**
 * Keeps [OverlaySpec.id] unique when a cut splits one authored overlay into several output spans:
 * the first span keeps the authored id and later spans get a suffix.
 */
internal fun toOutputOverlays(
    keepRanges: List<TimeRange>,
    speeds: List<SpeedRange>,
    overlays: List<OverlaySpec>,
): List<OverlaySpec> {
    val piecesByKeep = keepRanges.filter { it.endUs > it.startUs }
        .sortedBy { it.startUs }
        .map { keep -> keep to speedPiecesOf(keep, speeds) }
    return overlays.flatMap { overlay ->
        var consumedUs = 0L
        var spanIndex = 0
        buildList {
            for ((keep, pieces) in piecesByKeep) {
                val start = maxOf(overlay.range.startUs, keep.startUs)
                val end = minOf(overlay.range.endUs, keep.endUs)
                if (end > start) {
                    val mapped = TimeRange(
                        consumedUs + localOffsetUs(pieces, start),
                        consumedUs + localOffsetUs(pieces, end),
                    )
                    val id = if (spanIndex == 0) overlay.id else "${overlay.id}#$spanIndex"
                    add(withRange(overlay, id, mapped))
                    spanIndex++
                }
                consumedUs += pieces.sumOf { (range, speed) -> ((range.endUs - range.startUs) / speed).toLong() }
            }
        }
    }
}

internal fun editorExportSpec(
    inputPath: String,
    selection: TimeRange,
    cutMode: CutMode,
    overlays: List<OverlaySpec>,
    frameLayout: FrameLayout,
    flips: List<FlipRange>,
    speeds: List<SpeedRange>,
): EditSpec {
    val keepRanges = listOf(selection)
    return EditSpec(
        inputPath = inputPath,
        keepRanges = keepRanges,
        cutMode = cutMode,
        effects = EditEffects(
            frameLayout = frameLayout,
            flips = flips,
            speeds = speeds,
            overlays = toOutputOverlays(keepRanges, speeds, overlays),
        ),
        // media3 1.11.1 Transformer drops OverlayEffect on non-first EditedMediaItems of a
        // multi-item single Composition; SEGMENT_CONCAT exports each segment as its own Composition.
        concatStrategy = ConcatStrategy.SEGMENT_CONCAT,
    )
}

internal fun isExporting(state: EditState): Boolean = state is EditState.Running
