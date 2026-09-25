package com.doggy.clip_manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.doggy.clip_manager.R
import com.doggy.clip_manager.core.extension.ExtensionNavigator
import com.doggy.clip_manager.core.extension.LocalExtensionNavigator
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.feature.browser.BrowserScreenRoute
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.feature.browser.ImageGridRoute
import com.doggy.clip_manager.feature.browser.MediaGridRoute
import com.doggy.clip_manager.feature.editor.EditorPane
import com.doggy.clip_manager.feature.editor.EditorViewModel
import com.doggy.clip_manager.feature.player.ImageViewerPane
import com.doggy.clip_manager.feature.player.PlayerPane

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
                val viewer = @Composable { modifier: Modifier ->
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
                if (isFullscreen) {
                    viewer(Modifier.fillMaxSize())
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
                        val explorer = @Composable { modifier: Modifier ->
                            val key = effectiveTab
                            when {
                                editor.editing ->
                                    ImageGridRoute(onImageSelected = { editor.addImageOverlay(it) }, modifier = modifier)
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
                                explorer(Modifier.weight(1f))
                                viewer(Modifier.weight(1f))
                            }
                            else -> {
                                val explorerWidth = if (layout == LayerLayout.THIN) R.dimen.explorer_thin_width else R.dimen.explorer_width
                                explorer(Modifier.width(dimensionResource(explorerWidth)).fillMaxHeight())
                                VerticalDivider()
                                viewer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
