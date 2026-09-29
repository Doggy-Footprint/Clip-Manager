package com.doggy.clip_manager.feature.sidelayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doggy.clip_manager.core.data.ViewerFormats
import com.doggy.clip_manager.core.data.operation.ConflictPolicy
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import com.doggy.clip_manager.core.designsystem.component.ClipTopAppBar
import com.doggy.clip_manager.core.designsystem.component.EmptyState
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.model.FileEntry
import com.doggy.clip_manager.core.ui.rememberStoragePermissionState
import java.io.File

private val ARCHIVE_EXTENSIONS = setOf("zip", "cbz", "7z", "tar")

private fun isArchiveName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".zip") || lower.endsWith(".cbz") || lower.endsWith(".7z") ||
        lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".tar.xz")
}

@Composable
fun BrowserScreenRoute(
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedPath: String? = null,
    compact: Boolean = false,
    onOpenViewer: (FileOperationSource) -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel(),
    operations: OperationViewModel = hiltViewModel(),
) {
    val permission = rememberStoragePermissionState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val passwordRequest by viewModel.passwordRequest.collectAsStateWithLifecycle()
    val passwordError by viewModel.passwordError.collectAsStateWithLifecycle()
    val archiveError by viewModel.archiveError.collectAsStateWithLifecycle()
    val refreshTick by operations.refreshTick.collectAsStateWithLifecycle()
    val message by operations.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(permission.granted) {
        if (permission.granted) viewModel.reload()
    }
    // R14: a mutating op (copy/move paste, delete, ZIP, restore, in-archive edit) bumps this so
    // the currently browsed folder or archive path reflects the change without a manual refresh.
    LaunchedEffect(refreshTick) {
        if (refreshTick > 0) viewModel.reload()
    }
    // R16: OperationViewModel.message is otherwise never surfaced; shown once as a Snackbar then
    // consumed so it isn't re-shown on the next recomposition/rotation.
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        operations.consumeMessage()
    }
    val isAtRoot = (uiState as? BrowserUiState.Success)?.isAtRoot ?: true
    BackHandler(enabled = permission.granted && !isAtRoot) { viewModel.navigateUp() }

    Box(modifier.fillMaxSize()) {
        BrowserScreen(
            uiState = uiState,
            modifier = Modifier.fillMaxSize(),
            selectedPath = selectedPath,
            compact = compact,
            permissionGranted = permission.granted,
            onRequestPermission = permission::request,
            onEntryClick = { entry ->
                val success = uiState as? BrowserUiState.Success
                when {
                    entry.isDirectory -> viewModel.open(entry)
                    success?.archive != null -> {
                        val path = entry.file.path.removePrefix(success.archive.archive.path).trim('/')
                        onOpenViewer(FileOperationSource.Archived(success.archive.archive, path, success.archive.password))
                    }
                    entry.isVideo -> onOpenVideo(entry.file.absolutePath)
                    // R6 v4: CBZ short tap opens the comic viewer; other archives still open as a folder.
                    ViewerFormats.isViewable(entry.file.name) -> onOpenViewer(FileOperationSource.Local(entry.file))
                    isArchiveName(entry.file.name) -> viewModel.openArchive(entry.file)
                }
            },
            onEntryLongClick = { },
            onOpenAsFolder = { entry -> viewModel.openArchive(entry.file) },
            onNavigateUp = { viewModel.navigateUp() },
            operations = operations,
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (passwordRequest != null) {
        var input by remember(passwordRequest) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.cancelArchivePassword() },
            title = { Text("암호") },
            text = {
                Column {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    if (passwordError) Text("암호가 올바르지 않습니다")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitArchivePassword(input.toCharArray()) }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelArchivePassword() }) { Text("취소") }
            },
        )
    }
    if (archiveError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissArchiveError() },
            title = { Text("압축을 열 수 없습니다") },
            text = { Text(archiveError.orEmpty()) },
            confirmButton = { TextButton(onClick = { viewModel.dismissArchiveError() }) { Text("확인") } },
        )
    }
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
    onEntryLongClick: (FileEntry) -> Unit = {},
    operations: OperationViewModel? = null,
    onOpenAsFolder: (FileEntry) -> Unit = {},
) {
    val success = uiState as? BrowserUiState.Success

    DisposableEffect(Unit) { onDispose { operations?.leaveScreen() } }
    LaunchedEffect(success?.currentDir, success?.archive?.path) { operations?.leaveScreen() }

    val selectionMode = operations?.selectionMode?.collectAsStateWithLifecycle()?.value ?: false
    val selected = operations?.selected?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val staged = operations?.staged?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val conflict = operations?.conflict?.collectAsStateWithLifecycle()?.value
    var applyRemaining by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    fun sourceOf(entry: FileEntry): FileOperationSource {
        val archive = success?.archive
        return if (archive != null) {
            FileOperationSource.Archived(archive.archive, entry.file.path.removePrefix(archive.archive.path).trim('/'), archive.password)
        } else {
            FileOperationSource.Local(entry.file)
        }
    }

    Column(modifier.fillMaxSize()) {
        if (operations != null) {
            SelectionTopBar(
                selectionMode = selectionMode,
                count = selected.size,
                onSelect = { operations.enterSelection() },
                onExit = { operations.clearSelection() },
            )
        }
        if (compact) {
            if (permissionGranted && success != null && !success.isAtRoot) {
                IconButton(onClick = onNavigateUp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(ClipIcons.ArrowBack, contentDescription = null)
                }
            }
        } else ClipTopAppBar(
            title = success?.archive?.let { archive ->
                if (archive.path.isEmpty()) archive.archive.name else "${archive.archive.name}/${archive.path}"
            } ?: success?.takeUnless { it.isAtRoot }?.currentDir?.name
                ?: stringResource(R.string.feature_sidelayer_title),
            subtitle = success?.archive?.archive?.absolutePath?.takeIf { permissionGranted }
                ?: success?.currentDir?.absolutePath?.takeIf { permissionGranted },
            onNavigateUp = if (permissionGranted && success != null && !success.isAtRoot) onNavigateUp else null,
        )
        Box(Modifier.weight(1f)) {
            when {
                compact && (!permissionGranted || success == null || success.entries.isEmpty()) -> Unit
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
                    title = stringResource(R.string.feature_sidelayer_empty_title),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(success.entries, key = { it.file.absolutePath }) { entry ->
                        val entrySelected = entry.file.absolutePath == selectedPath ||
                            (operations != null && selected.contains(sourceOf(entry)))
                        val onClick = {
                            if (operations != null && selectionMode) operations.toggle(sourceOf(entry)) else onEntryClick(entry)
                        }
                        // R1 v3: long-press opens this row's own action menu, it does not enter
                        // selection mode; the caller-supplied callback still fires alongside it.
                        val onLongClick = { onEntryLongClick(entry) }
                        // R6 v4: CBZ opens as a comic viewer on tap, so its item menu offers this
                        // as the only way to browse it as a folder; other archives already do that on tap.
                        val openAsFolder: (() -> Unit)? = if (success?.archive == null && isArchiveName(entry.file.name)) {
                            { onOpenAsFolder(entry) }
                        } else {
                            null
                        }
                        if (compact) {
                            CompactFileCell(
                                entry = entry,
                                selected = entrySelected,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                operations = operations,
                                source = sourceOf(entry),
                                onOpenAsFolder = openAsFolder,
                            )
                            return@items
                        }
                        FileRow(
                            entry = entry,
                            selected = entrySelected,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            operations = operations,
                            source = sourceOf(entry),
                            onOpenAsFolder = openAsFolder,
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = dimensionResource(R.dimen.feature_sidelayer_divider_inset)))
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
                        val dir = success?.currentDir ?: File(".")
                        operations.zip(selected, File(dir, "archive_${System.currentTimeMillis()}.zip"))
                    },
                    onDelete = { operations.delete(selected) },
                    onInfo = { showInfo = true },
                )
            }
            val canPasteHere = success?.archive?.readOnly != true
            if (staged.isNotEmpty() && canPasteHere && success != null) {
                PasteBar(onPaste = { operations.paste(success.currentDestination()) })
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
            name = conflictName(conflictSource),
            applyRemaining = applyRemaining,
            onApplyRemainingChange = { applyRemaining = it },
            onSkip = { operations.resolveConflict(ConflictPolicy.SKIP, applyRemaining) },
            onAutoRename = { operations.resolveConflict(ConflictPolicy.AUTO_RENAME, applyRemaining) },
            onReplace = { operations.resolveConflict(ConflictPolicy.REPLACE, false) },
            onCancel = { operations.cancelConflict() },
        )
    }
}

private fun conflictName(source: FileOperationSource): String = when (source) {
    is FileOperationSource.Local -> source.file.name
    is FileOperationSource.Content -> source.name
    is FileOperationSource.Archived -> source.path.substringAfterLast('/')
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
private fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    operations: OperationViewModel?,
    source: FileOperationSource,
    onOpenAsFolder: (() -> Unit)? = null,
) {
    val (icon, tint) = entryIcon(entry)
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(entry.file.name, maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        trailingContent = if (operations != null) {
            {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(ClipIcons.MoreVert, contentDescription = null)
                    }
                    ItemActionsMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onCopy = { operations.toggle(source); operations.stage(move = false) },
                        onMove = { operations.toggle(source); operations.stage(move = true) },
                        onZip = { operations.zip(listOf(source), File(entry.file.parentFile, "archive_${System.currentTimeMillis()}.zip")) },
                        onDelete = { operations.delete(listOf(source)) },
                        onInfo = { },
                        onOpenAsFolder = onOpenAsFolder,
                    )
                }
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = selectionColor(selected)),
        modifier = Modifier.combinedClickable(
            enabled = entry.isDirectory || entry.isVideo || isArchiveName(entry.file.name) || ViewerFormats.isViewable(entry.file.name),
            onClick = onClick,
            onLongClick = { onLongClick(); if (operations != null) menuExpanded = true },
        ),
    )
}

@Composable
private fun CompactFileCell(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    operations: OperationViewModel? = null,
    source: FileOperationSource? = null,
    onOpenAsFolder: (() -> Unit)? = null,
) {
    val (icon, tint) = entryIcon(entry)
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(selectionColor(selected))
                .combinedClickable(
                    enabled = entry.isDirectory || entry.isVideo || isArchiveName(entry.file.name) || ViewerFormats.isViewable(entry.file.name),
                    onClick = onClick,
                    onLongClick = { onLongClick(); if (operations != null) menuExpanded = true },
                )
                .padding(dimensionResource(R.dimen.feature_sidelayer_compact_cell_padding)),
        ) {
            Icon(
                icon,
                contentDescription = entry.file.name,
                tint = tint,
                modifier = Modifier.size(dimensionResource(R.dimen.feature_sidelayer_compact_icon_size)),
            )
            Text(
                entry.file.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (operations != null && source != null) {
            ItemActionsMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onCopy = { operations.toggle(source); operations.stage(move = false) },
                onMove = { operations.toggle(source); operations.stage(move = true) },
                onZip = { operations.zip(listOf(source), File(entry.file.parentFile, "archive_${System.currentTimeMillis()}.zip")) },
                onDelete = { operations.delete(listOf(source)) },
                onInfo = { },
                onOpenAsFolder = onOpenAsFolder,
            )
        }
    }
}
