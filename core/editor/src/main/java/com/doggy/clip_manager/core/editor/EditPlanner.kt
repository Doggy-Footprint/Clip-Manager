package com.doggy.clip_manager.core.editor

object EditPlanner {

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

    fun effectiveCutMode(requested: CutMode, hasEffects: Boolean): CutMode =
        if (hasEffects) CutMode.PRECISE else requested

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
}
