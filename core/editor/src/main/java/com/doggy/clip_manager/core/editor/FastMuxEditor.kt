package com.doggy.clip_manager.core.editor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Copies samples between MediaExtractor and MediaMuxer without decoding/encoding, so bytes for
 * kept ranges are identical to the source (I3/I4's "lossless copy").
 */
internal object FastMuxEditor {

    suspend fun edit(
        inputPath: String,
        keepRanges: List<TimeRange>,
        outputPath: String,
        tempDir: File,
        strategy: ConcatStrategy,
        onProgress: suspend (Float) -> Unit,
    ) {
        val probeResult = MediaProbe.probe(inputPath)
        val audioMime = probeResult.audioFormat?.getString(MediaFormat.KEY_MIME)
        if (audioMime != null && audioMime !in MUXER_COPYABLE_AUDIO_MIME) {
            throw UnsupportedStreamCopyException("unsupported audio codec for stream copy: $audioMime")
        }
        val videoMime = probeResult.videoFormat.getString(MediaFormat.KEY_MIME)
        if (videoMime !in MUXER_COPYABLE_VIDEO_MIME) {
            throw UnsupportedStreamCopyException("unsupported video codec for stream copy: $videoMime")
        }

        val totalOutUs = EditPlanner.outputDurationUs(keepRanges).coerceAtLeast(1)

        when (strategy) {
            ConcatStrategy.SINGLE_COMPOSITION -> {
                muxRanges(inputPath, keepRanges, outputPath, totalOutUs, 0L, onProgress)
            }
            ConcatStrategy.SEGMENT_CONCAT -> {
                val segmentFiles = keepRanges.indices.map { index -> File(tempDir, "segment_$index.mp4") }
                try {
                    var producedUs = 0L
                    keepRanges.forEachIndexed { index, range ->
                        // Each segment file is muxed standalone and must start at pts 0; only the
                        // progress fraction reported to the caller uses the running total.
                        muxRanges(inputPath, listOf(range), segmentFiles[index].path, totalOutUs, producedUs, onProgress)
                        producedUs += range.endUs - range.startUs
                    }
                    concatSegments(inputPath, segmentFiles, keepRanges, outputPath)
                    currentCoroutineContext().ensureActive()
                } finally {
                    segmentFiles.forEach { it.delete() }
                }
            }
        }
    }

    private suspend fun muxRanges(
        inputPath: String,
        ranges: List<TimeRange>,
        outputPath: String,
        totalOutUs: Long,
        progressBaseUs: Long,
        onProgress: suspend (Float) -> Unit,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)
        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            val muxerTrackForSourceIndex = HashMap<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    muxerTrackForSourceIndex[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                }
            }
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            // pts written into this muxer session always starts at 0, regardless of where this
            // range sits in the overall job; progressBaseUs only affects the reported fraction so
            // that a standalone segment file (SEGMENT_CONCAT) is self-contained.
            var ptsBaseUs = 0L

            for (range in ranges) {
                extractor.seekTo(range.startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0 || sampleTime >= range.endUs) break
                    val muxerTrack = muxerTrackForSourceIndex[extractor.sampleTrackIndex]
                    // Seeking to the video sync sample leaves audio slightly before range start; a
                    // negative pts makes MPEG4Writer abandon a standalone segment file entirely.
                    if (muxerTrack == null || sampleTime < range.startUs) {
                        if (!extractor.advance()) break
                        continue
                    }
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    bufferInfo.set(0, size, ptsBaseUs + (sampleTime - range.startUs), toBufferFlags(extractor.sampleFlags))
                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    if (!extractor.advance()) break
                }
                ptsBaseUs += range.endUs - range.startUs
                onProgress(((progressBaseUs + ptsBaseUs).toFloat() / totalOutUs).coerceIn(0f, 1f))
            }
        } finally {
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                    // stop() throws if no samples were written (e.g. an empty range); output is discarded by caller on failure.
                }
            }
            muxer.release()
            extractor.release()
        }
    }

    private fun concatSegments(inputPath: String, segmentFiles: List<File>, ranges: List<TimeRange>, outputPath: String) {
        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            // Track formats/slots come from the original source, not a segment file: a segment's
            // own re-muxed format can carry that segment's own (much shorter) duration metadata,
            // which must not leak into the concatenated output's track configuration.
            val sourceExtractor = MediaExtractor()
            sourceExtractor.setDataSource(inputPath)
            var videoMuxerTrack: Int? = null
            var audioMuxerTrack: Int? = null
            for (i in 0 until sourceExtractor.trackCount) {
                val format = sourceExtractor.getTrackFormat(i)
                when (format.getString(MediaFormat.KEY_MIME)?.substringBefore('/')) {
                    "video" -> if (videoMuxerTrack == null) videoMuxerTrack = muxer.addTrack(format)
                    "audio" -> if (audioMuxerTrack == null) audioMuxerTrack = muxer.addTrack(format)
                }
            }
            sourceExtractor.release()
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var offsetUs = 0L

            segmentFiles.forEachIndexed { index, segmentFile ->
                val extractor = MediaExtractor()
                extractor.setDataSource(segmentFile.path)
                // Map by mime type, not raw track index: a segment file's own track numbering is
                // not guaranteed to line up with the original source's or the output muxer's.
                val muxerTrackForSourceTrack = HashMap<Int, Int>()
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val muxerTrack = when (format.getString(MediaFormat.KEY_MIME)?.substringBefore('/')) {
                        "video" -> videoMuxerTrack
                        "audio" -> audioMuxerTrack
                        else -> null
                    }
                    if (muxerTrack != null) {
                        muxerTrackForSourceTrack[i] = muxerTrack
                        extractor.selectTrack(i)
                    }
                }
                // Normalize against each track's own first sample instead of trusting it to read
                // back as exactly 0: a re-muxed standalone file is not guaranteed to round-trip
                // the written presentationTimeUs values as-is.
                val trackStartUs = HashMap<Int, Long>()
                while (true) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0) break
                    val sourceTrack = extractor.sampleTrackIndex
                    val muxerTrack = muxerTrackForSourceTrack[sourceTrack]
                    if (muxerTrack == null) {
                        if (!extractor.advance()) break
                        continue
                    }
                    val startUs = trackStartUs.getOrPut(sourceTrack) { sampleTime }
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    bufferInfo.set(0, size, offsetUs + (sampleTime - startUs), toBufferFlags(extractor.sampleFlags))
                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    if (!extractor.advance()) break
                }
                extractor.release()
                offsetUs += ranges[index].endUs - ranges[index].startUs
            }
        } finally {
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            muxer.release()
        }
    }

    private fun toBufferFlags(sampleFlags: Int): Int =
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
}
