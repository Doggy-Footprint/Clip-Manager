package com.doggy.clip_manager.core.extension

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class BuiltInTab { FILES, VIDEOS, AUDIO, IMAGES }

interface TabExtension {
    val id: String
    val icon: ImageVector
    val order: Int

    @Composable
    fun label(): String

    @Composable
    fun Content(modifier: Modifier)
}

interface SettingsSection {
    val id: String
    val order: Int

    @Composable
    fun title(): String

    @Composable
    fun Content()
}

interface BuiltInTabOverride {
    val target: BuiltInTab

    @Composable
    fun Content(modifier: Modifier, default: @Composable (Modifier) -> Unit)
}
