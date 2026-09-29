package com.doggy.clip_manager.feature.sidelayer

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doggy.clip_manager.core.data.operation.ConflictPolicy
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import com.doggy.clip_manager.core.designsystem.component.EmptyState
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun MediaEntry.toSource(): FileOperationSource =
    filePath?.let { FileOperationSource.Local(java.io.File(it)) } ?: FileOperationSource.Content(Uri.parse(uri), displayName)

@Composable
internal fun MediaGridScreen(
    uiState: MediaGridUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onEntryClick: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
    selectedUri: String? = null,
    onEntryLongClick: (MediaEntry) -> Unit = {},
    operations: OperationViewModel? = null,
) {
    val success = uiState as? MediaGridUiState.Success

    DisposableEffect(Unit) { onDispose { operations?.leaveScreen() } }

    val selectionMode = operations?.selectionMode?.collectAsStateWithLifecycle()?.value ?: false
    val selected = operations?.selected?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val staged = operations?.staged?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val conflict = operations?.conflict?.collectAsStateWithLifecycle()?.value
    var applyRemaining by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        if (operations != null) {
            SelectionTopBar(
                selectionMode = selectionMode,
                count = selected.size,
                onSelect = { operations.enterSelection() },
                onExit = { operations.clearSelection() },
            )
        }
        Box(Modifier.weight(1f)) {
            when {
                !permissionGranted -> EmptyState(
                    icon = ClipIcons.Lock,
                    title = stringResource(R.string.feature_sidelayer_permission_title),
                    body = stringResource(R.string.feature_sidelayer_permission_body),
                    actionLabel = stringResource(R.string.feature_sidelayer_permission_action),
                    onAction = onRequestPermission,
                )
                success == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                success.entries.isEmpty() -> EmptyState(
                    icon = ClipIcons.FolderOpen,
                    title = stringResource(R.string.feature_sidelayer_media_empty),
                )
                else -> {
                    val cellSize = dimensionResource(R.dimen.feature_sidelayer_image_cell_size)
                    val spacing = dimensionResource(R.dimen.feature_sidelayer_image_cell_spacing)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = cellSize),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(success.entries, key = { it.uri }) { entry ->
                            val entrySelected = entry.uri == selectedUri ||
                                (operations != null && selected.contains(entry.toSource()))
                            MediaGridCell(
                                entry = entry,
                                selected = entrySelected,
                                onClick = {
                                    if (operations != null && selectionMode) operations.toggle(entry.toSource()) else onEntryClick(entry)
                                },
                                // R1 v3: long-press opens this cell's action menu, it does not enter
                                // selection mode; the caller-supplied callback still fires alongside it.
                                onLongClick = { onEntryLongClick(entry) },
                                operations = operations,
                            )
                        }
                    }
                }
            }
        }
        if (operations != null) {
            if (selectionMode) {
                SelectionBottomBar(
                    enabled = selected.isNotEmpty(),
                    onCopy = { operations.stage(move = false) },
                    onMove = { operations.stage(move = true) },
                    onZip = {
                        val dir = android.os.Environment.getExternalStorageDirectory()
                        operations.zip(selected, java.io.File(dir, "archive_${System.currentTimeMillis()}.zip"))
                    },
                    onDelete = { operations.delete(selected) },
                    onInfo = { showInfo = true },
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("정보") },
            text = { Text("선택 ${selected.size}건") },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("확인") } },
        )
    }

    val conflictSource = conflict
    if (operations != null && conflictSource != null) {
        ConflictDialog(
            name = when (conflictSource) {
                is FileOperationSource.Local -> conflictSource.file.name
                is FileOperationSource.Content -> conflictSource.name
                is FileOperationSource.Archived -> conflictSource.path.substringAfterLast('/')
            },
            applyRemaining = applyRemaining,
            onApplyRemainingChange = { applyRemaining = it },
            onSkip = { operations.resolveConflict(ConflictPolicy.SKIP, applyRemaining) },
            onAutoRename = { operations.resolveConflict(ConflictPolicy.AUTO_RENAME, applyRemaining) },
            onReplace = { operations.resolveConflict(ConflictPolicy.REPLACE, false) },
            onCancel = { operations.cancelConflict() },
        )
    }
}

@Composable
private fun MediaGridCell(
    entry: MediaEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    operations: OperationViewModel? = null,
) {
    val spacing = dimensionResource(R.dimen.feature_sidelayer_image_cell_spacing)
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { onLongClick(); if (operations != null) menuExpanded = true })
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
        if (operations != null) {
            val source = entry.toSource()
            ItemActionsMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onCopy = { operations.toggle(source); operations.stage(move = false) },
                onMove = { operations.toggle(source); operations.stage(move = true) },
                onZip = {
                    val dir = android.os.Environment.getExternalStorageDirectory()
                    operations.zip(listOf(source), java.io.File(dir, "archive_${System.currentTimeMillis()}.zip"))
                },
                onDelete = { operations.delete(listOf(source)) },
                onInfo = { },
            )
        }
    }
}

@Composable
private fun MediaThumbnailImage(entry: MediaEntry, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val thumbnailPx = with(LocalDensity.current) {
        dimensionResource(R.dimen.feature_sidelayer_image_cell_size).roundToPx()
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
            modifier = Modifier.size(dimensionResource(R.dimen.feature_sidelayer_compact_icon_size)),
        )
    }
}
