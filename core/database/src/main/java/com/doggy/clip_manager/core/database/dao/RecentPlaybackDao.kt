package com.doggy.clip_manager.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.doggy.clip_manager.core.database.model.RecentPlaybackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPlaybackDao {
    @Query("SELECT * FROM recent_playback ORDER BY played_at_ms DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentPlaybackEntity>>

    @Upsert
    suspend fun upsert(entity: RecentPlaybackEntity)
}
