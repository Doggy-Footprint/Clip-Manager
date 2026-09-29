package com.doggy.clip_manager.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object OperationModule {
    @Provides
    fun provideTrashClock(): () -> Long = { System.currentTimeMillis() }
}
