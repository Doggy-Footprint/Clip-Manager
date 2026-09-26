package com.doggy.clip_manager.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.doggy.clip_manager.R
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.extension.BuiltInTab
import com.doggy.clip_manager.core.extension.TabExtension

enum class MediaTab(internal val icon: ImageVector, @StringRes internal val label: Int, val builtIn: BuiltInTab) {
    FILES(ClipIcons.Folder, R.string.tab_files, BuiltInTab.FILES),
    VIDEOS(ClipIcons.Video, R.string.tab_videos, BuiltInTab.VIDEOS),
    AUDIO(ClipIcons.Audio, R.string.tab_audio, BuiltInTab.AUDIO),
    IMAGES(ClipIcons.Image, R.string.tab_images, BuiltInTab.IMAGES),
}

@Composable
fun TabLayer(
    selected: TabKey,
    onSelect: (TabKey) -> Unit,
    extensions: List<TabExtension>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier.fillMaxHeight().width(dimensionResource(if (compact) R.dimen.tab_layer_compact_width else R.dimen.tab_layer_width))) {
        MediaTab.entries.forEach { tab ->
            RailItem(
                icon = tab.icon,
                label = stringResource(tab.label),
                compact = compact,
                selected = selected == TabKey.BuiltIn(tab),
                onClick = { onSelect(TabKey.BuiltIn(tab)) },
            )
        }
        extensions.forEach { ext ->
            RailItem(
                icon = ext.icon,
                label = ext.label(),
                compact = compact,
                selected = selected == TabKey.Extension(ext.id),
                onClick = { onSelect(TabKey.Extension(ext.id)) },
            )
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(Modifier.padding(vertical = dimensionResource(R.dimen.tab_layer_divider_padding)))
        RailItem(
            icon = ClipIcons.Settings,
            label = stringResource(R.string.tab_settings),
            compact = compact,
            selected = selected == TabKey.Settings,
            onClick = { onSelect(TabKey.Settings) },
        )
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    compact: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = if (compact) null else { { Text(label, maxLines = 1) } },
        alwaysShowLabel = !compact,
    )
}
