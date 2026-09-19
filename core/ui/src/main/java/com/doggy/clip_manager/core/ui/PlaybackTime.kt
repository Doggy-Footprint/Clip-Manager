package com.doggy.clip_manager.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

data class PlaybackTimeParts(
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val showHours: Boolean,
)

fun playbackTimeParts(ms: Long): PlaybackTimeParts {
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

@Composable
fun formatTime(ms: Long): String {
    val parts = playbackTimeParts(ms)
    return if (parts.showHours) {
        stringResource(R.string.clip_time_hours, parts.hours, parts.minutes, parts.seconds)
    } else {
        stringResource(R.string.clip_time, parts.minutes, parts.seconds)
    }
}
