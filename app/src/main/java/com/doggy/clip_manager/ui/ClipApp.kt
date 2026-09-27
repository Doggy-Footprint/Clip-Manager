package com.doggy.clip_manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.doggy.clip_manager.R
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.extension.ExtensionNavigator
import com.doggy.clip_manager.core.extension.LocalExtensionNavigator
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.feature.sidelayer.BrowserScreenRoute
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.feature.sidelayer.ImageGridRoute
import com.doggy.clip_manager.feature.sidelayer.MediaGridRoute
import com.doggy.clip_manager.feature.editor.EditorPane
import com.doggy.clip_manager.feature.editor.EditorToolboxStrip
import com.doggy.clip_manager.feature.editor.EditorViewModel
import com.doggy.clip_manager.feature.editor.R as EditorR
import com.doggy.clip_manager.feature.mainlayer.ImageViewerPane
import com.doggy.clip_manager.feature.mainlayer.PlayerPane
import kotlin.math.roundToInt

private enum class LayerLayout { WIDE, THIN, PORTRAIT }

@UnstableApi
@Composable
fun ClipApp() {
    var selectedTab: TabKey by rememberSaveable(stateSaver = TabKeySaver) { mutableStateOf(TabKey.BuiltIn(MediaTab.FILES)) }
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMediaUri by rememberSaveable { mutableStateOf<String?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var pendingSettingsSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    // F9: the floating panel's open state and position survive rotation and process restore the
    // same way the rest of this screen's navigation state does.
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    // Default offset clears the toggle button (D5): opening at (0, 0) would sit the panel directly
    // on top of the button that opens it, so it starts below/right of the corner the button occupies.
    val density = LocalDensity.current
    val defaultPanelOffsetPx = with(density) { dimensionResource(R.dimen.floating_panel_default_offset).toPx() }
    var panelOffsetX by rememberSaveable { mutableStateOf(defaultPanelOffsetPx) }
    var panelOffsetY by rememberSaveable { mutableStateOf(defaultPanelOffsetPx) }
    val navigator = remember {
        object : ExtensionNavigator {
            override fun openSettings(sectionId: String?) {
                selectedTab = TabKey.Settings
                pendingSettingsSectionId = sectionId
            }
        }
    }
    val editor: EditorViewModel = hiltViewModel()
    val extensionsViewModel: ExtensionsViewModel = hiltViewModel()
    val effectiveTab = resolveTabKey(
        selectedTab,
        extensionsViewModel.tabs.map { it.id }.toSet(),
    )

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    Surface(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalExtensionNavigator provides navigator) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val mainLayer = @Composable { modifier: Modifier ->
                    if (editor.editing) {
                        EditorPane(viewModel = editor, modifier = modifier)
                    } else if (selectedImageUri != null) {
                        ImageViewerPane(uri = selectedImageUri, modifier = modifier)
                    } else {
                        PlayerPane(
                            path = selectedPath,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = { isFullscreen = !isFullscreen },
                            onStartEdit = { durationUs ->
                                selectedPath?.let { editor.begin(it, durationUs) }
                            },
                            modifier = modifier,
                        )
                    }
                }
                if (editor.editing && editor.fullscreen) {
                    // A6: editor.fullscreen is independent of the player's isFullscreen, so this
                    // branch is checked separately and never falls through to it.
                    // Captured here: BoxWithConstraintsScope.maxWidth/maxHeight are only visible
                    // through this composable's implicit receiver, which the nested Box below shadows.
                    val outerMaxWidth = maxWidth
                    val outerMaxHeight = maxHeight
                    Box(Modifier.fillMaxSize()) {
                        EditorPane(viewModel = editor, modifier = Modifier.fillMaxSize())
                        if (panelOpen) {
                            val panelWidth = dimensionResource(R.dimen.floating_panel_width)
                            val panelHeight = dimensionResource(R.dimen.floating_panel_height)
                            val panelWidthPx = with(density) { panelWidth.toPx() }
                            val panelHeightPx = with(density) { panelHeight.toPx() }
                            val maxWidthPx = with(density) { outerMaxWidth.toPx() }
                            val maxHeightPx = with(density) { outerMaxHeight.toPx() }
                            // Re-clamped on every composition (not just while dragging), so a saved
                            // offset from a larger window - or one restored before this window's size
                            // is known - is corrected as soon as the new bounds are available, e.g. a
                            // rotation (D4) rather than only while the user next drags the panel.
                            val clampedOffsetX = panelOffsetX.coerceIn(0f, maxOf(0f, maxWidthPx - panelWidthPx))
                            val clampedOffsetY = panelOffsetY.coerceIn(0f, maxOf(0f, maxHeightPx - panelHeightPx))
                            SideEffect {
                                panelOffsetX = clampedOffsetX
                                panelOffsetY = clampedOffsetY
                            }
                            Surface(
                                tonalElevation = dimensionResource(R.dimen.floating_panel_elevation),
                                modifier = Modifier
                                    .offset { IntOffset(clampedOffsetX.roundToInt(), clampedOffsetY.roundToInt()) }
                                    .size(panelWidth, panelHeight)
                                    // EditingSideContent's own children (image grid items, the
                                    // toolbox strip) claim the Down for their own tap/scroll
                                    // handling in the (later) Main pass before a plain
                                    // detectDragGestures on this ancestor Surface would ever see an
                                    // unconsumed Down, so - same fix as the overlay row drag (D3) -
                                    // this reads movement directly in PointerEventPass.Initial
                                    // instead, and only consumes once actual movement occurs (a
                                    // plain tap on a child, e.g. adding an image, is left alone).
                                    .pointerInput(maxWidthPx, maxHeightPx, panelWidthPx, panelHeightPx) {
                                        awaitEachGesture {
                                            val pointerId = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).id
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                                if (!change.pressed) break
                                                val dragAmount = change.position - change.previousPosition
                                                if (dragAmount.x != 0f || dragAmount.y != 0f) {
                                                    change.consume()
                                                    // Clamped so the panel can be pushed to an edge but never
                                                    // dragged fully outside the window (F6).
                                                    panelOffsetX = (panelOffsetX + dragAmount.x).coerceIn(0f, maxOf(0f, maxWidthPx - panelWidthPx))
                                                    panelOffsetY = (panelOffsetY + dragAmount.y).coerceIn(0f, maxOf(0f, maxHeightPx - panelHeightPx))
                                                }
                                            }
                                        }
                                    },
                            ) {
                                EditingSideContent(editor = editor, modifier = Modifier.fillMaxSize())
                            }
                        }
                        // Drawn after the panel (D5): the toggle must stay clickable and visible on
                        // top of the panel, not be covered by it, so the user can always close it.
                        IconButton(
                            onClick = { panelOpen = !panelOpen },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(dimensionResource(R.dimen.floating_panel_toggle_padding)),
                        ) {
                            Icon(
                                imageVector = if (panelOpen) ClipIcons.FolderOpen else ClipIcons.Folder,
                                contentDescription = stringResource(
                                    if (panelOpen) R.string.floating_panel_close else R.string.floating_panel_open,
                                ),
                                // The default content color is too dark against the black editor
                                // background to be visible here (D5).
                                tint = colorResource(R.color.floating_panel_toggle_icon),
                            )
                        }
                    }
                    return@BoxWithConstraints
                }
                if (isFullscreen) {
                    mainLayer(Modifier.fillMaxSize())
                    return@BoxWithConstraints
                }
                val layout = when {
                    maxHeight > maxWidth -> LayerLayout.PORTRAIT
                    maxHeight < dimensionResource(R.dimen.thin_layout_max_height) -> LayerLayout.THIN
                    else -> LayerLayout.WIDE
                }
                Row(Modifier.fillMaxSize()) {
                    TabLayer(
                        selected = effectiveTab,
                        onSelect = { selectedTab = it },
                        extensions = extensionsViewModel.tabs,
                        compact = layout != LayerLayout.WIDE,
                    )
                    VerticalDivider()
                    if (effectiveTab == TabKey.Settings) {
                        SettingsScreen(
                            sections = extensionsViewModel.sections,
                            modifier = Modifier.weight(1f),
                            scrollToSectionId = pendingSettingsSectionId,
                            onScrolledToSection = { pendingSettingsSectionId = null },
                        )
                    } else {
                        val onMediaEntrySelected: (MediaEntry) -> Unit = { entry ->
                            if (entry.kind == MediaKind.IMAGE) {
                                selectedImageUri = entry.uri
                            } else {
                                selectedImageUri = null
                                entry.filePath?.let { selectedPath = it }
                            }
                            selectedMediaUri = entry.uri
                        }
                        fun defaultBuiltInContent(tab: MediaTab): @Composable (Modifier) -> Unit = { modifier ->
                            when (tab) {
                                MediaTab.FILES -> BrowserScreenRoute(
                                    onOpenVideo = {
                                        selectedImageUri = null
                                        selectedPath = it
                                    },
                                    selectedPath = selectedPath,
                                    compact = layout == LayerLayout.THIN,
                                    modifier = modifier,
                                )
                                MediaTab.VIDEOS -> MediaGridRoute(
                                    kind = MediaKind.VIDEO,
                                    onEntrySelected = onMediaEntrySelected,
                                    selectedUri = selectedMediaUri,
                                    modifier = modifier,
                                )
                                MediaTab.AUDIO -> MediaGridRoute(
                                    kind = MediaKind.AUDIO,
                                    onEntrySelected = onMediaEntrySelected,
                                    selectedUri = selectedMediaUri,
                                    modifier = modifier,
                                )
                                MediaTab.IMAGES -> MediaGridRoute(
                                    kind = MediaKind.IMAGE,
                                    onEntrySelected = onMediaEntrySelected,
                                    selectedUri = selectedMediaUri,
                                    modifier = modifier,
                                )
                            }
                        }
                        val sideLayer = @Composable { modifier: Modifier ->
                            val key = effectiveTab
                            when {
                                editor.editing -> EditingSideContent(editor = editor, modifier = modifier)
                                key is TabKey.BuiltIn -> {
                                    val default = defaultBuiltInContent(key.tab)
                                    val override = extensionsViewModel.overrides[key.tab.builtIn]
                                    if (override != null) {
                                        override.Content(modifier, default)
                                    } else {
                                        default(modifier)
                                    }
                                }
                                key is TabKey.Extension ->
                                    extensionsViewModel.tabs.firstOrNull { it.id == key.id }?.Content(modifier)
                            }
                        }
                        when (layout) {
                            LayerLayout.PORTRAIT -> Column(Modifier.weight(1f)) {
                                sideLayer(Modifier.weight(1f))
                                mainLayer(Modifier.weight(1f))
                            }
                            else -> {
                                val sideLayerWidth = if (layout == LayerLayout.THIN) R.dimen.side_layer_thin_width else R.dimen.side_layer_width
                                sideLayer(Modifier.width(dimensionResource(sideLayerWidth)).fillMaxHeight())
                                VerticalDivider()
                                mainLayer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The editing side layer's content (F1, F4): reused verbatim as the floating panel's content while
 * editor.fullscreen is active (F6), so both surfaces offer the same image source and toolbox.
 */
@UnstableApi
@Composable
private fun EditingSideContent(editor: EditorViewModel, modifier: Modifier = Modifier) {
    val overlays by editor.overlays.collectAsStateWithLifecycle()
    Column(modifier) {
        ImageGridRoute(onImageSelected = { editor.addImageOverlay(it) }, modifier = Modifier.weight(1f))
        EditorToolboxStrip(
            overlays = overlays,
            stowedOverlayIds = editor.stowedOverlayIds,
            onUnstow = editor::unstow,
            onStow = editor::stow,
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(EditorR.dimen.feature_editor_toolbox_strip_height)),
        )
    }
}
