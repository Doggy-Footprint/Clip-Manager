package com.doggy.clip_manager.feature.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.designsystem.theme.ClipTheme
import com.doggy.clip_manager.core.model.SeekMode
import com.doggy.clip_manager.core.player.ScrubThrottle
import kotlinx.coroutines.delay
import java.io.File

@Composable
internal fun PlayerScreenRoute(viewModel: PlayerViewModel = hiltViewModel()) {
    val player = viewModel.player
    val durationMs = viewModel.durationMs
    val scrubThrottle = remember { ScrubThrottle() }

    var sliderPositionMs by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(player.isPlaying()) }
    var wasPlayingBeforeDrag by remember { mutableStateOf(false) }

    LaunchedEffect(isDragging) {
        while (!isDragging) {
            sliderPositionMs = player.positionMs().toFloat()
            isPlaying = player.isPlaying()
            delay(200)
        }
    }

    val togglePlayback = {
        if (player.isPlaying()) player.pause() else player.play()
        isPlaying = player.isPlaying()
    }

    val backgroundColor = colorResource(R.color.feature_player_background)
    val contentColor = colorResource(R.color.feature_player_content)

    ClipTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Text(
                text = File(viewModel.path).name,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = dimensionResource(R.dimen.feature_player_title_padding_horizontal),
                    vertical = dimensionResource(R.dimen.feature_player_title_padding_vertical),
                ),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (viewModel.opened) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = togglePlayback),
                        factory = { context ->
                            SurfaceView(context).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        player.setSurface(holder.surface)
                                    }

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        player.setSurface(null)
                                    }
                                })
                            }
                        },
                    )
                } else {
                    Text(stringResource(R.string.feature_player_open_failed), color = contentColor)
                }
            }

            Slider(
                value = sliderPositionMs,
                valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                enabled = viewModel.opened,
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
                    .padding(horizontal = dimensionResource(R.dimen.feature_player_slider_padding_horizontal)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimensionResource(R.dimen.feature_player_controls_padding_start),
                        end = dimensionResource(R.dimen.feature_player_controls_padding_end),
                        bottom = dimensionResource(R.dimen.feature_player_controls_padding_bottom),
                    ),
            ) {
                IconButton(onClick = togglePlayback, enabled = viewModel.opened) {
                    Icon(
                        imageVector = if (isPlaying) ClipIcons.Pause else ClipIcons.Play,
                        contentDescription = null,
                        tint = contentColor,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.feature_player_progress,
                        formatTime(sliderPositionMs.toLong()),
                        formatTime(durationMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return stringResource(R.string.feature_player_time, minutes, seconds)
}
