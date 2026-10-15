package com.doggy.clip_manager.feature.mainlayer

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class CbzTapZone { Center, Left, Right, PreviewStrip }

internal fun cbzTapZone(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    stripHeightPx: Float,
    bottomInsetPx: Float,
): CbzTapZone {
    val stripBottom = height - bottomInsetPx
    if (y >= stripBottom - stripHeightPx && y < stripBottom) return CbzTapZone.PreviewStrip
    val half = min(width, height) / 6f
    if (x >= width / 2f - half && x <= width / 2f + half && y >= height / 2f - half && y <= height / 2f + half) {
        return CbzTapZone.Center
    }
    return if (x < width / 2f) CbzTapZone.Left else CbzTapZone.Right
}

@Composable
internal fun CbzReader(
    title: String,
    pageCount: Int,
    loadPage: suspend (index: Int, maxSidePx: Int?) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    var uiVisible by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val stripHeightDp = 24.dp
    val stripHeightPx = with(density) { stripHeightDp.toPx() }
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    val bottomInsetPx = max(
        WindowInsets.systemGestures.getBottom(density),
        WindowInsets.navigationBars.getBottom(density),
    ).toFloat()
    val bottomInsetDp = with(density) { bottomInsetPx.toDp() }

    fun next() { if (pageIndex < pageCount - 1) pageIndex++ }
    fun prev() { if (pageIndex > 0) pageIndex-- }

    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex) {
        bitmap = runCatching { loadPage(pageIndex, null) }.getOrNull()
    }

    Box(
        modifier
            .fillMaxSize()
            .testTag("cbz_reader")
            .pointerInput(stripHeightPx, bottomInsetPx, swipeThresholdPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var consumed = down.isConsumed
                    var last = down.position
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            consumed = true
                            break
                        }
                        last = change.position
                        consumed = consumed || change.isConsumed
                        if (!change.pressed) break
                    }
                    if (consumed) return@awaitEachGesture
                    val delta = last - down.position
                    if (delta.getDistance() <= viewConfiguration.touchSlop) {
                        if (previewOpen) {
                            previewOpen = false
                        } else {
                            when (
                                cbzTapZone(
                                    down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat(),
                                    stripHeightPx, bottomInsetPx,
                                )
                            ) {
                                CbzTapZone.Center -> uiVisible = !uiVisible
                                CbzTapZone.Left -> prev()
                                CbzTapZone.Right -> next()
                                CbzTapZone.PreviewStrip -> previewOpen = true
                            }
                        }
                    } else if (!previewOpen) {
                        if (delta.x <= -swipeThresholdPx) next()
                        else if (delta.x >= swipeThresholdPx) prev()
                    }
                }
            },
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }

        if (uiVisible) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(48.dp)
                    .testTag("cbz_top_bar"),
            ) {
                Text(
                    title,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 88.dp, end = 16.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInsetDp)
                    .fillMaxWidth()
                    .height(stripHeightDp)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .testTag("cbz_preview_strip_shade"),
            )
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .background(Color.Gray, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag("cbz_page_badge"),
        ) {
            Text("${pageIndex + 1}/$pageCount", color = Color.White)
        }

        if (previewOpen) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = pageIndex)
            val thumbMaxPx = with(density) { 96.dp.roundToPx() }
            LazyRow(
                state = listState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInsetDp)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .testTag("cbz_preview"),
            ) {
                items(pageCount) { i ->
                    var thumb by remember(i) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(i) {
                        thumb = runCatching { loadPage(i, thumbMaxPx) }.getOrNull()
                    }
                    Box(
                        Modifier
                            .height(96.dp)
                            .widthIn(min = 48.dp)
                            .clickable {
                                pageIndex = i
                                previewOpen = false
                            }
                            .testTag("cbz_thumb_$i"),
                    ) {
                        thumb?.let {
                            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.height(96.dp))
                        }
                    }
                }
            }
        }
    }
}
