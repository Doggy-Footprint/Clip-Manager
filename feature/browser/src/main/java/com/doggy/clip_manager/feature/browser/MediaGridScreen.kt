package com.doggy.clip_manager.feature.browser

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.doggy.clip_manager.core.designsystem.component.EmptyState
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MediaGridScreen(
    uiState: MediaGridUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onEntryClick: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
    selectedUri: String? = null,
) {
    val success = uiState as? MediaGridUiState.Success
    when {
        !permissionGranted -> EmptyState(
            icon = ClipIcons.Lock,
            title = stringResource(R.string.feature_browser_permission_title),
            body = stringResource(R.string.feature_browser_permission_body),
            actionLabel = stringResource(R.string.feature_browser_permission_action),
            onAction = onRequestPermission,
            modifier = modifier,
        )
        success == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        success.entries.isEmpty() -> EmptyState(
            icon = ClipIcons.FolderOpen,
            title = stringResource(R.string.feature_browser_media_empty),
            modifier = modifier,
        )
        else -> {
            val cellSize = dimensionResource(R.dimen.feature_browser_image_cell_size)
            val spacing = dimensionResource(R.dimen.feature_browser_image_cell_spacing)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = cellSize),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = modifier.fillMaxSize(),
            ) {
                items(success.entries, key = { it.uri }) { entry ->
                    MediaGridCell(
                        entry = entry,
                        selected = entry.uri == selectedUri,
                        onClick = { onEntryClick(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaGridCell(entry: MediaEntry, selected: Boolean, onClick: () -> Unit) {
    val spacing = dimensionResource(R.dimen.feature_browser_image_cell_spacing)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(spacing),
    ) {
        MediaThumbnailImage(entry, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            entry.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaThumbnailImage(entry: MediaEntry, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val thumbnailPx = with(LocalDensity.current) {
        dimensionResource(R.dimen.feature_browser_image_cell_size).roundToPx()
    }
    var thumbnail: Bitmap? by remember(entry.uri) { mutableStateOf(null) }
    LaunchedEffect(entry.uri, thumbnailPx) {
        thumbnail = withContext(Dispatchers.IO) {
            runCatching { loadThumbnail(resolver, Uri.parse(entry.uri), thumbnailPx) }.getOrNull()
        }
    }
    val bitmap = thumbnail
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = entry.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        return
    }
    // Audio tracks without album art, and anything whose thumbnail failed, fall back to their kind icon.
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = when (entry.kind) {
                MediaKind.VIDEO -> ClipIcons.Video
                MediaKind.AUDIO -> ClipIcons.Audio
                MediaKind.IMAGE -> ClipIcons.Image
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(R.dimen.feature_browser_compact_icon_size)),
        )
    }
}
