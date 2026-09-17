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
        assertEquals(CutMode.FAST, EditPlanner.effectiveCutMode(CutMode.FAST, EditEffects()))
        assertEquals(
            CutMode.PRECISE,
            EditPlanner.effectiveCutMode(
                CutMode.FAST,
                EditEffects(flips = listOf(FlipRange(TimeRange(0, SEC), true, false))),
            ),
        )
        assertEquals(CutMode.PRECISE, EditPlanner.effectiveCutMode(CutMode.PRECISE, EditEffects()))
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
        val result = EditPlanner.outputDurationUs(
            listOf(
                EditSegment(TimeRange(0, 2 * SEC), 1f, false, false),
                EditSegment(TimeRange(4 * SEC, 10 * SEC), 1f, false, false),
            ),
        )

        assertEquals(8_000_000L, result)
    }

    @Test
    fun outputDurationUs_C4_boundary_nonIntegralSpeedQuotientFloorsRatherThanRounds() {
        val result = EditPlanner.outputDurationUs(
            listOf(EditSegment(TimeRange(0, 1_000_006), 1.25f, false, false)),
        )

        assertEquals(800_004L, result)
    }

    @Test
    fun plan_C7_error_zeroRatioWidthThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(frameLayout = FrameLayout.Ratio(0, 1, FrameMode.CROP)),
        )
    }

    @Test
    fun plan_C7_error_negativeRatioHeightThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(frameLayout = FrameLayout.Ratio(1, -1, FrameMode.CROP)),
        )
    }

    @Test
    fun plan_C7_error_nanCropCoordinateThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(frameLayout = FrameLayout.Ratio(1, 1, FrameMode.CROP, NormalizedPoint(Float.NaN, 0.5f))),
        )
    }

    @Test
    fun plan_C7_error_cropCoordinatePastOneThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(frameLayout = FrameLayout.Ratio(1, 1, FrameMode.CROP, NormalizedPoint(0.5f, 1.01f))),
        )
    }

    @Test
    fun plan_C7_error_flipWithoutAxisThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(flips = listOf(FlipRange(TimeRange(0, SEC), horizontal = false, vertical = false))),
        )
    }

    @Test
    fun plan_C7_error_speedBelowAllowedRangeThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(speeds = listOf(SpeedRange(TimeRange(0, SEC), 0.49f))),
        )
    }

    @Test
    fun plan_C7_error_speedBetweenAllowedStepsThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(speeds = listOf(SpeedRange(TimeRange(0, SEC), 1.1f))),
        )
    }

    @Test
    fun plan_C7_error_nonFiniteSpeedThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(speeds = listOf(SpeedRange(TimeRange(0, SEC), Float.POSITIVE_INFINITY))),
        )
    }

    @Test
    fun plan_C7_error_zeroLengthEffectRangeThrowsInvalidEffect() {
        assertInvalidEffects(
            EditEffects(flips = listOf(FlipRange(TimeRange(SEC, SEC), horizontal = true, vertical = false))),
        )
    }

    @Test
    fun plan_C7_error_overlappingSpeedRangesThrowInvalidEffect() {
        assertInvalidEffects(
            EditEffects(
                speeds = listOf(
                    SpeedRange(TimeRange(0, 2 * SEC), 1.25f),
                    SpeedRange(TimeRange(SEC, 3 * SEC), 0.75f),
                ),
            ),
        )
    }

    private fun assertInvalidEffects(effects: EditEffects) {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.plan(10 * SEC, listOf(TimeRange(0, 10 * SEC)), effects)
        }
    }

    @Test
    fun plan_C1_normal_originalLayoutWithoutIntervalsKeepsExistingRangesAtOneX() {
        val result = EditPlanner.plan(10 * SEC, listOf(TimeRange(SEC, 3 * SEC)), EditEffects())

        assertEquals(listOf(EditSegment(TimeRange(SEC, 3 * SEC), 1f, false, false)), result.segments)
        assertEquals(OutputSize(640, 360), EditPlanner.outputSize(640, 360, FrameLayout.Original))
        assertEquals(CutMode.FAST, EditPlanner.effectiveCutMode(CutMode.FAST, EditEffects()))
    }

    @Test
    fun plan_C3_normal_bothAxisFlipMarksBothAxesOnItsIntersection() {
        val result = EditPlanner.plan(
            10 * SEC,
            listOf(TimeRange(0, 4 * SEC)),
            EditEffects(flips = listOf(FlipRange(TimeRange(SEC, 3 * SEC), true, true))),
        )

        assertEquals(
            listOf(
                EditSegment(TimeRange(0, SEC), 1f, false, false),
                EditSegment(TimeRange(SEC, 3 * SEC), 1f, true, true),
                EditSegment(TimeRange(3 * SEC, 4 * SEC), 1f, false, false),
            ),
            result.segments,
        )
    }

    @Test
    fun plan_C4_normal_disjointSpeedsSplitRangesAndLeaveOtherTimeAtOneX() {
        val result = EditPlanner.plan(
            10 * SEC,
            listOf(TimeRange(0, 10 * SEC)),
            EditEffects(speeds = listOf(SpeedRange(TimeRange(2 * SEC, 4 * SEC), 0.5f), SpeedRange(TimeRange(6 * SEC, 8 * SEC), 2f))),
        )

        assertEquals(
            listOf(
                EditSegment(TimeRange(0, 2 * SEC), 1f, false, false),
                EditSegment(TimeRange(2 * SEC, 4 * SEC), 0.5f, false, false),
                EditSegment(TimeRange(4 * SEC, 6 * SEC), 1f, false, false),
                EditSegment(TimeRange(6 * SEC, 8 * SEC), 2f, false, false),
                EditSegment(TimeRange(8 * SEC, 10 * SEC), 1f, false, false),
            ),
            result.segments,
        )
        assertEquals(11 * SEC, EditPlanner.outputDurationUs(result.segments))
    }

    @Test
    fun plan_C6_boundary_effectEndingAtKeepStartHasNoNonEmptyIntersection() {
        val result = EditPlanner.plan(
            10 * SEC,
            listOf(TimeRange(2 * SEC, 4 * SEC)),
            EditEffects(flips = listOf(FlipRange(TimeRange(SEC, 2 * SEC), true, false))),
        )

        assertEquals(listOf(EditSegment(TimeRange(2 * SEC, 4 * SEC), 1f, false, false)), result.segments)
    }

    @Test
    fun plan_C6_boundary_speedAtInputStartAndEndUsesAllowedEndpoints() {
        val result = EditPlanner.plan(
            10 * SEC,
            listOf(TimeRange(0, 10 * SEC)),
            EditEffects(speeds = listOf(SpeedRange(TimeRange(0, SEC), 0.5f), SpeedRange(TimeRange(9 * SEC, 10 * SEC), 2f))),
        )

        assertEquals(
            listOf(
                EditSegment(TimeRange(0, SEC), 0.5f, false, false),
                EditSegment(TimeRange(SEC, 9 * SEC), 1f, false, false),
                EditSegment(TimeRange(9 * SEC, 10 * SEC), 2f, false, false),
            ),
            result.segments,
        )
    }

    @Test
    fun plan_C8_edge_sameAxisFlipsCancelWhileDifferentAxisRemains() {
        val result = EditPlanner.plan(
            10 * SEC,
            listOf(TimeRange(0, 4 * SEC)),
            EditEffects(flips = listOf(FlipRange(TimeRange(0, 4 * SEC), true, false), FlipRange(TimeRange(SEC, 3 * SEC), true, true))),
        )

        assertEquals(
            listOf(
                EditSegment(TimeRange(0, SEC), 1f, true, false),
                EditSegment(TimeRange(SEC, 3 * SEC), 1f, false, true),
                EditSegment(TimeRange(3 * SEC, 4 * SEC), 1f, true, false),
            ),
            result.segments,
        )
    }

    @Test
    fun plan_C9_edge_effectOutsideKeptTimeLeavesSegmentsUnchangedButStillForcesPrecise() {
        val effects = EditEffects(flips = listOf(FlipRange(TimeRange(0, SEC), true, false)))
        val result = EditPlanner.plan(10 * SEC, listOf(TimeRange(2 * SEC, 4 * SEC)), effects)

        assertEquals(listOf(EditSegment(TimeRange(2 * SEC, 4 * SEC), 1f, false, false)), result.segments)
        assertEquals(CutMode.PRECISE, EditPlanner.effectiveCutMode(CutMode.FAST, effects))
    }

    @Test
    fun outputSize_C6_boundary_cropCentersAtZeroAndOnePreserveExactSquareOutput() {
        assertEquals(OutputSize(480, 480), EditPlanner.outputSize(640, 360, FrameLayout.Ratio(1, 1, FrameMode.CROP, NormalizedPoint(0f, 0f))))
        assertEquals(OutputSize(480, 480), EditPlanner.outputSize(640, 360, FrameLayout.Ratio(1, 1, FrameMode.CROP, NormalizedPoint(1f, 1f))))
    }
}
