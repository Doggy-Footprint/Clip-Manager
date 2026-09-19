package com.doggy.clip_manager.core.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.ByteOrder

/**
 * Loads the fixtures produced by scripts/generate-editor-fixtures.sh into the app's cache dir so
 * VideoEditor can operate on a real filesystem path.
 */
object FixtureAssets {

    const val FIXTURE_A = "fixture_a.mp4"
    const val FIXTURE_B = "fixture_b.mkv"
    const val FIXTURE_EFFECTS = "fixture_effects.mp4"
    const val FIXTURE_EFFECTS_SILENT = "fixture_effects_silent.mp4"

    // One 1s solid-color segment per index (0..9), as produced by the fixture generator script
    // (ffmpeg/X11 color names red, orange, yellow, green, cyan, blue, purple, magenta, white, gray).
    val SEGMENT_COLORS: List<Int> = listOf(
        Color.rgb(0xFF, 0x00, 0x00),
        Color.rgb(0xFF, 0xA5, 0x00),
        Color.rgb(0xFF, 0xFF, 0x00),
        Color.rgb(0x00, 0x80, 0x00),
        Color.rgb(0x00, 0xFF, 0xFF),
        Color.rgb(0x00, 0x00, 0xFF),
        Color.rgb(0x80, 0x00, 0x80),
        Color.rgb(0xFF, 0x00, 0xFF),
        Color.rgb(0xFF, 0xFF, 0xFF),
        Color.rgb(0x80, 0x80, 0x80),
    )

    /** Copies [assetName] into a fresh, uniquely-named file under [context]'s cache dir and returns it. */
    fun copyToCache(context: Context, assetName: String, destName: String = assetName): File {
        val out = File(context.cacheDir, destName)
        context.assets.open(assetName).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }
}

/** Center-pixel color of the closest frame to [timeUs] in the file at [path]. */
fun frameColorAt(path: String, timeUs: Long): Int {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("no frame at $timeUs for $path")
        return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    } finally {
        retriever.release()
    }
}

fun framePixelAt(path: String, timeUs: Long, x: Int, y: Int): Int {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("no frame at $timeUs for $path")
        return bitmap.getPixel(x, y)
    } finally {
        retriever.release()
    }
}

/** A whole decoded frame, for assertions that must scan many pixels of one frame. */
fun frameAt(path: String, timeUs: Long): Bitmap {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("no frame at $timeUs for $path")
    } finally {
        retriever.release()
    }
}

fun frameSizeAt(path: String, timeUs: Long): Pair<Int, Int> {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("no frame at $timeUs for $path")
        return bitmap.width to bitmap.height
    } finally {
        retriever.release()
    }
}

/** Asserts [actual] is within [tolerance] per channel of [expected]. */
fun assertColorClose(expected: Int, actual: Int, tolerance: Int = 30) {
    val channels = listOf(Color::red, Color::green, Color::blue)
    for (channel in channels) {
        val diff = kotlin.math.abs(channel(expected) - channel(actual))
        if (diff > tolerance) {
            throw AssertionError(
                "color mismatch: expected=#${Integer.toHexString(expected)} actual=#${Integer.toHexString(actual)} tolerance=$tolerance",
            )
        }
    }
}

/** Overall container duration in microseconds, read independently of the app's own duration logic. */
fun containerDurationUs(path: String): Long {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        return durationMs * 1_000
    } finally {
        retriever.release()
    }
}

/** True if [path] has at least one audio track. */
fun hasAudioTrack(path: String): Boolean {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(path)
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return true
        }
        return false
    } finally {
        extractor.release()
    }
}

/** Duration in microseconds of the first audio track in [path], or null if there is none. */
fun audioTrackDurationUs(path: String): Long? {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(path)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return format.getLong(android.media.MediaFormat.KEY_DURATION)
            }
        }
        return null
    } finally {
        extractor.release()
    }
}

fun dominantAudioFrequencyHz(path: String): Double {
    val extractor = MediaExtractor()
    extractor.setDataSource(path)
    val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
        extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: error("no audio track in $path")
    val inputFormat = extractor.getTrackFormat(trackIndex)
    val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("audio track has no MIME type")
    val codec = MediaCodec.createDecoderByType(mime)
    val samples = ArrayList<Short>()
    var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var inputEnded = false
    var outputEnded = false
    try {
        extractor.selectTrack(trackIndex)
        codec.configure(inputFormat, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        while (!outputEnded) {
            if (!inputEnded) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val input = codec.getInputBuffer(inputIndex) ?: error("decoder input buffer unavailable")
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    check(codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_16BIT)
                }
                in 0..Int.MAX_VALUE -> {
                    val output = codec.getOutputBuffer(outputIndex) ?: error("decoder output buffer unavailable")
                    output.position(info.offset)
                    output.limit(info.offset + info.size)
                    val pcm = output.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                    while (pcm.hasRemaining()) samples.add(pcm.get())
                    codec.releaseOutputBuffer(outputIndex, false)
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                }
            }
        }
    } finally {
        codec.stop()
        codec.release()
        extractor.release()
    }
    val risingCrossings = samples.zipWithNext().count { (previous, next) -> previous <= 0 && next > 0 }
    return risingCrossings.toDouble() * sampleRate / (samples.size - 1)
}
