package com.doggy.clip_manager.feature.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.view.Surface
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItemSequence
import com.doggy.clip_manager.core.editor.EditSpec
import com.doggy.clip_manager.core.editor.EditedMediaItemFactory
import java.io.File

/**
 * Plays the same [EditSpec] export uses: cut, aspect, flip, speed and overlays all come from
 * [EditedMediaItemFactory] (`buildPreview`), the core/editor code that also computes crop/flip/
 * speed for [PreciseTransformEditor]'s export, so the preview cannot diverge from the exported
 * file (F9/Q4). The item shape differs from export's, though: `buildPreview` emits one
 * [androidx.media3.transformer.EditedMediaItem] per keep range with time-varying flip/speed
 * effects, instead of export's one item per cut/flip/speed segment, because `CompositionPlayer`
 * (media3 1.11.1) freezes video after the first item once a sequence holds several items with
 * differing effects.
 */
@UnstableApi
class OverlayPreviewPlayer(private val context: Context) {
    private var player: CompositionPlayer? = null

    fun show(spec: EditSpec, durationUs: Long, surface: Surface, size: Size): Result<Unit> =
        runCatching {
            val input = File(spec.inputPath)
            require(input.isFile) { "input does not exist: ${spec.inputPath}" }
            val (sourceWidth, sourceHeight) = probeSize(spec.inputPath)
            val items = EditedMediaItemFactory.buildPreview(
                context = context,
                inputPath = spec.inputPath,
                durationUs = durationUs,
                keepRanges = spec.keepRanges,
                effects = spec.effects,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
            )
            release()
            val composition = Composition.Builder(listOf(EditedMediaItemSequence.Builder(items).build())).build()
            CompositionPlayer.Builder(context).build().also {
                player = it
                it.setVideoSurface(surface, size)
                it.setComposition(composition)
                it.prepare()
                it.play()
            }
            Unit
        }

    fun release() {
        player?.release()
        player = null
    }

    private fun probeSize(inputPath: String): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(inputPath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            return width to height
        } finally {
            retriever.release()
        }
    }
}
