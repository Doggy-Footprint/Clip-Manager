package com.doggy.clip_manager.feature.player

import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.EditEffects
import com.doggy.clip_manager.core.editor.EditSpec
import com.doggy.clip_manager.core.editor.EditState
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.editor.TimeRange

internal const val MIN_SELECTION_US = 100_000L

/**
 * The tool UI authors overlay ranges on the source timeline so the preview, which plays the
 * uncut source, shows an overlay exactly where it was placed. [EditSpec] however takes overlay
 * ranges on the output timeline, so [toOutputOverlays] converts at export time.
 */
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
 * Keeps [OverlaySpec.id] unique when a cut splits one authored overlay into several output spans:
 * the first span keeps the authored id and later spans get a suffix.
 */
internal fun toOutputOverlays(keepRanges: List<TimeRange>, overlays: List<OverlaySpec>): List<OverlaySpec> {
    val sorted = keepRanges.filter { it.endUs > it.startUs }.sortedBy { it.startUs }
    return overlays.flatMap { overlay ->
        var consumedUs = 0L
        var spanIndex = 0
        buildList {
            for (keep in sorted) {
                val start = maxOf(overlay.range.startUs, keep.startUs)
                val end = minOf(overlay.range.endUs, keep.endUs)
                if (end > start) {
                    val mapped = TimeRange(consumedUs + (start - keep.startUs), consumedUs + (end - keep.startUs))
                    val id = if (spanIndex == 0) overlay.id else "${overlay.id}#$spanIndex"
                    add(withRange(overlay, id, mapped))
                    spanIndex++
                }
                consumedUs += keep.endUs - keep.startUs
            }
        }
    }
}

internal fun editorExportSpec(
    inputPath: String,
    selection: TimeRange,
    cutMode: CutMode,
    overlays: List<OverlaySpec>,
): EditSpec {
    val keepRanges = listOf(selection)
    return EditSpec(
        inputPath = inputPath,
        keepRanges = keepRanges,
        cutMode = cutMode,
        effects = EditEffects(overlays = toOutputOverlays(keepRanges, overlays)),
    )
}

internal fun isExporting(state: EditState): Boolean = state is EditState.Running
