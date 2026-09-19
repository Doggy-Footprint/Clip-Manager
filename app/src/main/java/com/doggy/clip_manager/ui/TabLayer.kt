package com.doggy.clip_manager.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.doggy.clip_manager.R
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons

enum class MediaTab(internal val icon: ImageVector, @StringRes internal val label: Int) {
    FILES(ClipIcons.Folder, R.string.tab_files),
    VIDEOS(ClipIcons.Video, R.string.tab_videos),
    AUDIO(ClipIcons.Audio, R.string.tab_audio),
    IMAGES(ClipIcons.Image, R.string.tab_images),
}

@Composable
fun TabLayer(
    selected: MediaTab,
    onSelect: (MediaTab) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var dummySelection by rememberSaveable { mutableStateOf<String?>(null) }

    NavigationRail(modifier = modifier.fillMaxHeight().width(dimensionResource(if (compact) R.dimen.tab_layer_compact_width else R.dimen.tab_layer_width))) {
        MediaTab.entries.forEach { tab ->
            RailItem(
                icon = tab.icon,
                label = stringResource(tab.label),
                compact = compact,
                selected = dummySelection == null && selected == tab,
                onClick = {
                    dummySelection = null
                    onSelect(tab)
                },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = dimensionResource(R.dimen.tab_layer_divider_padding)))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val tag = stringResource(R.string.tab_dummy_tag)
            RailItem(
                icon = ClipIcons.Tag,
                label = tag,
                compact = compact,
                selected = dummySelection == "#$tag",
                onClick = { dummySelection = "#$tag" },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = dimensionResource(R.dimen.tab_layer_divider_padding)))
        RailItem(
            icon = ClipIcons.Settings,
            label = stringResource(R.string.tab_settings),
            compact = compact,
            selected = false,
            onClick = {},
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
