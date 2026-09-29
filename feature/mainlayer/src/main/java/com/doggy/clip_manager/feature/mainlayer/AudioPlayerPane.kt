package com.doggy.clip_manager.feature.mainlayer

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    private var mediaPlayer: MediaPlayer? = null
    private var openedPath: String? = null

    var isPlaying: Boolean by mutableStateOf(false)
        private set
    var opened: Boolean by mutableStateOf(false)
        private set
    var durationMs: Long by mutableStateOf(0L)
        private set

    fun open(path: String) {
        if (path == openedPath) return
        release()
        openedPath = path
        opened = try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener { this@AudioPlayerViewModel.isPlaying = false }
            }
            durationMs = mediaPlayer?.duration?.toLong() ?: 0L
            true
        } catch (e: Exception) {
            false
        }
    }

    fun positionMs(): Long = mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun togglePlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            player.start()
            isPlaying = true
        }
    }

    fun seekTo(ms: Long) {
        mediaPlayer?.seekTo(ms.toInt())
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        openedPath = null
        opened = false
        isPlaying = false
        durationMs = 0L
    }

    override fun onCleared() = release()
}

@Composable
fun AudioPlayerPane(path: String?, modifier: Modifier = Modifier, viewModel: AudioPlayerViewModel = hiltViewModel()) {
    if (path == null) {
        Text("열 수 있는 음성 파일이 없습니다", modifier = modifier.padding(16.dp))
        return
    }
    LaunchedEffect(path) { viewModel.open(path) }
    DisposableEffect(Unit) { onDispose { viewModel.release() } }

    if (!viewModel.opened) {
        Text("파일을 열 수 없습니다", modifier = modifier.padding(16.dp))
        return
    }

    var sliderPositionMs by remember(path) { mutableFloatStateOf(0f) }
    var isDragging by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path, isDragging) {
        while (!isDragging) {
            sliderPositionMs = viewModel.positionMs().toFloat()
            delay(200)
        }
    }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(com.doggy.clip_manager.core.designsystem.icon.ClipIcons.Audio, contentDescription = null)
        Slider(
            value = sliderPositionMs,
            valueRange = 0f..(if (viewModel.durationMs > 0) viewModel.durationMs.toFloat() else 1f),
            onValueChange = {
                isDragging = true
                sliderPositionMs = it
            },
            onValueChangeFinished = {
                viewModel.seekTo(sliderPositionMs.toLong())
                isDragging = false
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        IconButton(onClick = { viewModel.togglePlayback() }) {
            Icon(
                if (viewModel.isPlaying) com.doggy.clip_manager.core.designsystem.icon.ClipIcons.Pause else com.doggy.clip_manager.core.designsystem.icon.ClipIcons.Play,
                contentDescription = null,
            )
        }
    }
}
