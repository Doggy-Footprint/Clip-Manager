package com.doggy.clip_manager.core.testing.repository

import com.doggy.clip_manager.core.data.repository.RecentPlaybackRepository
import com.doggy.clip_manager.core.model.RecentPlayback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class TestRecentPlaybackRepository : RecentPlaybackRepository {
    private val playbacks = MutableStateFlow<List<RecentPlayback>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<RecentPlayback>> =
        playbacks.map { list -> list.sortedByDescending { it.playedAtMs }.take(limit) }

    override suspend fun record(playback: RecentPlayback) {
        playbacks.update { list -> list.filterNot { it.path == playback.path } + playback }
    }
}
