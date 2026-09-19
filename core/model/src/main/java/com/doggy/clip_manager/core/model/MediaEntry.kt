package com.doggy.clip_manager.core.model

enum class MediaKind { VIDEO, AUDIO, IMAGE }

/**
 * [uri] is an unparsed content URI string for the same reason as [ImageSource].
 *
 * [filePath] is the on-disk absolute path: the FFmpeg-based player opens paths, not content URIs,
 * so an entry without one cannot be played. Sources that have no filesystem backing leave it null.
 */
data class MediaEntry(
    val uri: String,
    val filePath: String?,
    val displayName: String,
    val kind: MediaKind,
    val bucketName: String?,
    val dateModifiedSeconds: Long,
    val durationMs: Long?,
)

data class MediaQuery(val kinds: Set<MediaKind>)
