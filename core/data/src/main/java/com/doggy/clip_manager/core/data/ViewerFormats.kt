package com.doggy.clip_manager.core.data

/** Shared with feature.mainlayer's finer-grained classifier; kept minimal so both file/media browse lists know which rows open a viewer (R6). */
object ViewerFormats {
    private val VIEWABLE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp",
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus",
        "pdf", "txt", "cbz",
    )

    fun isViewable(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return FileListing.isVideo(name) || ext in VIEWABLE_EXTENSIONS
    }
}
