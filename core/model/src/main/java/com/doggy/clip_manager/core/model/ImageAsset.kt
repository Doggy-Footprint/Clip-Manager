package com.doggy.clip_manager.core.model

/**
 * [uri] is an unparsed content URI string: this module is a pure JVM library and cannot
 * reference `android.net.Uri`.
 */
data class ImageSource(val uri: String)

data class ImageAsset(val source: ImageSource, val displayName: String)
