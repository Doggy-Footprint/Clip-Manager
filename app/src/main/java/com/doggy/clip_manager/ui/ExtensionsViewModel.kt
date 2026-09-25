package com.doggy.clip_manager.ui

import androidx.lifecycle.ViewModel
import com.doggy.clip_manager.core.extension.BuiltInTab
import com.doggy.clip_manager.core.extension.BuiltInTabOverride
import com.doggy.clip_manager.core.extension.SettingsSection
import com.doggy.clip_manager.core.extension.TabExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    tabs: Set<@JvmSuppressWildcards TabExtension>,
    sections: Set<@JvmSuppressWildcards SettingsSection>,
    overrides: Set<@JvmSuppressWildcards BuiltInTabOverride>,
) : ViewModel() {
    val tabs: List<TabExtension> = orderedTabExtensions(tabs)
    val sections: List<SettingsSection> = orderedSettingsSections(sections)
    val overrides: Map<BuiltInTab, BuiltInTabOverride> = overridesByTarget(overrides)
}
