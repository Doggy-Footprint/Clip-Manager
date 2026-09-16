package com.doggy.clip_manager.core.editor

import android.content.Context
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Loads the fixtures produced by scripts/generate-editor-fixtures.sh into the app's cache dir so
 * VideoEditor can operate on a real filesystem path.
 */
object FixtureAssets {

    const val FIXTURE_A = "fixture_a.mp4"
    const val FIXTURE_B = "fixture_b.mkv"

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
