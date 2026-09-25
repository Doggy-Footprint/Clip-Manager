package com.doggy.clip_manager.core.extension

import dagger.Module
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtensionBindingsModule {
    @Multibinds
    abstract fun tabs(): Set<TabExtension>

    @Multibinds
    abstract fun sections(): Set<SettingsSection>

    @Multibinds
    abstract fun overrides(): Set<BuiltInTabOverride>
}
