package com.doggy.clip_manager.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.doggy.clip_manager.player.NativePlayer
import com.doggy.clip_manager.player.ScrubThrottle
import com.doggy.clip_manager.player.SeekMode
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(path: String) {
    val player = remember { NativePlayer() }
    val scrubThrottle = remember { ScrubThrottle() }

    var durationMs by remember { mutableLongStateOf(0L) }
    var sliderPositionMs by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var wasPlayingBeforeDrag by remember { mutableStateOf(false) }

    DisposableEffect(path) {
        val opened = player.open(path)
        if (opened) {
            durationMs = player.durationMs()
            player.play()
        }
        onDispose { player.release() }
    }

    LaunchedEffect(isDragging) {
        while (!isDragging) {
            sliderPositionMs = player.positionMs().toFloat()
            delay(200)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            val currentPlayer = rememberUpdatedState(player)
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        if (currentPlayer.value.isPlaying()) currentPlayer.value.pause() else currentPlayer.value.play()
                    },
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                currentPlayer.value.setSurface(holder.surface)
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                currentPlayer.value.setSurface(null)
                            }
                        })
                    }
                }
            )
        }

        Text(
            text = "${formatTime(sliderPositionMs.toLong())} / ${formatTime(durationMs)}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        )

        Slider(
            value = sliderPositionMs,
            valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
            onValueChange = { value ->
                if (!isDragging) {
                    isDragging = true
                    wasPlayingBeforeDrag = player.isPlaying()
                    player.pause()
                    scrubThrottle.onDragStart()
                }
                sliderPositionMs = value
                val throttled = scrubThrottle.onDrag(value.toLong())
                if (throttled != null) {
                    player.seekTo(throttled, SeekMode.SCRUB)
                }
            },
            onValueChangeFinished = {
                scrubThrottle.onDragEnd()
                isDragging = false
                if (wasPlayingBeforeDrag) player.play()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
