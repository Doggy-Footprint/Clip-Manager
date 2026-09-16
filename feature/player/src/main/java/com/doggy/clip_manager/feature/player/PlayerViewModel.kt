package com.doggy.clip_manager.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.doggy.clip_manager.core.player.NativePlayer
import com.doggy.clip_manager.feature.player.navigation.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    val path: String = savedStateHandle.toRoute<PlayerRoute>().path
    val player = NativePlayer()
    val opened: Boolean = player.open(path)
    val durationMs: Long = if (opened) player.durationMs() else 0L

    init {
        if (opened) player.play()
    }

    override fun onCleared() {
        player.release()
    }
}
