package com.doggy.clip_manager.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SlowDetectorTest {

    @Test
    fun onProgress_C19_normal_wellWithinExpectedTimeIsNormal() {
        val detector = SlowDetector(expectedDurationMs = 10_000, startMs = 0)

        val state = detector.onProgress(5_000, 0.5f)

        assertEquals(SlowState.NORMAL, state)
    }

    @Test
    fun onProgress_C20_boundary_exactlyThreeTimesExpectedIsNormal() {
        val detector = SlowDetector(expectedDurationMs = 10_000, startMs = 0)

        // estimated total = (now - start) / fraction = 15_000 / 0.5 = 30_000 == 3 * 10_000
        val state = detector.onProgress(15_000, 0.5f)

        assertEquals(SlowState.NORMAL, state)
    }

    @Test
    fun onProgress_C20_boundary_justOverThreeTimesExpectedIsSlower() {
        val detector = SlowDetector(expectedDurationMs = 10_000, startMs = 0)

        // estimated total = 15_002 / 0.5 = 30_004 > 30_000
        val state = detector.onProgress(15_002, 0.5f)

        assertEquals(SlowState.SLOWER_THAN_EXPECTED, state)
    }

    @Test
    fun onProgress_C21_boundary_noProgressSince30000msIsStalled() {
        val detector = SlowDetector(expectedDurationMs = 1_000_000, startMs = 0)
        detector.onProgress(1_000, 0.1f)

        val state = detector.onProgress(31_000, 0.1f)

        assertEquals(SlowState.STALLED, state)
    }

    @Test
    fun onProgress_C21_boundary_noProgressSince29999msIsNormal() {
        val detector = SlowDetector(expectedDurationMs = 1_000_000, startMs = 0)
        detector.onProgress(1_000, 0.1f)

        val state = detector.onProgress(30_999, 0.1f)

        assertEquals(SlowState.NORMAL, state)
    }

    @Test
    fun onProgress_C22_edge_zeroFractionPastThreeTimesExpectedIsSlower() {
        val detector = SlowDetector(expectedDurationMs = 5_000, startMs = 0)

        val state = detector.onProgress(15_001, 0f)

        assertEquals(SlowState.SLOWER_THAN_EXPECTED, state)
    }

    @Test
    fun onProgress_C23_edge_stalledAndSlowerSimultaneouslyReportsStalled() {
        val detector = SlowDetector(expectedDurationMs = 5_000, startMs = 0)
        // Establishes a progress point far below the stall/slower thresholds.
        detector.onProgress(1_000, 0.1f)

        // 31_000ms since last increase (>=30_000, stalled) and estimate 31_000/0.1=310_000 (>3*5_000, slower).
        val state = detector.onProgress(31_000, 0.1f)

        assertEquals(SlowState.STALLED, state)
    }

    @Test
    fun onProgress_C24_normal_decreaseIsIgnoredForBothMaxFractionAndStallClock() {
        val detector = SlowDetector(expectedDurationMs = 10_000_000, startMs = 0)

        assertEquals(SlowState.NORMAL, detector.onProgress(1_000, 0.4f))
        // Decrease: must not update the max fraction nor the last-increase timestamp.
        assertEquals(SlowState.NORMAL, detector.onProgress(2_000, 0.3f))
        // 29_999ms since the last real increase at 1_000ms.
        assertEquals(SlowState.NORMAL, detector.onProgress(30_999, 0.3f))
        // 30_000ms since the last real increase at 1_000ms.
        assertEquals(SlowState.STALLED, detector.onProgress(31_000, 0.3f))
    }

    @Test
    fun onProgress_C25_error_negativeFractionThrowsAndLeavesStateUnchanged() {
        val detector = SlowDetector(expectedDurationMs = 1_000_000, startMs = 0)
        detector.onProgress(1_000, 0.5f)

        assertThrows(IllegalArgumentException::class.java) { detector.onProgress(2_000, -0.1f) }

        // Last-increase timestamp must still be 1_000: 31_000 - 1_000 = 30_000 => STALLED.
        assertEquals(SlowState.STALLED, detector.onProgress(31_000, 0.5f))
    }

    @Test
    fun onProgress_C25_error_fractionAboveOneThrowsAndLeavesStateUnchanged() {
        val detector = SlowDetector(expectedDurationMs = 1_000_000, startMs = 0)
        detector.onProgress(1_000, 0.5f)

        assertThrows(IllegalArgumentException::class.java) { detector.onProgress(2_000, 1.5f) }

        assertEquals(SlowState.STALLED, detector.onProgress(31_000, 0.5f))
    }

    @Test
    fun onProgress_C25_error_nanFractionThrowsAndLeavesStateUnchanged() {
        val detector = SlowDetector(expectedDurationMs = 1_000_000, startMs = 0)
        detector.onProgress(1_000, 0.5f)

        assertThrows(IllegalArgumentException::class.java) { detector.onProgress(2_000, Float.NaN) }

        assertEquals(SlowState.STALLED, detector.onProgress(31_000, 0.5f))
    }
}
