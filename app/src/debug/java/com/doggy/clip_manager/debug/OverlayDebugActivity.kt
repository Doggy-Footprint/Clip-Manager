package com.doggy.clip_manager.debug

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.data.repository.ImageRepository
import com.doggy.clip_manager.core.editor.EditEffects
import com.doggy.clip_manager.core.editor.EditService
import com.doggy.clip_manager.core.editor.EditSpec
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.OverlayEditSession
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.TimeRange
import com.doggy.clip_manager.core.model.ImageAsset
import com.doggy.clip_manager.core.model.ImageSource
import com.doggy.clip_manager.feature.browser.ImageGridScreen
import com.doggy.clip_manager.feature.player.ImageOverlayEditor
import com.doggy.clip_manager.feature.player.OverlayPreviewPlayer
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SEC = 1_000_000L
private val DEFAULT_RANGE = TimeRange(1 * SEC, 3 * SEC)

/**
 * Phase 3 verification harness: assembles the overlay session, image grid, overlay editor and
 * preview player so overlay rendering and the foreground-service export path can be exercised
 * before the Phase 4 tool UI exists. Debug source set only.
 */
@UnstableApi
@AndroidEntryPoint
class OverlayDebugActivity : ComponentActivity() {

    @Inject
    lateinit var imageRepository: ImageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                OverlayDebugScreen(imageRepository, intent.getStringExtra(EXTRA_VIDEO_PATH))
            }
        }
    }

    companion object {
        // Lets an adb launch preload the input so the verification run does not need the SAF picker.
        const val EXTRA_VIDEO_PATH = "video_path"
    }
}

@UnstableApi
@Composable
private fun OverlayDebugScreen(imageRepository: ImageRepository, initialVideoPath: String?) {
    val context = LocalContext.current
    val session = remember { OverlayEditSession() }
    val overlays by session.overlays.collectAsState()
    var images by remember { mutableStateOf(emptyList<ImageAsset>()) }
    var videoPath by remember { mutableStateOf<String?>(null) }
    var durationUs by remember { mutableStateOf(0L) }
    var previewing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("비디오를 선택하세요") }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copied = copyToCache(context, uri)
        videoPath = copied.absolutePath
        durationUs = durationUsOf(copied.absolutePath)
        status = "${copied.name} (${durationUs / SEC}s)"
    }

    LaunchedEffect(initialVideoPath) {
        val path = initialVideoPath ?: return@LaunchedEffect
        videoPath = path
        durationUs = durationUsOf(path)
        status = "${File(path).name} (${durationUs / SEC}s)"
    }

    LaunchedEffect(Unit) {
        images = withContext(Dispatchers.IO) { runCatching { imageRepository.listImages() }.getOrDefault(emptyList()) }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickVideo.launch(arrayOf("video/*")) }) { Text("비디오 선택") }
            Button(
                onClick = { previewing = !previewing },
                enabled = videoPath != null,
            ) { Text(if (previewing) "미리보기 종료" else "미리보기") }
            Button(
                onClick = {
                    val path = videoPath ?: return@Button
                    EditService.start(
                        context,
                        EditSpec(
                            inputPath = path,
                            keepRanges = listOf(TimeRange(0, durationUs)),
                            cutMode = CutMode.PRECISE,
                            effects = EditEffects(overlays = overlays),
                        ),
                    )
                    status = "내보내기 시작 (오버레이 ${overlays.size}개)"
                },
                enabled = videoPath != null && overlays.isNotEmpty(),
            ) { Text("내보내기") }
            Button(onClick = { session.clear() }, enabled = overlays.isNotEmpty()) { Text("오버레이 비우기") }
        }
        Text(status, Modifier.padding(vertical = 4.dp))

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageGridScreen(
                images = images,
                onImageSelected = { asset -> session.add(newOverlay(asset, overlays)) },
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.weight(2f)) {
                PreviewPane(
                    inputPath = videoPath,
                    durationUs = durationUs,
                    overlays = overlays,
                    active = previewing,
                    onError = { status = "미리보기 실패: $it" },
                )
                overlays.filterIsInstance<ImageOverlay>().lastOrNull()?.let { selected ->
                    ImageOverlayEditor(
                        overlay = selected,
                        onTransformChange = { session.update(selected.copy(transform = it)) },
                        onDelete = { session.remove(selected.id) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * The preview owns its own [SurfaceView] instead of borrowing the one the FFmpeg player uses (F2),
 * so entering and leaving preview never transfers surface ownership between the two players.
 */
@UnstableApi
@Composable
private fun PreviewPane(
    inputPath: String?,
    durationUs: Long,
    overlays: List<OverlaySpec>,
    active: Boolean,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { OverlayPreviewPlayer(context) }
    var surface by remember { mutableStateOf<Surface?>(null) }
    var surfaceSize by remember { mutableStateOf(Size(0, 0)) }

    AndroidView(
        factory = { viewContext ->
            SurfaceView(viewContext).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        surface = holder.surface
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        surfaceSize = Size(width, height)
                        surface = holder.surface
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        surface = null
                        player.release()
                    }
                })
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    LaunchedEffect(active, inputPath, overlays, surface, surfaceSize) {
        val target = surface
        if (!active || inputPath == null || target == null || surfaceSize.width == 0) {
            player.release()
            return@LaunchedEffect
        }
        player.show(inputPath, durationUs, overlays, target, surfaceSize)
            .onFailure {
                android.util.Log.e("OverlayDebug", "preview failed", it)
                onError(it.message ?: it::class.java.simpleName)
            }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
}

private fun newOverlay(asset: ImageAsset, existing: List<OverlaySpec>): ImageOverlay =
    ImageOverlay(
        id = "debug-${existing.size}-${System.nanoTime()}",
        range = DEFAULT_RANGE,
        source = ImageSource(asset.source.uri),
    )

private fun copyToCache(context: Context, uri: Uri): File {
    val out = File(context.cacheDir, "overlay-debug-input.mp4")
    context.contentResolver.openInputStream(uri)!!.use { input ->
        out.outputStream().use { input.copyTo(it) }
    }
    return out
}

private fun durationUsOf(path: String): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1_000
    } finally {
        retriever.release()
    }
}
