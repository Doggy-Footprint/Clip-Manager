package com.doggy.clip_manager.feature.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.doggy.clip_manager.core.designsystem.component.EmptyState
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImageViewerPane(uri: String?, modifier: Modifier = Modifier) {
    if (uri == null) {
        EmptyState(
            icon = ClipIcons.Image,
            title = stringResource(R.string.feature_player_image_empty_title),
            modifier = modifier,
        )
        return
    }
    val resolver = LocalContext.current.contentResolver
    var bitmap: Bitmap? by remember(uri) { mutableStateOf(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                // Viewer-sized decode: full-resolution originals can exhaust the heap.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > MAX_VIEWER_PX || bounds.outHeight / sample > MAX_VIEWER_PX) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    val image = bitmap
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val MAX_VIEWER_PX = 2048
