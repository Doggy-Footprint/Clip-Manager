package com.doggy.clip_manager.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.core.content.res.ResourcesCompat
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.OverlayTransform
import kotlin.math.atan2
import kotlin.math.hypot

private enum class Handle { MOVE, SCALE, ROTATE, ALPHA }

private class OverlayGeometry(
    private val transform: OverlayTransform,
    private val canvas: Size,
    private val metrics: OverlayMetrics,
) {
    val center = Offset(transform.positionX * canvas.width, transform.positionY * canvas.height)
    val half = minOf(canvas.width, canvas.height) * metrics.halfExtentRatio * transform.scale
    val topLeft = Offset(center.x - half, center.y - half)
    val delete = Offset(center.x + half, center.y - half)
    val scaleHandle = Offset(center.x - half, center.y + half)
    val rotateHandle = Offset(center.x + half, center.y + half)
    val alphaBarY = center.y + half + metrics.alphaBarOffset
    val alphaBarWidth = half * 2

    fun handleAt(point: Offset): Handle? = when {
        point.distanceTo(scaleHandle) <= metrics.touchRadius -> Handle.SCALE
        point.distanceTo(rotateHandle) <= metrics.touchRadius -> Handle.ROTATE
        point.x in scaleHandle.x..scaleHandle.x + alphaBarWidth &&
            point.y in alphaBarY - metrics.touchRadius..alphaBarY + metrics.touchRadius -> Handle.ALPHA
        point.x in topLeft.x..topLeft.x + half * 2 && point.y in topLeft.y..topLeft.y + half * 2 -> Handle.MOVE
        else -> null
    }

    fun isDelete(point: Offset): Boolean = point.distanceTo(delete) <= metrics.handleRadius
}

private class OverlayMetrics(
    val halfExtentRatio: Float,
    val handleRadius: Float,
    val touchRadius: Float,
    val glyph: Float,
    val stroke: Float,
    val alphaBarOffset: Float,
    val alphaBarHeight: Float,
    val alphaThumbRadius: Float,
)

@Composable
fun ImageOverlayEditor(
    overlay: ImageOverlay,
    onTransformChange: (OverlayTransform) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val halfExtentRatio = ResourcesCompat.getFloat(
        LocalContext.current.resources,
        R.dimen.feature_editor_overlay_half_extent_ratio,
    )
    val handleRadius = dimensionResource(R.dimen.feature_editor_overlay_handle_radius)
    val touchRadius = dimensionResource(R.dimen.feature_editor_overlay_handle_touch_radius)
    val glyph = dimensionResource(R.dimen.feature_editor_overlay_handle_glyph)
    val stroke = dimensionResource(R.dimen.feature_editor_overlay_stroke)
    val alphaBarOffset = dimensionResource(R.dimen.feature_editor_overlay_alpha_bar_offset)
    val alphaBarHeight = dimensionResource(R.dimen.feature_editor_overlay_alpha_bar_height)
    val alphaThumbRadius = dimensionResource(R.dimen.feature_editor_overlay_alpha_thumb_radius)
    val metrics = remember(density, halfExtentRatio, handleRadius, touchRadius, glyph, stroke, alphaBarOffset, alphaBarHeight, alphaThumbRadius) {
        with(density) {
            OverlayMetrics(
                halfExtentRatio = halfExtentRatio,
                handleRadius = handleRadius.toPx(),
                touchRadius = touchRadius.toPx(),
                glyph = glyph.toPx(),
                stroke = stroke.toPx(),
                alphaBarOffset = alphaBarOffset.toPx(),
                alphaBarHeight = alphaBarHeight.toPx(),
                alphaThumbRadius = alphaThumbRadius.toPx(),
            )
        }
    }

    val boundsColor = colorResource(R.color.feature_editor_overlay_bounds)
    val handleColor = colorResource(R.color.feature_editor_overlay_handle)
    val glyphColor = colorResource(R.color.feature_editor_overlay_handle_glyph)
    val trackColor = colorResource(R.color.feature_editor_overlay_alpha_track)

    // A single pointer layer: two stacked Canvases would let the upper one swallow the gestures
    // of the lower one.
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(overlay, metrics) {
                detectTapGestures { point ->
                    if (OverlayGeometry(overlay.transform, size.toSize(), metrics).isDelete(point)) onDelete()
                }
            }
            .pointerInput(overlay, metrics) {
                var active: Handle? = null
                detectDragGestures(
                    onDragStart = { point ->
                        active = OverlayGeometry(overlay.transform, size.toSize(), metrics).handleAt(point)
                    },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, drag ->
                    val canvas = size.toSize()
                    val geometry = OverlayGeometry(overlay.transform, canvas, metrics)
                    val transform = overlay.transform
                    when (active) {
                        Handle.MOVE -> onTransformChange(
                            transform.copy(
                                positionX = (transform.positionX + drag.x / canvas.width).coerceIn(0f, 1f),
                                positionY = (transform.positionY + drag.y / canvas.height).coerceIn(0f, 1f),
                            ),
                        )
                        Handle.SCALE -> onTransformChange(
                            transform.copy(scale = (transform.scale - drag.x / canvas.width).coerceAtLeast(MIN_SCALE)),
                        )
                        Handle.ROTATE -> {
                            val angle = Math.toDegrees(
                                atan2(
                                    (change.position.y - geometry.center.y).toDouble(),
                                    (change.position.x - geometry.center.x).toDouble(),
                                ),
                            ).toFloat()
                            onTransformChange(transform.copy(rotationDegrees = angle))
                        }
                        Handle.ALPHA -> onTransformChange(
                            transform.copy(alpha = (transform.alpha + drag.x / geometry.alphaBarWidth).coerceIn(0f, 1f)),
                        )
                        null -> Unit
                    }
                }
            },
    ) {
        val geometry = OverlayGeometry(overlay.transform, size, metrics)
        drawRect(boundsColor, geometry.topLeft, Size(geometry.half * 2, geometry.half * 2), style = Stroke(metrics.stroke))

        drawCircle(handleColor, metrics.handleRadius, geometry.delete)
        drawLine(glyphColor, geometry.delete - Offset(metrics.glyph, metrics.glyph), geometry.delete + Offset(metrics.glyph, metrics.glyph), metrics.stroke)
        drawLine(glyphColor, geometry.delete - Offset(metrics.glyph, -metrics.glyph), geometry.delete + Offset(metrics.glyph, -metrics.glyph), metrics.stroke)

        drawCircle(handleColor, metrics.handleRadius, geometry.scaleHandle)
        drawLine(glyphColor, geometry.scaleHandle - Offset(metrics.glyph, 0f), geometry.scaleHandle + Offset(metrics.glyph, 0f), metrics.stroke)

        drawCircle(handleColor, metrics.handleRadius, geometry.rotateHandle)
        drawArc(
            glyphColor,
            ROTATE_GLYPH_START_DEGREES,
            ROTATE_GLYPH_SWEEP_DEGREES,
            false,
            geometry.rotateHandle - Offset(metrics.glyph, metrics.glyph),
            Size(metrics.glyph * 2, metrics.glyph * 2),
            style = Stroke(metrics.stroke),
        )

        drawRect(trackColor, Offset(geometry.scaleHandle.x, geometry.alphaBarY), Size(geometry.alphaBarWidth, metrics.alphaBarHeight))
        drawCircle(
            handleColor,
            metrics.alphaThumbRadius,
            Offset(geometry.scaleHandle.x + geometry.alphaBarWidth * overlay.transform.alpha, geometry.alphaBarY + metrics.alphaBarHeight / 2),
        )
    }
}

private const val MIN_SCALE = .01f
private const val ROTATE_GLYPH_START_DEGREES = -70f
private const val ROTATE_GLYPH_SWEEP_DEGREES = 260f

private fun Offset.distanceTo(other: Offset): Float = hypot(x - other.x, y - other.y)

private fun androidx.compose.ui.unit.IntSize.toSize(): Size = Size(width.toFloat(), height.toFloat())
