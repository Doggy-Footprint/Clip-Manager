package com.doggy.clip_manager.feature.browser

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.doggy.clip_manager.core.model.ImageAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImageGridScreen(
    images: List<ImageAsset>,
    onImageSelected: (ImageAsset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.feature_browser_images_empty))
        }
        return
    }
    val cellSize = dimensionResource(R.dimen.feature_browser_image_cell_size)
    val spacing = dimensionResource(R.dimen.feature_browser_image_cell_spacing)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = cellSize),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier.fillMaxSize(),
    ) {
        items(images, key = { it.source.uri }) { asset ->
            ImageGridCell(
                asset = asset,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { onImageSelected(asset) }
                    .padding(spacing / 2),
            )
        }
    }
}

@Composable
private fun ImageGridCell(asset: ImageAsset, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val thumbnailPx = with(LocalDensity.current) {
        dimensionResource(R.dimen.feature_browser_image_cell_size).roundToPx()
    }
    var thumbnail: Bitmap? by remember(asset.source.uri) { mutableStateOf(null) }
    LaunchedEffect(asset.source.uri, thumbnailPx) {
        thumbnail = withContext(Dispatchers.IO) {
            runCatching { loadThumbnail(resolver, Uri.parse(asset.source.uri), thumbnailPx) }.getOrNull()
        }
    }
    val bitmap = thumbnail
    if (bitmap == null) {
        Box(modifier)
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = asset.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

// Gallery originals are far larger than a grid cell; decoding them full size exhausts the heap.
private fun loadThumbnail(resolver: ContentResolver, uri: Uri, targetPx: Int): Bitmap? {
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
