package com.doggy.clip_manager.core.database.di

import android.content.Context
import androidx.room.Room
import com.doggy.clip_manager.core.database.ClipDatabase
import com.doggy.clip_manager.core.database.dao.RecentPlaybackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesClipDatabase(@ApplicationContext context: Context): ClipDatabase =
        Room.databaseBuilder(context, ClipDatabase::class.java, "clip-database").build()

    @Provides
    fun providesRecentPlaybackDao(database: ClipDatabase): RecentPlaybackDao = database.recentPlaybackDao()
}
