package com.doggy.clip_manager.core.editor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
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
        overlays: List<OverlaySpec>,
        outputPath: String,
        tempDir: File,
        strategy: ConcatStrategy,
        onProgress: suspend (Float) -> Unit,
    ) {
        when (strategy) {
            ConcatStrategy.SINGLE_COMPOSITION -> {
                export(
                    context,
                    buildComposition(context, inputPath, segments, frameLayout, sourceWidth, sourceHeight, overlays, 0L),
                    outputPath,
                    onProgress,
                )
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
                            buildComposition(context, inputPath, listOf(segment), frameLayout, sourceWidth, sourceHeight, overlays, base),
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

    /**
     * [outputOffsetStartUs] is where [segments] begin on the output timeline. Overlay ranges are
     * expressed on that timeline, so SEGMENT_CONCAT exports one segment at a time and must say
     * where it sits; SINGLE_COMPOSITION starts at zero.
     */
    private fun buildComposition(
        context: Context,
        inputPath: String,
        segments: List<EditSegment>,
        frameLayout: FrameLayout,
        sourceWidth: Int,
        sourceHeight: Int,
        overlays: List<OverlaySpec>,
        outputOffsetStartUs: Long,
    ): Composition {
        val items = EditedMediaItemFactory.buildItems(
            context, inputPath, segments, frameLayout, sourceWidth, sourceHeight, overlays, outputOffsetStartUs,
        )
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
