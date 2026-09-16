package com.doggy.clip_manager.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrubThrottleTest {

    @Test
    fun onDrag_C6_normal_firstDragAfterStartAlwaysSeeks() {
        val throttle = ScrubThrottle()

        throttle.onDragStart()
        val result = throttle.onDrag(10000)

        assertEquals(10000L, result)
    }

    @Test
    fun onDrag_C7_boundary_deltaEqualToThresholdDoesNotSeek() {
        val throttle = ScrubThrottle()
        throttle.onDragStart()
        throttle.onDrag(10000)

        val above = throttle.onDrag(10500)
        val below = throttle.onDrag(9500)

        assertNull(above)
        assertNull(below)
    }

    @Test
    fun onDrag_C8_boundary_deltaAboveThresholdSeeksAndUpdatesReferencePoint() {
        val throttle = ScrubThrottle()
        throttle.onDragStart()
        throttle.onDrag(10000)

        val first = throttle.onDrag(10501)
        val second = throttle.onDrag(9999)

        assertEquals(10501L, first)
        assertEquals(9999L, second)
    }

    @Test
    fun onDrag_C9_normal_deltaMeasuredFromLastRequestedNotLastDragValue() {
        val throttle = ScrubThrottle()
        throttle.onDragStart()
        throttle.onDrag(10000)

        val notRequested = throttle.onDrag(10300)
        val requested = throttle.onDrag(10600)

        assertNull(notRequested)
        assertEquals(10600L, requested)
    }

    @Test
    fun onDrag_C10_edge_stateResetsOnEachNewDrag() {
        val throttle = ScrubThrottle()
        throttle.onDragStart()
        throttle.onDrag(10000)
        throttle.onDragEnd()

        throttle.onDragStart()
        val result = throttle.onDrag(10000)

        assertEquals(10000L, result)
    }

    @Test
    fun onDrag_C11_edge_dragWithoutStartIsTreatedAsNewDrag() {
        val throttle = ScrubThrottle()

        val result = throttle.onDrag(5000)

        assertEquals(5000L, result)
    }
}
