package com.doggy.clip_manager.feature.editor

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.designsystem.theme.ClipTheme
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.FlipRange
import com.doggy.clip_manager.core.editor.FrameLayout
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.SpeedRange
import com.doggy.clip_manager.core.editor.TimeRange
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce

/** Snapshot of every VM field the preview must react to, so [snapshotFlow] observes them all at once. */
private data class EditorPreviewSnapshot(
    val path: String?,
    val durationUs: Long,
    val target: Pair<Surface?, Size>,
    val selection: TimeRange,
    val cutMode: CutMode,
    val frameLayout: FrameLayout,
    val flips: List<FlipRange>,
    val speeds: List<SpeedRange>,
)

/**
 * Editing replaces the playback surface: [OverlayPreviewPlayer] owns its own [SurfaceView] rather
 * than borrowing the one [NativePlayer] draws into, so the two never contend for a surface.
 */
@UnstableApi
@Composable
fun EditorPane(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val overlays by viewModel.overlays.collectAsStateWithLifecycle()
    val preview = remember { OverlayPreviewPlayer(context) }
    var surface by remember { mutableStateOf<Surface?>(null) }
    var surfaceSize by remember { mutableStateOf(Size(0, 0)) }
    var previewError by remember { mutableStateOf(false) }

    BackHandler { viewModel.end() }

    DisposableEffect(preview) { onDispose { preview.release() } }

    val path = viewModel.path
    @OptIn(FlowPreview::class)
    LaunchedEffect(preview) {
        // Every rebuild tears down and recreates CompositionPlayer, so a drag or a keystroke must
        // not refresh the preview per event. Effect fields are read here so a ratio/flip/speed
        // change rebuilds the preview the same way an overlay change does (F9).
        combine(
            snapshotFlow {
                EditorPreviewSnapshot(
                    path = viewModel.path,
                    durationUs = viewModel.durationUs,
                    target = surface to surfaceSize,
                    selection = viewModel.selection,
                    cutMode = viewModel.cutMode,
                    frameLayout = viewModel.frameLayout,
                    flips = viewModel.flips,
                    speeds = viewModel.speeds,
                )
            },
            viewModel.overlays,
        ) { snapshot, _ -> snapshot }.debounce(PREVIEW_REFRESH_DEBOUNCE_MS).collect { snapshot ->
            val (currentSurface, size) = snapshot.target
            if (snapshot.path == null || currentSurface == null || size.width == 0 || snapshot.durationUs <= 0L) return@collect
            val spec = viewModel.exportSpec() ?: return@collect
            previewError = preview.show(spec, snapshot.durationUs, currentSurface, size).isFailure
        }
    }

    ClipTheme(darkTheme = true) {
        Column(modifier.fillMaxSize().background(colorResource(R.color.feature_editor_background))) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) = Unit

                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    surfaceSize = Size(width, height)
                                    surface = holder.surface
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    surface = null
                                    preview.release()
                                }
                            })
                        }
                    },
                )
                if (previewError || path == null) {
                    Text(
                        stringResource(R.string.feature_editor_preview_failed),
                        color = colorResource(R.color.feature_editor_content),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val selected = overlays.firstOrNull { it.id == viewModel.selectedOverlayId } as? ImageOverlay
                if (selected != null) {
                    ImageOverlayEditor(
                        overlay = selected,
                        onTransformChange = { viewModel.update(selected.copy(transform = it)) },
                        onDelete = { viewModel.remove(selected.id) },
                    )
                }
            }
            EditorToolPanel(
                viewModel = viewModel,
                overlays = overlays,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.feature_editor_panel_height)),
            )
        }
    }
}

private const val PREVIEW_REFRESH_DEBOUNCE_MS = 400L
