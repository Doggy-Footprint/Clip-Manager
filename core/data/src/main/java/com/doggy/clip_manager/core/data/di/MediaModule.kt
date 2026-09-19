package com.doggy.clip_manager.core.data.di

import com.doggy.clip_manager.core.data.media.DefaultMediaRepository
import com.doggy.clip_manager.core.data.media.MediaFilter
import com.doggy.clip_manager.core.data.media.MediaRepository
import com.doggy.clip_manager.core.data.media.MediaSource
import com.doggy.clip_manager.core.data.media.MediaStoreMediaSource
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface MediaModule {
    @Binds
    @IntoSet
    fun bindsMediaStoreSource(source: MediaStoreMediaSource): MediaSource

    /** No filters are bound yet; path rules will add `@Binds @IntoSet` entries here. */
    @Multibinds
    fun mediaFilters(): Set<MediaFilter>

    @Binds
    fun bindsMediaRepository(repository: DefaultMediaRepository): MediaRepository
}
