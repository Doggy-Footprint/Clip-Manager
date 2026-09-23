package com.doggy.clip_manager.core.editor

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import java.io.File

/**
 * Builds the Media3 [EditedMediaItem]s an edit needs. Export ([PreciseTransformEditor]) and
 * preview (`OverlayPreviewPlayer`) must not disagree on what Crop/Presentation/ScaleAndRotate/
 * speed an [EditEffects] produces, so both call this instead of each building their own (Q4).
 */
@UnstableApi
object EditedMediaItemFactory {

    /** Plans [effects] against [durationUs]/[keepRanges] itself so callers only need an [EditSpec]-shaped input. */
    fun build(
        context: Context,
        inputPath: String,
        durationUs: Long,
        keepRanges: List<TimeRange>,
        effects: EditEffects,
        sourceWidth: Int,
        sourceHeight: Int,
        outputOffsetStartUs: Long = 0L,
    ): List<EditedMediaItem> {
        val segments = EditPlanner.plan(durationUs, keepRanges, effects).segments
        return buildItems(
            context,
            inputPath,
            segments,
            effects.frameLayout,
            sourceWidth,
            sourceHeight,
            effects.overlays,
            outputOffsetStartUs,
            sourceDurationUs = durationUs,
        )
    }

    /**
     * [outputOffsetStartUs] is where [segments] begin on the output timeline. Overlay ranges are
     * expressed on that timeline, so segment-by-segment export must say where it sits;
     * single-composition callers start at zero.
     *
     * [sourceDurationUs], when given, is set as [EditedMediaItem.durationUs]: Media3 requires that
     * to be the *unclipped* source media duration (clip end/start are validated against it), not
     * the segment's own (post-cut, post-speed) output length. `CompositionPlayer.setComposition`
     * throws otherwise once a clip's end exceeds its own output duration (e.g. any keep range that
     * a speed/flip boundary splits into >1 segment). Export never needed this (Transformer derives
     * duration itself), so it stays null there and behaviour is unchanged.
     */
    internal fun buildItems(
        context: Context,
        inputPath: String,
        segments: List<EditSegment>,
        frameLayout: FrameLayout,
        sourceWidth: Int,
        sourceHeight: Int,
        overlays: List<OverlaySpec>,
        outputOffsetStartUs: Long,
        sourceDurationUs: Long? = null,
    ): List<EditedMediaItem> {
        val overlayFactory = OverlayCompositionFactory(context)
        var outputOffsetUs = outputOffsetStartUs
        return segments.map { segment ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(inputPath)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(segment.range.startUs)
                        .setEndPositionUs(segment.range.endUs)
                        .build(),
                )
                .build()
            val segmentOutUs = ((segment.range.endUs - segment.range.startUs) / segment.speed).toLong()
            val segmentStartUs = outputOffsetUs
            val videoEffects = buildVideoEffects(frameLayout, sourceWidth, sourceHeight, segment) +
                overlayFactory.create(
                    overlays.mapNotNull { overlay ->
                        val start = maxOf(0L, overlay.range.startUs - segmentStartUs)
                        val end = minOf(segmentOutUs, overlay.range.endUs - segmentStartUs)
                        if (start < end) overlay.withRange(TimeRange(start, end)) else null
                    },
                )
            val builder = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), videoEffects))
            if (sourceDurationUs != null) {
                builder.setDurationUs(sourceDurationUs)
            }
            if (segment.speed != 1f) {
                builder.setSpeed(
                    SpeedParameters(
                        object : SpeedProvider {
                            override fun getSpeed(timeUs: Long): Float = segment.speed

                            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                        },
                        true,
                    ),
                )
            }
            outputOffsetUs += segmentOutUs
            builder.build()
        }
    }

    private fun buildVideoEffects(
        frameLayout: FrameLayout,
        sourceWidth: Int,
        sourceHeight: Int,
        segment: EditSegment,
    ): List<Effect> {
        val effects = buildFrameLayoutEffects(frameLayout, sourceWidth, sourceHeight).toMutableList()
        effects += ScaleAndRotateTransformation.Builder()
            .setScale(if (segment.horizontalFlip) -1f else 1f, if (segment.verticalFlip) -1f else 1f)
            .build()
        return effects
    }

    /** Crop/Presentation part of [buildVideoEffects], reused as-is by the preview item (Q4). */
    private fun buildFrameLayoutEffects(
        frameLayout: FrameLayout,
        sourceWidth: Int,
        sourceHeight: Int,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        when (frameLayout) {
            FrameLayout.Original -> Unit
            is FrameLayout.Ratio -> {
                val output = EditPlanner.outputSize(sourceWidth, sourceHeight, frameLayout)
                when (frameLayout.mode) {
                    FrameMode.CROP -> {
                        val sourceAspect = sourceWidth.toFloat() / sourceHeight
                        val targetAspect = output.width.toFloat() / output.height
                        val cropWidth = minOf(1f, targetAspect / sourceAspect)
                        val cropHeight = minOf(1f, sourceAspect / targetAspect)
                        val centerX = frameLayout.cropCenter.x.coerceIn(cropWidth / 2f, 1f - cropWidth / 2f)
                        val centerY = frameLayout.cropCenter.y.coerceIn(cropHeight / 2f, 1f - cropHeight / 2f)
                        effects += Crop(
                            2f * (centerX - cropWidth / 2f) - 1f,
                            2f * (centerX + cropWidth / 2f) - 1f,
                            2f * (centerY - cropHeight / 2f) - 1f,
                            2f * (centerY + cropHeight / 2f) - 1f,
                        )
                        effects += Presentation.createForWidthAndHeight(
                            output.width, output.height, Presentation.LAYOUT_STRETCH_TO_FIT,
                        )
                    }
                    FrameMode.STRETCH -> effects += Presentation.createForWidthAndHeight(
                        output.width, output.height, Presentation.LAYOUT_STRETCH_TO_FIT,
                    )
                    FrameMode.FIT -> effects += Presentation.createForWidthAndHeight(
                        output.width, output.height, Presentation.LAYOUT_SCALE_TO_FIT,
                    )
                }
            }
        }
        return effects
    }

    /**
     * Preview-only builder (F9/ID2): one [EditedMediaItem] per keep range (in practice always 1,
     * since [editorExportSpec] passes a single-element keep list) instead of one per
     * [EditPlanner.plan] segment. media3 1.11.1's `CompositionPlayer` never re-enables its video
     * renderer after the first item's period ends when a sequence holds several items with
     * differing effects, freezing the picture from the second item on; export is unaffected since
     * `Transformer` (via [buildItems]) does not hit that renderer path. Flip and speed inside the
     * keep range are instead handled by time-varying effects on that single item.
     */
    fun buildPreview(
        context: Context,
        inputPath: String,
        durationUs: Long,
        keepRanges: List<TimeRange>,
        effects: EditEffects,
        sourceWidth: Int,
        sourceHeight: Int,
    ): List<EditedMediaItem> {
        EditPlanner.validateEffects(effects, durationUs)
        val kept = EditPlanner.normalizeKeepRanges(durationUs, keepRanges)
        val overlayFactory = OverlayCompositionFactory(context)
        return kept.map { keepRange ->
            buildPreviewItem(inputPath, durationUs, keepRange, effects, sourceWidth, sourceHeight, overlayFactory)
        }
    }

    private fun buildPreviewItem(
        inputPath: String,
        sourceDurationUs: Long,
        keepRange: TimeRange,
        effects: EditEffects,
        sourceWidth: Int,
        sourceHeight: Int,
        overlayFactory: OverlayCompositionFactory,
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(inputPath)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(keepRange.startUs)
                    .setEndPositionUs(keepRange.endUs)
                    .build(),
            )
            .build()

        val speedSteps = EditPlanner.speedSegments(keepRange, effects.speeds)
        val hasSpeedChange = speedSteps.any { (_, speed) -> speed != 1f }

        val videoEffects = mutableListOf<Effect>()
        val audioProcessors = mutableListOf<AudioProcessor>()
        if (hasSpeedChange) {
            // CompositionPlayer (media3 1.11.1) rejects a composition where a speed-changing
            // effect (from createExperimentalSpeedChangingEffect) isn't the *first* video effect
            // (IllegalArgumentException, verified empirically), so it goes first regardless of
            // what would otherwise be the natural crop/flip/overlay order.
            val speedProvider = KeepRangeSpeedProvider(keepRange, speedSteps)
            val speedChange = Effects.createExperimentalSpeedChangingEffect(speedProvider)
            audioProcessors += speedChange.first
            videoEffects += speedChange.second
        }
        videoEffects += buildFrameLayoutEffects(effects.frameLayout, sourceWidth, sourceHeight)
        // Because the speed effect (if any) precedes it, this only ever sees keep-relative output
        // (post-speed) time, so it maps back to source time itself (EditPlanner.sourceTimeForOutput)
        // instead of the simpler `presentationTimeUs + keepRange.startUs` a pre-speed position would
        // allow, to decide which source-timeline flip range presentationTimeUs falls in.
        videoEffects += TimeBasedFlip(keepRange, effects.flips, effects.speeds)
        // Overlay ranges are already on the output timeline (`toOutputOverlays`, F7) and this is
        // the only item on that timeline (single keep range starts at output 0), so they need no
        // further offsetting here; they were already relying on post-speed time (the speed effect
        // came first even before this reordering), so this is unaffected by it.
        videoEffects += overlayFactory.create(effects.overlays)

        return EditedMediaItem.Builder(mediaItem)
            .setDurationUs(sourceDurationUs)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
    }
}

/** Time-varying replacement for [ScaleAndRotateTransformation] (F9): flip decisions follow source time, not a constant. */
@UnstableApi
private class TimeBasedFlip(
    private val keepRange: TimeRange,
    private val flips: List<FlipRange>,
    private val speeds: List<SpeedRange>,
) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val sourceTimeUs = EditPlanner.sourceTimeForOutput(keepRange, speeds, presentationTimeUs)
        val (horizontal, vertical) = EditPlanner.flipAt(flips, sourceTimeUs)
        return Matrix().apply {
            setScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f)
        }
    }
}

/**
 * [EditPlanner.speedSegments] steps, remapped from source time to the clip-relative time (starting
 * at 0) `createExperimentalSpeedChangingEffect`'s `SpeedProvider` receives for this keep range.
 */
@UnstableApi
private class KeepRangeSpeedProvider(
    keepRange: TimeRange,
    steps: List<Pair<TimeRange, Float>>,
) : SpeedProvider {
    private val clipRelativeSteps = steps.map { (range, speed) -> (range.startUs - keepRange.startUs) to speed }

    override fun getSpeed(timeUs: Long): Float =
        clipRelativeSteps.lastOrNull { (start, _) -> start <= timeUs }?.second ?: 1f

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long =
        clipRelativeSteps.firstOrNull { (start, _) -> start > timeUs }?.first ?: C.TIME_UNSET
}
