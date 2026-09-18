package com.doggy.clip_manager.feature.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.doggy.clip_manager.core.editor.EditPlanner
import com.doggy.clip_manager.core.editor.OverlayCompositionFactory
import com.doggy.clip_manager.core.editor.OverlaySpec
import java.io.File

/**
 * Renders Phase 3 overlays over the untouched source so the user can place them. Cut, aspect,
 * flip and speed are not applied here, so preview duration equals the input duration.
 */
@UnstableApi
class OverlayPreviewPlayer(private val context: Context) {
    private var player: CompositionPlayer? = null

    fun show(inputPath: String, durationUs: Long, overlays: List<OverlaySpec>, surface: Surface, size: Size): Result<Unit> =
        runCatching {
            val input = File(inputPath)
            require(input.isFile) { "input does not exist: $inputPath" }
            // Same clipping the export path applies, so a placed overlay shows the same span here.
            val normalized = EditPlanner.normalizeOutputOverlays(durationUs, overlays)
            val videoEffects: List<Effect> = OverlayCompositionFactory(context).create(normalized)
            release()
            val composition = Composition.Builder(
                listOf(
                    EditedMediaItemSequence.Builder(
                        EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                            .setEffects(Effects(emptyList(), videoEffects))
                            .build(),
                    ).build(),
                ),
            ).build()
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
}
