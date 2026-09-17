package com.doggy.clip_manager.core.editor

/** [startUs, endUs) */
data class TimeRange(val startUs: Long, val endUs: Long)

enum class CutMode { FAST, PRECISE }

enum class ConcatStrategy { SINGLE_COMPOSITION, SEGMENT_CONCAT }

data class EditSpec(
    val inputPath: String,
    val keepRanges: List<TimeRange>,
    val cutMode: CutMode,
    val hasEffects: Boolean = false,
    val concatStrategy: ConcatStrategy = ConcatStrategy.SINGLE_COMPOSITION,
)
