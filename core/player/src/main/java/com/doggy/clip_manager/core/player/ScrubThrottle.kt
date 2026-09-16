package com.doggy.clip_manager.core.player

class ScrubThrottle(val thresholdMs: Long = 500) {
    private var lastRequestedMs: Long? = null

    fun onDragStart() {
        lastRequestedMs = null
    }

    fun onDrag(positionMs: Long): Long? {
        val last = lastRequestedMs
        if (last == null || kotlin.math.abs(positionMs - last) > thresholdMs) {
            lastRequestedMs = positionMs
            return positionMs
        }
        return null
    }

    fun onDragEnd() {
        lastRequestedMs = null
    }
}
