package com.doggy.clip_manager.feature.browser

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size

// Gallery originals are far larger than a grid cell; decoding them full size exhausts the heap.
internal fun loadThumbnail(resolver: ContentResolver, uri: Uri, targetPx: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return resolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    var sample = 1
    while (bounds.outWidth / sample > targetPx && bounds.outHeight / sample > targetPx) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}
