package com.doggy.clip_manager.core.editor

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

internal data class ProbeResult(
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val videoTrackIndex: Int,
    val videoFormat: MediaFormat,
    val audioTrackIndex: Int?,
    val audioFormat: MediaFormat?,
)

/** Formats MediaMuxer(MPEG_4) can stream-copy without decoding. */
internal val MUXER_COPYABLE_VIDEO_MIME = setOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)
internal val MUXER_COPYABLE_AUDIO_MIME = setOf(MediaFormat.MIMETYPE_AUDIO_AAC)

internal object MediaProbe {

    fun probe(path: String): ProbeResult {
        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            throw InputNotReadableException("cannot read input: $path")
        }
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(path)
            } catch (e: Exception) {
                throw InputNotReadableException("cannot open input: $path", e)
            }

            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            var audioTrack: Int? = null
            var audioFormat: MediaFormat? = null
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
                if (mime.startsWith("video/") && videoTrack == -1) {
                    videoTrack = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrack == null) {
                    audioTrack = i
                    audioFormat = format
                }
            }
            if (videoTrack == -1 || videoFormat == null) {
                throw InputNotReadableException("no video track: $path")
            }
            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            return ProbeResult(durationUs, width, height, videoTrack, videoFormat, audioTrack, audioFormat)
        } finally {
            extractor.release()
        }
    }

    fun videoKeyframesUs(path: String, videoTrackIndex: Int): List<Long> {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        try {
            extractor.selectTrack(videoTrackIndex)
            val keyframes = mutableListOf<Long>()
            do {
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    keyframes.add(extractor.sampleTime)
                }
            } while (extractor.advance())
            return keyframes
        } finally {
            extractor.release()
        }
    }
}
