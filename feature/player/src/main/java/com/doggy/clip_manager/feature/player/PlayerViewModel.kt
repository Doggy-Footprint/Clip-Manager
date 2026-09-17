package com.doggy.clip_manager.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.doggy.clip_manager.core.player.NativePlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {
    var path: String? by mutableStateOf(null)
        private set
    var player: NativePlayer by mutableStateOf(NativePlayer())
        private set
    var opened: Boolean by mutableStateOf(false)
        private set
    var durationMs: Long by mutableStateOf(0L)
        private set
    var videoAspectRatio: Float? by mutableStateOf(null)
        private set

    fun open(newPath: String) {
        if (newPath == path) return
        player.release()
        val next = NativePlayer()
        val ok = next.open(newPath)
        path = newPath
        player = next
        opened = ok
        durationMs = if (ok) next.durationMs() else 0L
        videoAspectRatio = if (ok && next.videoWidth() > 0 && next.videoHeight() > 0) {
            next.videoWidth().toFloat() / next.videoHeight()
        } else {
            null
        }
        if (ok) next.play()
    }

    override fun onCleared() {
        player.release()
    }
}
