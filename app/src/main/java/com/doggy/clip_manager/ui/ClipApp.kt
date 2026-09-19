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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.doggy.clip_manager.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.feature.browser.BrowserScreenRoute
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
    var selectedTab by rememberSaveable { mutableStateOf(MediaTab.FILES) }
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMediaUri by rememberSaveable { mutableStateOf<String?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val editor: EditorViewModel = hiltViewModel()

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    Surface(Modifier.fillMaxSize()) {
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
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    compact = layout != LayerLayout.WIDE,
                )
                VerticalDivider()
                val explorer = @Composable { modifier: Modifier ->
                    when {
                        editor.editing ->
                            ImageGridRoute(onImageSelected = { editor.addImageOverlay(it) }, modifier = modifier)
                        selectedTab == MediaTab.FILES -> BrowserScreenRoute(
                            onOpenVideo = {
                                selectedImageUri = null
                                selectedPath = it
                            },
                            selectedPath = selectedPath,
                            compact = layout == LayerLayout.THIN,
                            modifier = modifier,
                        )
                        else -> MediaGridRoute(
                            kind = when (selectedTab) {
                                MediaTab.VIDEOS -> MediaKind.VIDEO
                                MediaTab.AUDIO -> MediaKind.AUDIO
                                else -> MediaKind.IMAGE
                            },
                            onEntrySelected = { entry ->
                                if (entry.kind == MediaKind.IMAGE) {
                                    selectedImageUri = entry.uri
                                } else {
                                    selectedImageUri = null
                                    entry.filePath?.let { selectedPath = it }
                                }
                                selectedMediaUri = entry.uri
                            },
                            selectedUri = selectedMediaUri,
                            modifier = modifier,
                        )
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
