package com.doggy.clip_manager.core.data.di

import com.doggy.clip_manager.core.data.repository.FileRepository
import com.doggy.clip_manager.core.data.repository.LocalFileRepository
import com.doggy.clip_manager.core.data.repository.OfflineRecentPlaybackRepository
import com.doggy.clip_manager.core.data.repository.RecentPlaybackRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun bindsFileRepository(repository: LocalFileRepository): FileRepository

    @Binds
    fun bindsRecentPlaybackRepository(repository: OfflineRecentPlaybackRepository): RecentPlaybackRepository
}
