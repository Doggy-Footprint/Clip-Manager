package com.doggy.clip_manager.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private const val SEC = 1_000_000L

class EditPlannerTest {

    @Test
    fun keepRangesFromDeletions_C1_normal_singleMiddleDeletionKeepsBeforeAndAfter() {
        val result = EditPlanner.keepRangesFromDeletions(
            durationUs = 10 * SEC,
            deletions = listOf(TimeRange(2 * SEC, 4 * SEC)),
        )

        assertEquals(listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)), result)
    }

    @Test
    fun keepRangesFromDeletions_C2_normal_frontAndBackDeletionsKeepMiddle() {
        val result = EditPlanner.keepRangesFromDeletions(
            durationUs = 10 * SEC,
            deletions = listOf(TimeRange(0, SEC), TimeRange(9 * SEC, 10 * SEC)),
        )

        assertEquals(listOf(TimeRange(SEC, 9 * SEC)), result)
    }

    @Test
    fun keepRangesFromDeletions_C3_edge_unsortedOverlappingDeletionsAreMerged() {
        val result = EditPlanner.keepRangesFromDeletions(
            durationUs = 10 * SEC,
            deletions = listOf(
                TimeRange(5 * SEC, 7 * SEC),
                TimeRange(SEC, 3 * SEC),
                TimeRange(2 * SEC, 4 * SEC),
            ),
        )

        assertEquals(
            listOf(TimeRange(0, SEC), TimeRange(4 * SEC, 5 * SEC), TimeRange(7 * SEC, 10 * SEC)),
            result,
        )
    }

    @Test
    fun keepRangesFromDeletions_C4_boundary_touchingDeletionsMergeIntoOneGap() {
        val result = EditPlanner.keepRangesFromDeletions(
            durationUs = 10 * SEC,
            deletions = listOf(TimeRange(SEC, 2 * SEC), TimeRange(2 * SEC, 3 * SEC)),
        )

        assertEquals(listOf(TimeRange(0, SEC), TimeRange(3 * SEC, 10 * SEC)), result)
    }

    @Test
    fun keepRangesFromDeletions_C5_boundary_deletionPastDurationIsClipped() {
        val result = EditPlanner.keepRangesFromDeletions(
            durationUs = 10 * SEC,
            deletions = listOf(TimeRange(8 * SEC, 12 * SEC)),
        )

        assertEquals(listOf(TimeRange(0, 8 * SEC)), result)
    }

    @Test
    fun keepRangesFromDeletions_C6_error_deletingEntireDurationThrowsEmptyEdit() {
        assertThrows(EmptyEditException::class.java) {
            EditPlanner.keepRangesFromDeletions(10 * SEC, listOf(TimeRange(0, 10 * SEC)))
        }
    }

    @Test
    fun keepRangesFromDeletions_C7_error_zeroLengthRangeThrowsInvalidRange() {
        assertThrows(InvalidRangeException::class.java) {
            EditPlanner.keepRangesFromDeletions(10 * SEC, listOf(TimeRange(3 * SEC, 3 * SEC)))
        }
    }

    @Test
    fun keepRangesFromDeletions_C7_error_negativeStartThrowsInvalidRange() {
        assertThrows(InvalidRangeException::class.java) {
            EditPlanner.keepRangesFromDeletions(10 * SEC, listOf(TimeRange(-1, 2 * SEC)))
        }
    }

    @Test
    fun normalizeKeepRanges_C8_normal_overlappingAndTouchingRangesMerge() {
        val result = EditPlanner.normalizeKeepRanges(
            durationUs = 10 * SEC,
            ranges = listOf(
                TimeRange(6 * SEC, 8 * SEC),
                TimeRange(SEC, 3 * SEC),
                TimeRange(2 * SEC, 4 * SEC),
                TimeRange(4 * SEC, 5 * SEC),
            ),
        )

        assertEquals(listOf(TimeRange(SEC, 5 * SEC), TimeRange(6 * SEC, 8 * SEC)), result)
    }

    @Test
    fun normalizeKeepRanges_C9_boundary_rangeStartingAtOrPastDurationIsDropped() {
        val result = EditPlanner.normalizeKeepRanges(
            durationUs = 10 * SEC,
            ranges = listOf(TimeRange(9 * SEC, 11 * SEC), TimeRange(10 * SEC, 12 * SEC)),
        )

        assertEquals(listOf(TimeRange(9 * SEC, 10 * SEC)), result)
    }

    @Test
    fun normalizeKeepRanges_C10_error_onlyRangeStartsAtDurationThrowsEmptyEdit() {
        assertThrows(EmptyEditException::class.java) {
            EditPlanner.normalizeKeepRanges(10 * SEC, listOf(TimeRange(10 * SEC, 12 * SEC)))
        }
    }

    @Test
    fun normalizeKeepRanges_C7_error_negativeStartThrowsInvalidRange() {
        assertThrows(InvalidRangeException::class.java) {
            EditPlanner.normalizeKeepRanges(10 * SEC, listOf(TimeRange(-1, 2 * SEC)))
        }
    }

    @Test
    fun normalizeKeepRanges_C7_error_zeroLengthRangeThrowsInvalidRange() {
        assertThrows(InvalidRangeException::class.java) {
            EditPlanner.normalizeKeepRanges(10 * SEC, listOf(TimeRange(3 * SEC, 3 * SEC)))
        }
    }

    @Test
    fun effectiveCutMode_C11_normal_effectsForcePreciseRegardlessOfRequest() {
        assertEquals(CutMode.FAST, EditPlanner.effectiveCutMode(CutMode.FAST, hasEffects = false))
        assertEquals(CutMode.PRECISE, EditPlanner.effectiveCutMode(CutMode.FAST, hasEffects = true))
        assertEquals(CutMode.PRECISE, EditPlanner.effectiveCutMode(CutMode.PRECISE, hasEffects = false))
        assertEquals(CutMode.PRECISE, EditPlanner.effectiveCutMode(CutMode.PRECISE, hasEffects = true))
    }

    @Test
    fun snapToKeyframes_C12_normal_startSnapsToLargestKeyframeAtOrBeforeStart() {
        val result = EditPlanner.snapToKeyframes(
            ranges = listOf(TimeRange(1_500_000, 3 * SEC), TimeRange(5 * SEC, 6 * SEC)),
            keyframesUs = listOf(0L, SEC, 2 * SEC, 5 * SEC),
        )

        assertEquals(listOf(TimeRange(SEC, 3 * SEC), TimeRange(5 * SEC, 6 * SEC)), result)
    }

    @Test
    fun snapToKeyframes_C13_edge_snappedRangesThatNowOverlapAreMerged() {
        val result = EditPlanner.snapToKeyframes(
            ranges = listOf(TimeRange(0, 1_200_000), TimeRange(1_500_000, 3 * SEC)),
            keyframesUs = listOf(0L, SEC),
        )

        assertEquals(listOf(TimeRange(0, 3 * SEC)), result)
    }

    @Test
    fun snapToKeyframes_C14_edge_noKeyframeAtOrBeforeStartSnapsToZero() {
        val result = EditPlanner.snapToKeyframes(
            ranges = listOf(TimeRange(500_000, SEC)),
            keyframesUs = emptyList(),
        )

        assertEquals(listOf(TimeRange(0, SEC)), result)
    }

    @Test
    fun outputDurationUs_C15_normal_sumsRangeLengths() {
        val result = EditPlanner.outputDurationUs(listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)))

        assertEquals(8_000_000L, result)
    }
}
