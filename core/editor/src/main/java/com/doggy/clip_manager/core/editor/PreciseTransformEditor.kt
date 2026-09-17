package com.doggy.clip_manager.core.editor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Re-encodes kept ranges through Media3 Transformer, producing frame-accurate PRECISE output. */
@UnstableApi
internal object PreciseTransformEditor {

    suspend fun edit(
        context: Context,
        inputPath: String,
        segments: List<EditSegment>,
        frameLayout: FrameLayout,
        sourceWidth: Int,
        sourceHeight: Int,
        outputPath: String,
        tempDir: File,
        strategy: ConcatStrategy,
        onProgress: suspend (Float) -> Unit,
    ) {
        when (strategy) {
            ConcatStrategy.SINGLE_COMPOSITION -> {
                export(context, buildComposition(inputPath, segments, frameLayout, sourceWidth, sourceHeight), outputPath, onProgress)
            }
            ConcatStrategy.SEGMENT_CONCAT -> {
                val totalOutUs = EditPlanner.outputDurationUs(segments).coerceAtLeast(1)
                val segmentFiles = segments.indices.map { index -> File(tempDir, "segment_$index.mp4") }
                try {
                    var producedUs = 0L
                    segments.forEachIndexed { index, segment ->
                        val segmentUs = ((segment.range.endUs - segment.range.startUs) / segment.speed).toLong()
                        val base = producedUs
                        export(
                            context,
                            buildComposition(inputPath, listOf(segment), frameLayout, sourceWidth, sourceHeight),
                            segmentFiles[index].path,
                        ) { fraction ->
                            onProgress(((base + fraction * segmentUs) / totalOutUs.toFloat()).coerceIn(0f, 1f))
                        }
                        producedUs += segmentUs
                    }
                    export(context, buildPassthroughComposition(segmentFiles), outputPath, onProgress)
                } finally {
                    segmentFiles.forEach { it.delete() }
                }
            }
        }
    }

    private fun buildComposition(
        inputPath: String,
        segments: List<EditSegment>,
        frameLayout: FrameLayout,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Composition {
        val items = segments.map { segment ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(inputPath)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(segment.range.startUs)
                        .setEndPositionUs(segment.range.endUs)
                        .build(),
                )
                .build()
            val videoEffects = buildVideoEffects(frameLayout, sourceWidth, sourceHeight, segment)
            val builder = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), videoEffects))
            if (segment.speed != 1f) {
                builder.setSpeed(
                    androidx.media3.common.SpeedParameters(
                        object : androidx.media3.common.audio.SpeedProvider {
                            override fun getSpeed(timeUs: Long): Float = segment.speed

                            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
                        },
                        true,
                    ),
                )
            }
            builder.build()
        }
        val sequence = EditedMediaItemSequence.Builder(items).build()
        return Composition.Builder(listOf(sequence)).build()
    }

    private fun buildVideoEffects(
        frameLayout: FrameLayout,
        sourceWidth: Int,
        sourceHeight: Int,
        segment: EditSegment,
    ): List<androidx.media3.common.Effect> {
        val effects = mutableListOf<androidx.media3.common.Effect>()
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
        effects += ScaleAndRotateTransformation.Builder()
            .setScale(if (segment.horizontalFlip) -1f else 1f, if (segment.verticalFlip) -1f else 1f)
            .build()
        return effects
    }

    private fun buildPassthroughComposition(segmentFiles: List<File>): Composition {
        val items = segmentFiles.map { file -> EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file))).build() }
        val sequence = EditedMediaItemSequence.Builder(items).build()
        return Composition.Builder(listOf(sequence)).build()
    }

    private suspend fun export(
        context: Context,
        composition: Composition,
        outputPath: String,
        onProgress: suspend (Float) -> Unit,
    ) = coroutineScope {
        val result = CompletableDeferred<Unit>()
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                result.complete(Unit)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                result.completeExceptionally(EncodingFailedException(exportException))
            }
        }
        val transformer = withContext(Dispatchers.Main.immediate) {
            Transformer.Builder(context).addListener(listener).build().also {
                it.start(composition, outputPath)
            }
        }

        val pollJob = launch {
            val progressHolder = ProgressHolder()
            while (isActive) {
                withContext(Dispatchers.Main.immediate) { transformer.getProgress(progressHolder) }
                onProgress((progressHolder.progress.coerceAtLeast(0) / 100f).coerceIn(0f, 1f))
                delay(150)
            }
        }
        try {
            result.await()
        } finally {
            pollJob.cancel()
            withContext(Dispatchers.Main.immediate + kotlinx.coroutines.NonCancellable) {
                transformer.cancel()
            }
        }
    }
}
