package com.doggy.clip_manager.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.doggy.clip_manager.core.model.RecentPlayback

@Entity(tableName = "recent_playback")
data class RecentPlaybackEntity(
    @PrimaryKey val path: String,
    @ColumnInfo(name = "last_position_ms") val lastPositionMs: Long,
    @ColumnInfo(name = "played_at_ms") val playedAtMs: Long,
)

fun RecentPlaybackEntity.asExternalModel() = RecentPlayback(path, lastPositionMs, playedAtMs)
