package com.doggy.clip_manager.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doggy.clip_manager.core.designsystem.component.ClipTopAppBar
import com.doggy.clip_manager.core.designsystem.component.EmptyState
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.model.FileEntry
import com.doggy.clip_manager.core.ui.rememberStoragePermissionState

@Composable
internal fun BrowserScreenRoute(
    onOpenVideo: (String) -> Unit,
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
) {
    val success = uiState as? BrowserUiState.Success
    Column(Modifier.fillMaxSize()) {
        ClipTopAppBar(
            title = success?.takeUnless { it.isAtRoot }?.currentDir?.name
                ?: stringResource(R.string.feature_browser_title),
            subtitle = success?.currentDir?.absolutePath?.takeIf { permissionGranted },
            onNavigateUp = if (permissionGranted && success != null && !success.isAtRoot) onNavigateUp else null,
        )
        when {
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
                    FileRow(entry = entry, onClick = { onEntryClick(entry) })
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    val (icon, tint) = when {
        entry.isDirectory -> ClipIcons.Folder to MaterialTheme.colorScheme.primary
        entry.isVideo -> ClipIcons.Video to MaterialTheme.colorScheme.secondary
        else -> ClipIcons.File to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        headlineContent = { Text(entry.file.name, maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        modifier = Modifier.clickable(enabled = entry.isDirectory || entry.isVideo, onClick = onClick),
    )
}
