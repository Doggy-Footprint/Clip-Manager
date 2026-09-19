package com.doggy.clip_manager.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeTest {

    @Test
    fun playbackTimeParts_T1_normal_truncatesToWholeSecondsUnderOneHour() {
        val result = playbackTimeParts(125_999)

        assertEquals(PlaybackTimeParts(hours = 0, minutes = 2, seconds = 5, showHours = false), result)
    }

    @Test
    fun playbackTimeParts_T2_boundary_justBelowOneHourHidesHours() {
        val result = playbackTimeParts(3_599_999)

        assertEquals(PlaybackTimeParts(hours = 0, minutes = 59, seconds = 59, showHours = false), result)
    }

    @Test
    fun playbackTimeParts_T2_boundary_exactlyOneHourShowsHours() {
        val result = playbackTimeParts(3_600_000)

        assertEquals(PlaybackTimeParts(hours = 1, minutes = 0, seconds = 0, showHours = true), result)
    }

    @Test
    fun playbackTimeParts_T3_normal_overOneHourShowsHoursMinutesSeconds() {
        val result = playbackTimeParts(3_723_000)

        assertEquals(PlaybackTimeParts(hours = 1, minutes = 2, seconds = 3, showHours = true), result)
    }

    @Test
    fun playbackTimeParts_T4_edge_negativeMsTreatedAsZero() {
        val result = playbackTimeParts(-1)

        assertEquals(PlaybackTimeParts(hours = 0, minutes = 0, seconds = 0, showHours = false), result)
    }

    @Test
    fun playbackTimeParts_T4_edge_zeroMs() {
        val result = playbackTimeParts(0)

        assertEquals(PlaybackTimeParts(hours = 0, minutes = 0, seconds = 0, showHours = false), result)
    }
}
