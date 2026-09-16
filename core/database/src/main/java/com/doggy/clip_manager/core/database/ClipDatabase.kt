package com.doggy.clip_manager.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.doggy.clip_manager.core.database.dao.RecentPlaybackDao
import com.doggy.clip_manager.core.database.model.RecentPlaybackEntity

@Database(entities = [RecentPlaybackEntity::class], version = 1, exportSchema = true)
abstract class ClipDatabase : RoomDatabase() {
    abstract fun recentPlaybackDao(): RecentPlaybackDao
}
