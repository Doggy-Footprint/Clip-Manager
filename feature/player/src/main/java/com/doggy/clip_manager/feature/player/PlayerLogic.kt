package com.doggy.clip_manager.feature.player

internal fun matchHeightFirst(containerWidth: Float, containerHeight: Float, videoAspectRatio: Float): Boolean {
    if (containerHeight <= 0 || videoAspectRatio <= 0) return false
    return containerWidth / containerHeight > videoAspectRatio
}

internal fun skipTargetMs(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    return (positionMs + deltaMs).coerceIn(0L, maxOf(durationMs, 0L))
}

internal fun shouldAutoHide(controlsVisible: Boolean, isPlaying: Boolean, isDragging: Boolean): Boolean {
    return controlsVisible && isPlaying && !isDragging
}

internal data class PlayerUiState(
    val title: String,
    val opened: Boolean,
    val videoAspectRatio: Float?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val controlsVisible: Boolean,
)
