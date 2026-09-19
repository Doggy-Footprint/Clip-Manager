package com.doggy.clip_manager.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLogicTest {

    // matchHeightFirst

    @Test
    fun matchHeightFirst_F1_normal_widerContainerThanVideoReturnsFalse() {
        val result = matchHeightFirst(containerWidth = 1280f, containerHeight = 800f, videoAspectRatio = 16f / 9f)

        assertFalse(result)
    }

    @Test
    fun matchHeightFirst_F2_normal_tallerContainerThanVideoReturnsTrue() {
        val result = matchHeightFirst(containerWidth = 1280f, containerHeight = 720f, videoAspectRatio = 4f / 3f)

        assertTrue(result)
    }

    @Test
    fun matchHeightFirst_F3_boundary_equalRatiosReturnsFalse() {
        val result = matchHeightFirst(containerWidth = 1600f, containerHeight = 900f, videoAspectRatio = 16f / 9f)

        assertFalse(result)
    }

    @Test
    fun matchHeightFirst_F4_edge_zeroContainerHeightReturnsFalse() {
        val result = matchHeightFirst(containerWidth = 1280f, containerHeight = 0f, videoAspectRatio = 1f)

        assertFalse(result)
    }

    @Test
    fun matchHeightFirst_F4_edge_nonPositiveVideoAspectRatioReturnsFalse() {
        val zeroRatio = matchHeightFirst(containerWidth = 1280f, containerHeight = 800f, videoAspectRatio = 0f)
        val negativeRatio = matchHeightFirst(containerWidth = 1280f, containerHeight = 800f, videoAspectRatio = -1f)

        assertFalse(zeroRatio)
        assertFalse(negativeRatio)
    }

    // skipTargetMs

    @Test
    fun skipTargetMs_S1_normal_forwardSkipAddsDelta() {
        val result = skipTargetMs(positionMs = 10_000, deltaMs = 5_000, durationMs = 60_000)

        assertEquals(15_000L, result)
    }

    @Test
    fun skipTargetMs_S1_normal_backwardSkipSubtractsDelta() {
        val result = skipTargetMs(positionMs = 10_000, deltaMs = -5_000, durationMs = 60_000)

        assertEquals(5_000L, result)
    }

    @Test
    fun skipTargetMs_S2_boundary_belowZeroClampsToZero() {
        val result = skipTargetMs(positionMs = 3_000, deltaMs = -5_000, durationMs = 60_000)

        assertEquals(0L, result)
    }

    @Test
    fun skipTargetMs_S2_boundary_aboveDurationClampsToDuration() {
        val result = skipTargetMs(positionMs = 58_000, deltaMs = 5_000, durationMs = 60_000)

        assertEquals(60_000L, result)
    }

    @Test
    fun skipTargetMs_S3_edge_zeroDurationClampsToZero() {
        val result = skipTargetMs(positionMs = 0, deltaMs = 5_000, durationMs = 0)

        assertEquals(0L, result)
    }

    @Test
    fun skipTargetMs_S3_edge_negativeDurationClampsToZero() {
        val result = skipTargetMs(positionMs = 1_000, deltaMs = 5_000, durationMs = -100)

        assertEquals(0L, result)
    }

    // shouldAutoHide

    @Test
    fun shouldAutoHide_H1_normal_visiblePlayingNotDraggingReturnsTrue() {
        val result = shouldAutoHide(controlsVisible = true, isPlaying = true, isDragging = false)

        assertTrue(result)
    }

    @Test
    fun shouldAutoHide_H2_boundary_controlsHiddenReturnsFalse() {
        val result = shouldAutoHide(controlsVisible = false, isPlaying = true, isDragging = false)

        assertFalse(result)
    }

    @Test
    fun shouldAutoHide_H2_boundary_notPlayingReturnsFalse() {
        val result = shouldAutoHide(controlsVisible = true, isPlaying = false, isDragging = false)

        assertFalse(result)
    }

    @Test
    fun shouldAutoHide_H2_boundary_isDraggingReturnsFalse() {
        val result = shouldAutoHide(controlsVisible = true, isPlaying = true, isDragging = true)

        assertFalse(result)
    }
}
