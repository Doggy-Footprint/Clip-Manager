package com.doggy.clip_manager.core.data.repository

import com.doggy.clip_manager.core.model.RecentPlayback
import kotlinx.coroutines.flow.Flow

interface RecentPlaybackRepository {
    fun observeRecent(limit: Int): Flow<List<RecentPlayback>>

    suspend fun record(playback: RecentPlayback)
}
