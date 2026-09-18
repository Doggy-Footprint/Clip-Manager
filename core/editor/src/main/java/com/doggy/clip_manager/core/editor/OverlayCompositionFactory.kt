package com.doggy.clip_manager.core.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import androidx.media3.common.Effect
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.TextOverlay as MediaTextOverlay
import com.doggy.clip_manager.core.model.ImageSource

@UnstableApi
class OverlayCompositionFactory(private val context: Context) {
    fun create(overlays: List<OverlaySpec>): List<Effect> {
        if (overlays.isEmpty()) return emptyList()
        val textures = overlays.map { overlay ->
            when (overlay) {
                is TextOverlay -> renderText(overlay)
                is ImageOverlay -> renderImage(overlay)
            }
        }
        return listOf(OverlayEffect(textures))
    }

    private fun renderText(spec: TextOverlay): TextureOverlay {
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            spec.style.fontSizePt,
            context.resources.displayMetrics,
        ).toInt()
        val text = SpannableString(spec.text).apply {
            setSpan(ForegroundColorSpan(spec.style.colorArgb.toInt()), 0, length, 0)
            setSpan(AbsoluteSizeSpan(sizePx), 0, length, 0)
            spec.style.backgroundArgb?.let { setSpan(BackgroundColorSpan(it.toInt()), 0, length, 0) }
        }
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(spec.style.positionX * 2f - 1f, 1f - spec.style.positionY * 2f)
            .setOverlayFrameAnchor(if (spec.style.centerAligned) 0f else -1f, -1f)
            .build()
        return TimedTextOverlay(spec.range, text, settings)
    }

    private fun renderImage(spec: ImageOverlay): TextureOverlay {
        val transform = spec.transform
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(transform.positionX * 2f - 1f, 1f - transform.positionY * 2f)
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(transform.scale, transform.scale)
            .setAlphaScale(transform.alpha)
            .setRotationDegrees(transform.rotationDegrees)
            .build()
        return TimedBitmapOverlay(spec.range, loadBitmap(spec.source), settings)
    }

    // Overlay images come from the user's gallery at full camera resolution; decoding them
    // unscaled next to a video frame pipeline exhausts the heap on large photos.
    private fun loadBitmap(source: ImageSource): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(source) { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return decode(source) { BitmapFactory.decodeStream(it, null, options) }
            ?: throw InputNotReadableException("cannot decode overlay image: ${source.uri}")
    }

    private fun <T> decode(source: ImageSource, block: (java.io.InputStream) -> T): T? = try {
        context.contentResolver.openInputStream(Uri.parse(source.uri))?.use(block)
            ?: throw InputNotReadableException("cannot open overlay image: ${source.uri}")
    } catch (error: InputNotReadableException) {
        throw error
    } catch (error: Exception) {
        throw InputNotReadableException("cannot open overlay image: ${source.uri}", error)
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_OVERLAY_EDGE_PX || height / sample > MAX_OVERLAY_EDGE_PX) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_OVERLAY_EDGE_PX = 2048
    }
}

@UnstableApi
private class TimedTextOverlay(
    range: TimeRange,
    private val text: SpannableString,
    visible: StaticOverlaySettings,
) : MediaTextOverlay() {
    private val gate = TimeGate(range, visible)

    override fun getText(presentationTimeUs: Long): SpannableString = text

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = gate.at(presentationTimeUs)
}

@UnstableApi
private class TimedBitmapOverlay(
    range: TimeRange,
    private val bitmap: Bitmap,
    visible: StaticOverlaySettings,
) : BitmapOverlay() {
    private val gate = TimeGate(range, visible)

    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = gate.at(presentationTimeUs)
}

// getOverlaySettings runs per rendered frame, so both variants are built once up front.
@UnstableApi
private class TimeGate(private val range: TimeRange, private val visible: StaticOverlaySettings) {
    private val hidden: StaticOverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(visible.backgroundFrameAnchor.first, visible.backgroundFrameAnchor.second)
        .setOverlayFrameAnchor(visible.overlayFrameAnchor.first, visible.overlayFrameAnchor.second)
        .setScale(visible.scale.first, visible.scale.second)
        .setRotationDegrees(visible.rotationDegrees)
        .setAlphaScale(0f)
        .build()

    fun at(presentationTimeUs: Long): StaticOverlaySettings =
        if (presentationTimeUs >= range.startUs && presentationTimeUs < range.endUs) visible else hidden
}
