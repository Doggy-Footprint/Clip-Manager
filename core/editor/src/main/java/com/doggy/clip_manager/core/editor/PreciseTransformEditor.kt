package com.doggy.clip_manager.core.editor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
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
        keepRanges: List<TimeRange>,
        outputPath: String,
        tempDir: File,
        strategy: ConcatStrategy,
        onProgress: suspend (Float) -> Unit,
    ) {
        when (strategy) {
            ConcatStrategy.SINGLE_COMPOSITION -> {
                export(context, buildComposition(inputPath, keepRanges), outputPath, onProgress)
            }
            ConcatStrategy.SEGMENT_CONCAT -> {
                val totalOutUs = EditPlanner.outputDurationUs(keepRanges).coerceAtLeast(1)
                val segmentFiles = keepRanges.indices.map { index -> File(tempDir, "segment_$index.mp4") }
                try {
                    var producedUs = 0L
                    keepRanges.forEachIndexed { index, range ->
                        val rangeUs = range.endUs - range.startUs
                        val base = producedUs
                        export(
                            context,
                            buildComposition(inputPath, listOf(range)),
                            segmentFiles[index].path,
                        ) { fraction ->
                            onProgress(((base + fraction * rangeUs) / totalOutUs.toFloat()).coerceIn(0f, 1f))
                        }
                        producedUs += rangeUs
                    }
                    export(context, buildPassthroughComposition(segmentFiles), outputPath, onProgress)
                } finally {
                    segmentFiles.forEach { it.delete() }
                }
            }
        }
    }

    private fun buildComposition(inputPath: String, ranges: List<TimeRange>): Composition {
        val items = ranges.map { range ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(inputPath)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(range.startUs)
                        .setEndPositionUs(range.endUs)
                        .build(),
                )
                .build()
            // A no-op video effect forces Transformer to re-encode instead of stream-copying,
            // which is required for PRECISE's frame-accurate trimming.
            EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), listOf(ScaleAndRotateTransformation.Builder().build())))
                .build()
        }
        val sequence = EditedMediaItemSequence.Builder(items).build()
        return Composition.Builder(listOf(sequence)).build()
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
