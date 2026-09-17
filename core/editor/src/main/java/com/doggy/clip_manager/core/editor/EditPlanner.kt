package com.doggy.clip_manager.core.editor

internal data class EditSegment(
    val range: TimeRange,
    val speed: Float,
    val horizontalFlip: Boolean,
    val verticalFlip: Boolean,
)

internal data class EditPlan(val segments: List<EditSegment>)

internal data class OutputSize(val width: Int, val height: Int)

object EditPlanner {

    private val allowedSpeeds = setOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** Exposed so VideoEditor.start can perform the same synchronous check (Errors section). */
    internal fun validateRanges(ranges: List<TimeRange>) {
        for (range in ranges) {
            if (range.startUs < 0 || range.startUs >= range.endUs) {
                throw InvalidRangeException(
                    "invalid range: startUs=${range.startUs}, endUs=${range.endUs}",
                )
            }
        }
    }

    internal fun validateEffects(effects: EditEffects, durationUs: Long? = null) {
        when (val layout = effects.frameLayout) {
            FrameLayout.Original -> Unit
            is FrameLayout.Ratio -> {
                if (layout.width <= 0 || layout.height <= 0) throw InvalidEffectException("ratio must be positive")
                if (!layout.cropCenter.x.isFinite() || !layout.cropCenter.y.isFinite() ||
                    layout.cropCenter.x !in 0f..1f || layout.cropCenter.y !in 0f..1f
                ) throw InvalidEffectException("crop center must be within 0..1")
            }
        }
        effects.flips.forEach { flip ->
            validateEffectRange(flip.range)
            if (!flip.horizontal && !flip.vertical) throw InvalidEffectException("flip must select an axis")
        }
        effects.speeds.forEach { speed ->
            validateEffectRange(speed.range)
            if (!speed.speed.isFinite() || speed.speed !in allowedSpeeds) {
                throw InvalidEffectException("unsupported speed: ${speed.speed}")
            }
        }
        if (durationUs != null) {
            val normalized = effects.speeds.mapNotNull { speed ->
                intersect(speed.range, TimeRange(0, durationUs))?.let { it to speed.speed }
            }.sortedBy { it.first.startUs }
            normalized.zipWithNext().forEach { (left, right) ->
                if (right.first.startUs < left.first.endUs) throw InvalidEffectException("speed ranges overlap")
            }
        }
    }

    private fun validateEffectRange(range: TimeRange) {
        if (range.startUs < 0 || range.startUs >= range.endUs) {
            throw InvalidEffectException("invalid effect range: $range")
        }
    }

    /** Sorts and merges overlapping or touching ranges. Input ranges must already be valid. */
    private fun mergeSorted(ranges: List<TimeRange>): List<TimeRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.startUs }
        val merged = mutableListOf<TimeRange>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            current = if (next.startUs <= current.endUs) {
                TimeRange(current.startUs, maxOf(current.endUs, next.endUs))
            } else {
                merged.add(current)
                next
            }
        }
        merged.add(current)
        return merged
    }

    fun keepRangesFromDeletions(durationUs: Long, deletions: List<TimeRange>): List<TimeRange> {
        validateRanges(deletions)
        val clipped = deletions.mapNotNull { range ->
            val start = maxOf(0L, range.startUs)
            val end = minOf(durationUs, range.endUs)
            if (start < end) TimeRange(start, end) else null
        }
        val mergedDeletions = mergeSorted(clipped)

        val keep = mutableListOf<TimeRange>()
        var cursor = 0L
        for (deletion in mergedDeletions) {
            if (deletion.startUs > cursor) {
                keep.add(TimeRange(cursor, deletion.startUs))
            }
            cursor = maxOf(cursor, deletion.endUs)
        }
        if (cursor < durationUs) {
            keep.add(TimeRange(cursor, durationUs))
        }
        if (keep.isEmpty()) throw EmptyEditException("no ranges remain after deletions")
        return keep
    }

    fun normalizeKeepRanges(durationUs: Long, ranges: List<TimeRange>): List<TimeRange> {
        validateRanges(ranges)
        val clipped = ranges.mapNotNull { range ->
            val start = maxOf(0L, range.startUs)
            val end = minOf(durationUs, range.endUs)
            if (start < end) TimeRange(start, end) else null
        }
        val merged = mergeSorted(clipped)
        if (merged.isEmpty()) throw EmptyEditException("no ranges remain after normalization")
        return merged
    }

    fun effectiveCutMode(requested: CutMode, effects: EditEffects): CutMode =
        if (effects.isPresent) CutMode.PRECISE else requested

    fun snapToKeyframes(ranges: List<TimeRange>, keyframesUs: List<Long>): List<TimeRange> {
        validateRanges(ranges)
        val sortedKeyframes = keyframesUs.sorted()
        val snapped = ranges.map { range ->
            val snappedStart = sortedKeyframes.lastOrNull { it <= range.startUs } ?: 0L
            TimeRange(snappedStart, range.endUs)
        }
        return mergeSorted(snapped)
    }

    fun outputDurationUs(ranges: List<TimeRange>): Long =
        ranges.sumOf { it.endUs - it.startUs }

    internal fun plan(durationUs: Long, keepRanges: List<TimeRange>, effects: EditEffects): EditPlan {
        validateEffects(effects, durationUs)
        val kept = normalizeKeepRanges(durationUs, keepRanges)
        return EditPlan(kept.flatMap { keptRange ->
            val boundaries = buildSet {
                add(keptRange.startUs)
                add(keptRange.endUs)
                effects.flips.forEach { intersect(it.range, keptRange)?.let { overlap -> add(overlap.startUs); add(overlap.endUs) } }
                effects.speeds.forEach { intersect(it.range, keptRange)?.let { overlap -> add(overlap.startUs); add(overlap.endUs) } }
            }.sorted()
            boundaries.zipWithNext().map { (start, end) ->
                val sample = TimeRange(start, end)
                val flips = effects.flips.filter { intersect(it.range, sample) != null }
                val speed = effects.speeds.firstOrNull { intersect(it.range, sample) != null }?.speed ?: 1f
                EditSegment(sample, speed, flips.count { it.horizontal } % 2 == 1, flips.count { it.vertical } % 2 == 1)
            }
        })
    }

    internal fun outputDurationUs(segments: List<EditSegment>): Long =
        segments.sumOf { segment -> ((segment.range.endUs - segment.range.startUs) / segment.speed).toLong() }

    internal fun outputSize(sourceWidth: Int, sourceHeight: Int, layout: FrameLayout): OutputSize {
        if (layout is FrameLayout.Original) return OutputSize(sourceWidth, sourceHeight)
        val ratio = layout as FrameLayout.Ratio
        val divisor = gcd(ratio.width, ratio.height)
        val ratioWidth = ratio.width / divisor
        val ratioHeight = ratio.height / divisor
        val step = if (ratioWidth % 2 == 0 && ratioHeight % 2 == 0) 1L else 2L
        val area = sourceWidth.toLong() * sourceHeight
        val ratioArea = ratioWidth.toLong() * ratioHeight
        val ideal = kotlin.math.sqrt(area.toDouble() / ratioArea).toLong()
        val candidates = (0L..3L).map { delta -> maxOf(step, ((ideal / step) + delta - 1) * step) }
        val multiplier = candidates.minBy { candidate -> kotlin.math.abs(ratioArea * candidate * candidate - area) }
        return OutputSize((ratioWidth * multiplier).toInt(), (ratioHeight * multiplier).toInt())
    }

    private fun intersect(a: TimeRange, b: TimeRange): TimeRange? {
        val start = maxOf(a.startUs, b.startUs)
        val end = minOf(a.endUs, b.endUs)
        return if (start < end) TimeRange(start, end) else null
    }

    private tailrec fun gcd(left: Int, right: Int): Int = if (right == 0) left else gcd(right, left % right)
}
