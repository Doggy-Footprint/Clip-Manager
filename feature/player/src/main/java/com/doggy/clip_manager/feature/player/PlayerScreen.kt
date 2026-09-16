package com.doggy.clip_manager.feature.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.designsystem.theme.ClipTheme
import com.doggy.clip_manager.core.model.SeekMode
import com.doggy.clip_manager.core.player.ScrubThrottle
import kotlinx.coroutines.delay
import java.io.File

private const val CONTROLS_AUTO_HIDE_MS = 5_000L
private const val SKIP_STEP_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScreenRoute(viewModel: PlayerViewModel = hiltViewModel()) {
    val player = viewModel.player
    val durationMs = viewModel.durationMs
    val scrubThrottle = remember { ScrubThrottle() }

    var sliderPositionMs by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(player.isPlaying()) }
    var wasPlayingBeforeDrag by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(isDragging) {
        while (!isDragging) {
            sliderPositionMs = player.positionMs().toFloat()
            isPlaying = player.isPlaying()
            delay(200)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isDragging, interactionCount) {
        if (controlsVisible && isPlaying && !isDragging) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    val togglePlayback: () -> Unit = {
        if (player.isPlaying()) player.pause() else player.play()
        isPlaying = player.isPlaying()
        interactionCount++
    }
    val skipBy: (Long) -> Unit = { deltaMs ->
        val target = (player.positionMs() + deltaMs).coerceIn(0L, durationMs)
        player.seekTo(target, SeekMode.KEYFRAME)
        sliderPositionMs = target.toFloat()
        interactionCount++
    }

    val backgroundColor = colorResource(R.color.feature_player_background)
    val contentColor = colorResource(R.color.feature_player_content)
    val scrimColor = colorResource(R.color.feature_player_scrim)
    val seekInactiveColor = colorResource(R.color.feature_player_seek_inactive)
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = contentColor,
        disabledContentColor = seekInactiveColor,
    )

    ClipTheme(darkTheme = true) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { controlsVisible = !controlsVisible },
            contentAlignment = Alignment.Center,
        ) {
            val aspectRatio = viewModel.videoAspectRatio
            val controlSpacingRatio = ResourcesCompat.getFloat(
                LocalContext.current.resources,
                R.dimen.feature_player_control_spacing_ratio,
            )
            val controlSpacing = maxWidth * controlSpacingRatio
            if (viewModel.opened) {
                val videoModifier = if (aspectRatio != null) {
                    Modifier.aspectRatio(aspectRatio, matchHeightConstraintsFirst = maxWidth / maxHeight > aspectRatio)
                } else {
                    Modifier.fillMaxSize()
                }
                AndroidView(
                    modifier = videoModifier,
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

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(scrimColor, Color.Transparent)))
                            .statusBarsPadding()
                            .padding(
                                horizontal = dimensionResource(R.dimen.feature_player_overlay_padding_horizontal),
                                vertical = dimensionResource(R.dimen.feature_player_overlay_padding_vertical),
                            ),
                    ) {
                        Text(
                            text = File(viewModel.path).name,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = dimensionResource(R.dimen.feature_player_title_padding_horizontal)),
                        )
                        Row {
                            IconButton(onClick = {}, colors = iconColors) {
                                Icon(ClipIcons.MoreVert, stringResource(R.string.feature_player_quick_menu))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, scrimColor)))
                            .navigationBarsPadding()
                            .padding(
                                horizontal = dimensionResource(R.dimen.feature_player_overlay_padding_horizontal),
                                vertical = dimensionResource(R.dimen.feature_player_overlay_padding_vertical),
                            ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = formatTime(sliderPositionMs.toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor,
                            )
                            Spacer(Modifier.width(dimensionResource(R.dimen.feature_player_seek_time_spacing)))
                            val valueMax = if (durationMs > 0) durationMs.toFloat() else 1f
                            val thumbSize = dimensionResource(R.dimen.feature_player_seek_thumb_size)
                            val trackHeight = dimensionResource(R.dimen.feature_player_seek_track_height)
                            Slider(
                                value = sliderPositionMs,
                                valueRange = 0f..valueMax,
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
                                    interactionCount++
                                },
                                // The Slider's thumb slot is not vertically centered against a custom
                                // track, so the knob is drawn from the track and the slot only reserves width.
                                thumb = { Spacer(Modifier.size(thumbSize)) },
                                track = {
                                    val fraction = (sliderPositionMs / valueMax).coerceIn(0f, 1f)
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(trackHeight),
                                    ) {
                                        val centerY = size.height / 2
                                        val filledX = size.width * fraction
                                        drawLine(seekInactiveColor, Offset(0f, centerY), Offset(size.width, centerY), size.height)
                                        drawLine(contentColor, Offset(0f, centerY), Offset(filledX, centerY), size.height)
                                        drawCircle(contentColor, thumbSize.toPx() / 2, Offset(filledX, centerY))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(dimensionResource(R.dimen.feature_player_seek_time_spacing)))
                            Text(
                                text = formatTime(durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(
                                controlSpacing,
                                Alignment.CenterHorizontally,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PlayerControlButton(ClipIcons.SkipPrevious, R.string.feature_player_previous, false, iconColors) {}
                            PlayerControlButton(ClipIcons.Replay5, R.string.feature_player_rewind, viewModel.opened, iconColors) {
                                skipBy(-SKIP_STEP_MS)
                            }
                            PlayerControlButton(
                                icon = if (isPlaying) ClipIcons.Pause else ClipIcons.Play,
                                description = if (isPlaying) R.string.feature_player_pause else R.string.feature_player_play,
                                enabled = viewModel.opened,
                                colors = iconColors,
                                onClick = togglePlayback,
                            )
                            PlayerControlButton(ClipIcons.Forward5, R.string.feature_player_forward, viewModel.opened, iconColors) {
                                skipBy(SKIP_STEP_MS)
                            }
                            PlayerControlButton(ClipIcons.SkipNext, R.string.feature_player_next, false, iconColors) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    @StringRes description: Int,
    enabled: Boolean,
    colors: IconButtonColors,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = Modifier.size(dimensionResource(R.dimen.feature_player_control_button_size)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            modifier = Modifier.size(dimensionResource(R.dimen.feature_player_control_icon_size)),
        )
    }
}

@Composable
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return stringResource(R.string.feature_player_time, minutes, seconds)
}
