package com.doggy.clip_manager.ui

import androidx.compose.runtime.saveable.Saver
import com.doggy.clip_manager.core.extension.BuiltInTab
import com.doggy.clip_manager.core.extension.SettingsSection
import com.doggy.clip_manager.core.extension.TabExtension
import com.doggy.clip_manager.core.extension.BuiltInTabOverride

sealed interface TabKey {
    data class BuiltIn(val tab: MediaTab) : TabKey

    data class Extension(val id: String) : TabKey

    data object Settings : TabKey
}

// rememberSaveable cannot store a plain sealed interface directly, so TabKey is encoded to a
// single restorable String instead of introducing Parcelable/Serializable on the extension API.
val TabKeySaver: Saver<TabKey, String> = Saver(
    save = { key ->
        when (key) {
            is TabKey.BuiltIn -> "builtin:${key.tab.name}"
            is TabKey.Extension -> "ext:${key.id}"
            TabKey.Settings -> "settings"
        }
    },
    restore = { encoded ->
        when {
            encoded == "settings" -> TabKey.Settings
            encoded.startsWith("builtin:") ->
                TabKey.BuiltIn(MediaTab.valueOf(encoded.removePrefix("builtin:")))
            encoded.startsWith("ext:") ->
                TabKey.Extension(encoded.removePrefix("ext:"))
            else -> TabKey.BuiltIn(MediaTab.FILES)
        }
    },
)

fun orderedTabExtensions(set: Set<TabExtension>): List<TabExtension> =
    set.sortedWith(compareBy({ it.order }, { it.id }))

fun orderedSettingsSections(set: Set<SettingsSection>): List<SettingsSection> =
    set.sortedWith(compareBy({ it.order }, { it.id }))

fun overridesByTarget(set: Set<BuiltInTabOverride>): Map<BuiltInTab, BuiltInTabOverride> {
    val result = mutableMapOf<BuiltInTab, BuiltInTabOverride>()
    for (override in set) {
        if (result.containsKey(override.target)) {
            error("Duplicate BuiltInTabOverride for target ${override.target}")
        }
        result[override.target] = override
    }
    return result
}

fun resolveTabKey(key: TabKey, extensionIds: Set<String>): TabKey =
    if (key is TabKey.Extension && key.id !in extensionIds) {
        TabKey.BuiltIn(MediaTab.FILES)
    } else {
        key
    }
