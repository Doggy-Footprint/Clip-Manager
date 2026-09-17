package com.doggy.clip_manager.core.editor

enum class SlowState { NORMAL, SLOWER_THAN_EXPECTED, STALLED }

class SlowDetector(
    private val expectedDurationMs: Long,
    private val startMs: Long,
    private val slowFactor: Double = 3.0,
    private val stallTimeoutMs: Long = 30_000,
) {
    private var maxFraction: Float = 0f
    private var lastIncreaseMs: Long = startMs

    fun onProgress(nowMs: Long, fraction: Float): SlowState {
        require(!fraction.isNaN() && fraction >= 0f && fraction <= 1f) {
            "fraction must be within [0,1]: $fraction"
        }
        if (fraction > maxFraction) {
            maxFraction = fraction
            lastIncreaseMs = nowMs
        }

        if (nowMs - lastIncreaseMs >= stallTimeoutMs) return SlowState.STALLED

        val elapsedMs = nowMs - startMs
        val thresholdMs = slowFactor * expectedDurationMs
        val isSlower = if (maxFraction <= 0f) {
            elapsedMs > thresholdMs
        } else {
            (elapsedMs / maxFraction) > thresholdMs
        }
        return if (isSlower) SlowState.SLOWER_THAN_EXPECTED else SlowState.NORMAL
    }
}
