package com.doggy.clip_manager.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.doggy.clip_manager.R
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.feature.browser.navigation.BrowserRoute
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val labelRes: Int,
    val route: Any,
    val routeClass: KClass<*>,
) {
    FILES(ClipIcons.Folder, ClipIcons.FolderOutlined, R.string.tab_files, BrowserRoute, BrowserRoute::class),
    TAGS(ClipIcons.Tag, ClipIcons.TagOutlined, R.string.tab_tags, TagsRoute, TagsRoute::class),
}
