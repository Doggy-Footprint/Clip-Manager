package com.doggy.clip_manager.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.FlipRange
import com.doggy.clip_manager.core.editor.FrameMode
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.OverlayTransform
import com.doggy.clip_manager.core.editor.SpeedRange
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.editor.TextOverlayStyle
import com.doggy.clip_manager.core.editor.TimeRange
import com.doggy.clip_manager.core.model.ImageSource
import kotlin.reflect.KProperty
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs one [EditorViewModel] field with [SavedStateHandle]: the initial value is decoded once at
 * construction and every assignment re-encodes and writes it back (F8). [decode] is required to
 * never throw for corrupt or missing values (Errors, C9, C10); this wrapper also catches any
 * exception [decode] fails to and falls back to [default] so one bad field cannot crash restore.
 */
internal class PersistedField<T>(
    private val handle: SavedStateHandle,
    private val key: String,
    default: T,
    private val encode: (T) -> Any?,
    decode: (Any?) -> T,
) {
    private var current: T by mutableStateOf(
        runCatching { decode(handle.get<Any?>(key)) }.getOrDefault(default),
    )

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = current

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        current = value
        handle[key] = encode(value)
    }
}

internal fun <T> SavedStateHandle.persisted(
    key: String,
    default: T,
    encode: (T) -> Any?,
    decode: (Any?) -> T,
): PersistedField<T> = PersistedField(this, key, default, encode, decode)

internal fun encodeCutMode(mode: CutMode): Any = mode.name
internal fun decodeCutMode(value: Any?): CutMode =
    runCatching { CutMode.valueOf(value as String) }.getOrDefault(CutMode.PRECISE)

internal fun encodeRatioPreset(preset: RatioPreset): Any = preset.name
internal fun decodeRatioPreset(value: Any?): RatioPreset =
    runCatching { RatioPreset.valueOf(value as String) }.getOrDefault(RatioPreset.ORIGINAL)

internal fun encodeFrameMode(mode: FrameMode): Any = mode.name
internal fun decodeFrameMode(value: Any?): FrameMode =
    runCatching { FrameMode.valueOf(value as String) }.getOrDefault(FrameMode.CROP)

internal fun encodeTimeRange(range: TimeRange): Any = longArrayOf(range.startUs, range.endUs)

/** Null means the saved value was missing or undecodable; callers pick a duration-aware fallback. */
internal fun decodeTimeRange(value: Any?): TimeRange? = runCatching {
    val array = value as LongArray
    TimeRange(array[0], array[1])
}.getOrNull()

internal fun encodeStringSet(ids: Set<String>): Any = ids.toTypedArray()
internal fun decodeStringSet(value: Any?): Set<String> = runCatching {
    @Suppress("UNCHECKED_CAST")
    (value as Array<String>).toSet()
}.getOrDefault(emptySet())

internal fun encodeFlips(flips: List<FlipRange>): Any {
    val array = JSONArray()
    flips.forEach { flip ->
        array.put(
            JSONObject()
                .put("start", flip.range.startUs)
                .put("end", flip.range.endUs)
                .put("horizontal", flip.horizontal)
                .put("vertical", flip.vertical),
        )
    }
    return array.toString()
}

internal fun decodeFlips(value: Any?): List<FlipRange> = runCatching {
    val array = JSONArray(value as String)
    (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        FlipRange(
            range = TimeRange(obj.getLong("start"), obj.getLong("end")),
            horizontal = obj.getBoolean("horizontal"),
            vertical = obj.getBoolean("vertical"),
        )
    }
}.getOrDefault(emptyList())

internal fun encodeSpeeds(speeds: List<SpeedRange>): Any {
    val array = JSONArray()
    speeds.forEach { speed ->
        array.put(
            JSONObject()
                .put("start", speed.range.startUs)
                .put("end", speed.range.endUs)
                .put("speed", speed.speed.toDouble()),
        )
    }
    return array.toString()
}

internal fun decodeSpeeds(value: Any?): List<SpeedRange> = runCatching {
    val array = JSONArray(value as String)
    (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        SpeedRange(
            range = TimeRange(obj.getLong("start"), obj.getLong("end")),
            speed = obj.getDouble("speed").toFloat(),
        )
    }
}.getOrDefault(emptyList())

private const val OVERLAY_TYPE_TEXT = "text"
private const val OVERLAY_TYPE_IMAGE = "image"

internal fun encodeOverlays(overlays: List<OverlaySpec>): Any {
    val array = JSONArray()
    overlays.forEach { overlay ->
        val obj = JSONObject()
            .put("id", overlay.id)
            .put("start", overlay.range.startUs)
            .put("end", overlay.range.endUs)
        when (overlay) {
            is TextOverlay -> obj
                .put("type", OVERLAY_TYPE_TEXT)
                .put("text", overlay.text)
                .put("positionX", overlay.style.positionX.toDouble())
                .put("positionY", overlay.style.positionY.toDouble())
                .put("fontSizePt", overlay.style.fontSizePt.toDouble())
                .put("colorArgb", overlay.style.colorArgb)
                .put("backgroundArgb", overlay.style.backgroundArgb ?: JSONObject.NULL)
                .put("centerAligned", overlay.style.centerAligned)
            is ImageOverlay -> obj
                .put("type", OVERLAY_TYPE_IMAGE)
                .put("sourceUri", overlay.source.uri)
                .put("positionX", overlay.transform.positionX.toDouble())
                .put("positionY", overlay.transform.positionY.toDouble())
                .put("scale", overlay.transform.scale.toDouble())
                .put("alpha", overlay.transform.alpha.toDouble())
                .put("rotationDegrees", overlay.transform.rotationDegrees.toDouble())
        }
        array.put(obj)
    }
    return array.toString()
}

internal fun decodeOverlays(value: Any?): List<OverlaySpec> = runCatching {
    val array = JSONArray(value as String)
    (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        val range = TimeRange(obj.getLong("start"), obj.getLong("end"))
        val id = obj.getString("id")
        when (obj.getString("type")) {
            OVERLAY_TYPE_TEXT -> TextOverlay(
                id = id,
                range = range,
                text = obj.getString("text"),
                style = TextOverlayStyle(
                    positionX = obj.getDouble("positionX").toFloat(),
                    positionY = obj.getDouble("positionY").toFloat(),
                    fontSizePt = obj.getDouble("fontSizePt").toFloat(),
                    colorArgb = obj.getLong("colorArgb"),
                    backgroundArgb = if (obj.isNull("backgroundArgb")) null else obj.getLong("backgroundArgb"),
                    centerAligned = obj.getBoolean("centerAligned"),
                ),
            )
            OVERLAY_TYPE_IMAGE -> ImageOverlay(
                id = id,
                range = range,
                source = ImageSource(obj.getString("sourceUri")),
                transform = OverlayTransform(
                    positionX = obj.getDouble("positionX").toFloat(),
                    positionY = obj.getDouble("positionY").toFloat(),
                    scale = obj.getDouble("scale").toFloat(),
                    alpha = obj.getDouble("alpha").toFloat(),
                    rotationDegrees = obj.getDouble("rotationDegrees").toFloat(),
                ),
            )
            else -> error("unknown overlay type")
        }
    }
}.getOrDefault(emptyList())
