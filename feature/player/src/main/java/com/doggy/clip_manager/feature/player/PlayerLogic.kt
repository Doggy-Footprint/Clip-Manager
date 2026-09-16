package com.doggy.clip_manager.feature.player

internal fun matchHeightFirst(containerWidth: Float, containerHeight: Float, videoAspectRatio: Float): Boolean {
    if (containerHeight <= 0 || videoAspectRatio <= 0) return false
    return containerWidth / containerHeight > videoAspectRatio
}

internal fun skipTargetMs(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    return (positionMs + deltaMs).coerceIn(0L, maxOf(durationMs, 0L))
}

internal data class PlaybackTimeParts(
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val showHours: Boolean,
)

internal fun playbackTimeParts(ms: Long): PlaybackTimeParts {
    val totalSeconds = maxOf(ms, 0L) / 1000
    val hours = totalSeconds / 3600
    val showHours = hours >= 1
    val minutes = if (showHours) (totalSeconds % 3600) / 60 else totalSeconds / 60
    val seconds = totalSeconds % 60
    return PlaybackTimeParts(
        hours = if (showHours) hours else 0L,
        minutes = minutes,
        seconds = seconds,
        showHours = showHours,
    )
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
