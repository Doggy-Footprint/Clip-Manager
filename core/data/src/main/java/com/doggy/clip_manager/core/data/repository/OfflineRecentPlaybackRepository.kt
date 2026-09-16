package com.doggy.clip_manager.core.data.repository

import com.doggy.clip_manager.core.database.dao.RecentPlaybackDao
import com.doggy.clip_manager.core.database.model.RecentPlaybackEntity
import com.doggy.clip_manager.core.database.model.asExternalModel
import com.doggy.clip_manager.core.model.RecentPlayback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class OfflineRecentPlaybackRepository @Inject constructor(
    private val dao: RecentPlaybackDao,
) : RecentPlaybackRepository {
    override fun observeRecent(limit: Int): Flow<List<RecentPlayback>> =
        dao.observeRecent(limit).map { entities -> entities.map { it.asExternalModel() } }

    override suspend fun record(playback: RecentPlayback) =
        dao.upsert(RecentPlaybackEntity(playback.path, playback.lastPositionMs, playback.playedAtMs))
}
