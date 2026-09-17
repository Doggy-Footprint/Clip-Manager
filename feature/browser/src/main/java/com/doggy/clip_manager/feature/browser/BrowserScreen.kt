package com.doggy.clip_manager.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doggy.clip_manager.core.designsystem.component.ClipTopAppBar
import com.doggy.clip_manager.core.designsystem.component.EmptyState
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.model.FileEntry
import com.doggy.clip_manager.core.ui.rememberStoragePermissionState

@Composable
fun BrowserScreenRoute(
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedPath: String? = null,
    compact: Boolean = false,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val permission = rememberStoragePermissionState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(permission.granted) {
        if (permission.granted) viewModel.reload()
    }
    val isAtRoot = (uiState as? BrowserUiState.Success)?.isAtRoot ?: true
    BackHandler(enabled = permission.granted && !isAtRoot) { viewModel.navigateUp() }

    BrowserScreen(
        uiState = uiState,
        modifier = modifier,
        selectedPath = selectedPath,
        compact = compact,
        permissionGranted = permission.granted,
        onRequestPermission = permission::request,
        onEntryClick = { entry ->
            when {
                entry.isDirectory -> viewModel.open(entry)
                entry.isVideo -> onOpenVideo(entry.file.absolutePath)
            }
        },
        onNavigateUp = { viewModel.navigateUp() },
    )
}

@Composable
internal fun BrowserScreen(
    uiState: BrowserUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    selectedPath: String? = null,
    compact: Boolean = false,
) {
    val success = uiState as? BrowserUiState.Success
    Column(modifier.fillMaxSize()) {
        if (compact) {
            if (permissionGranted && success != null && !success.isAtRoot) {
                IconButton(onClick = onNavigateUp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(ClipIcons.ArrowBack, contentDescription = null)
                }
            }
        } else ClipTopAppBar(
            title = success?.takeUnless { it.isAtRoot }?.currentDir?.name
                ?: stringResource(R.string.feature_browser_title),
            subtitle = success?.currentDir?.absolutePath?.takeIf { permissionGranted },
            onNavigateUp = if (permissionGranted && success != null && !success.isAtRoot) onNavigateUp else null,
        )
        when {
            compact && (!permissionGranted || success == null || success.entries.isEmpty()) -> Unit
            !permissionGranted -> EmptyState(
                icon = ClipIcons.Lock,
                title = stringResource(R.string.feature_browser_permission_title),
                body = stringResource(R.string.feature_browser_permission_body),
                actionLabel = stringResource(R.string.feature_browser_permission_action),
                onAction = onRequestPermission,
            )
            success == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            success.entries.isEmpty() -> EmptyState(
                icon = ClipIcons.FolderOpen,
                title = stringResource(R.string.feature_browser_empty_title),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(success.entries, key = { it.file.absolutePath }) { entry ->
                    val selected = entry.file.absolutePath == selectedPath
                    if (compact) {
                        CompactFileCell(entry = entry, selected = selected, onClick = { onEntryClick(entry) })
                        return@items
                    }
                    FileRow(entry = entry, selected = selected, onClick = { onEntryClick(entry) })
                    HorizontalDivider(modifier = Modifier.padding(start = dimensionResource(R.dimen.feature_browser_divider_inset)))
                }
            }
        }
    }
}

@Composable
private fun entryIcon(entry: FileEntry): Pair<ImageVector, Color> = when {
    entry.isDirectory -> ClipIcons.Folder to MaterialTheme.colorScheme.primary
    entry.isVideo -> ClipIcons.Video to MaterialTheme.colorScheme.secondary
    else -> ClipIcons.File to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun selectionColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

@Composable
private fun FileRow(entry: FileEntry, selected: Boolean, onClick: () -> Unit) {
    val (icon, tint) = entryIcon(entry)
    ListItem(
        headlineContent = { Text(entry.file.name, maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = selectionColor(selected)),
        modifier = Modifier.clickable(enabled = entry.isDirectory || entry.isVideo, onClick = onClick),
    )
}

@Composable
private fun CompactFileCell(entry: FileEntry, selected: Boolean, onClick: () -> Unit) {
    val (icon, tint) = entryIcon(entry)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionColor(selected))
            .clickable(enabled = entry.isDirectory || entry.isVideo, onClick = onClick)
            .padding(dimensionResource(R.dimen.feature_browser_compact_cell_padding)),
    ) {
        Icon(
            icon,
            contentDescription = entry.file.name,
            tint = tint,
            modifier = Modifier.size(dimensionResource(R.dimen.feature_browser_compact_icon_size)),
        )
        Text(
            entry.file.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
