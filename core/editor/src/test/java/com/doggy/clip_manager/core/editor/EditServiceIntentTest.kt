package com.doggy.clip_manager.core.editor

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.model.ImageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEC = 1_000_000L

/**
 * Exercises EditService.encodeStartIntent/decodeStartSpec (contract:
 * agent-docs/contracts/edit-service-overlay-intent.md, v1). C10-C12 deliberately avoid the real
 * overlay extra key names (implementer's choice) and instead locate the target extra by matching
 * its array type/size/content, so the tests stay valid regardless of the chosen key names.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35])
class EditServiceIntentTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // ---- C1-C6: normal round trips ----

    @Test
    fun decodeStartSpec_C1_normal_singleTextOverlayRoundTripsExactly() {
        val overlay = TextOverlay("t1", TimeRange(0, 2 * SEC), "hello")
        val spec = baseSpec(overlays = listOf(overlay))

        val decoded = roundTrip(spec)

        assertEquals(spec, decoded)
        assertEquals(listOf(overlay), decoded?.effects?.overlays)
    }

    @Test
    fun decodeStartSpec_C2_normal_nonDefaultTextStyleRoundTripsAllFields() {
        val style = TextOverlayStyle(
            positionX = 0.1f,
            positionY = 0.9f,
            fontSizePt = 33.5f,
            colorArgb = 0xff102030,
            backgroundArgb = 0x80000000,
            centerAligned = false,
        )
        val overlay = TextOverlay("t1", TimeRange(0, 2 * SEC), "hello", style)
        val spec = baseSpec(overlays = listOf(overlay))

        val decoded = roundTrip(spec)

        assertEquals(spec, decoded)
        val decodedStyle = (decoded?.effects?.overlays?.single() as TextOverlay).style
        assertNotNull(decodedStyle.backgroundArgb)
        assertEquals(style, decodedStyle)
    }

    @Test
    fun decodeStartSpec_C3_normal_nullBackgroundArgbRoundTripsAsNullNotASentinel() {
        val overlay = TextOverlay("t1", TimeRange(0, SEC), "hello", TextOverlayStyle(backgroundArgb = null))
        val spec = baseSpec(overlays = listOf(overlay))

        val decoded = roundTrip(spec)

        val decodedStyle = (decoded?.effects?.overlays?.single() as TextOverlay).style
        assertNull(decodedStyle.backgroundArgb)
    }

    @Test
    fun decodeStartSpec_C4_normal_imageOverlayRoundTripsAllFields() {
        val overlay = ImageOverlay(
            "i1",
            TimeRange(SEC, 3 * SEC),
            ImageSource("content://media/external/images/media/42"),
            OverlayTransform(0.25f, 0.75f, 2f, 0.5f, -45f),
        )
        val spec = baseSpec(overlays = listOf(overlay))

        val decoded = roundTrip(spec)

        assertEquals(spec, decoded)
        assertEquals(listOf(overlay), decoded?.effects?.overlays)
    }

    @Test
    fun decodeStartSpec_C5_normal_mixedOverlayListPreservesOrderTypesAndFields() {
        val overlays = listOf(
            TextOverlay("t1", TimeRange(0, SEC), "first"),
            ImageOverlay("i1", TimeRange(SEC, 2 * SEC), ImageSource("content://media/1")),
            TextOverlay("t2", TimeRange(2 * SEC, 3 * SEC), "second"),
            ImageOverlay("i2", TimeRange(3 * SEC, 4 * SEC), ImageSource("content://media/2")),
        )
        val spec = baseSpec(overlays = overlays)

        val decoded = roundTrip(spec)

        assertEquals(overlays, decoded?.effects?.overlays)
    }

    @Test
    fun decodeStartSpec_C6_normal_allFourEffectKindsTogetherRoundTripExactly() {
        val spec = EditSpec(
            inputPath = "/movies/a.mp4",
            keepRanges = listOf(TimeRange(0, 10 * SEC)),
            cutMode = CutMode.PRECISE,
            effects = EditEffects(
                frameLayout = FrameLayout.Ratio(16, 9, FrameMode.CROP, NormalizedPoint(0.5f, 0.5f)),
                flips = listOf(FlipRange(TimeRange(0, SEC), horizontal = true, vertical = false)),
                speeds = listOf(SpeedRange(TimeRange(SEC, 2 * SEC), speed = 1.5f)),
                overlays = listOf(TextOverlay("t1", TimeRange(0, SEC), "hi")),
            ),
        )

        val decoded = roundTrip(spec)

        assertEquals(spec, decoded)
    }

    // ---- C7-C9: boundaries ----

    @Test
    fun decodeStartSpec_C7_boundary_emptyOverlayListRoundTripsAsEmptyList() {
        val spec = baseSpec(overlays = emptyList())

        val decoded = roundTrip(spec)

        assertEquals(emptyList<OverlaySpec>(), decoded?.effects?.overlays)
        assertEquals(spec, decoded)
    }

    @Test
    fun decodeStartSpec_C8_boundary_extremeTransformValuesRoundTripAndAreNotNull() {
        val transform = OverlayTransform(positionX = 0f, positionY = 1f, scale = 0.001f, alpha = 0f, rotationDegrees = 0f)
        val overlay = ImageOverlay("i1", TimeRange(0, SEC), ImageSource("content://media/1"), transform)
        val spec = baseSpec(overlays = listOf(overlay))

        val decoded = roundTrip(spec)

        assertNotNull(decoded)
        assertEquals(transform, (decoded?.effects?.overlays?.single() as ImageOverlay).transform)
    }

    @Test
    fun decodeStartSpec_C9_boundary_zeroBackgroundArgbRoundTripsAsZeroNotNull() {
        val overlay = TextOverlay("t1", TimeRange(0, SEC), "hello", TextOverlayStyle(backgroundArgb = 0L))
        val spec = baseSpec(overlays = listOf(overlay))

        val decoded = roundTrip(spec)

        val decodedStyle = (decoded?.effects?.overlays?.single() as TextOverlay).style
        assertNotNull(decodedStyle.backgroundArgb)
        assertEquals(0L, decodedStyle.backgroundArgb)
    }

    @Test
    fun decodeStartSpec_U3_normal_nonDefaultFrameFlipSpeedRoundTripWhenOverlaysAreEmpty() {
        val spec = EditSpec(
            inputPath = "/movies/a.mp4",
            keepRanges = listOf(TimeRange(0, 10 * SEC)),
            cutMode = CutMode.PRECISE,
            effects = EditEffects(
                frameLayout = FrameLayout.Ratio(16, 9, FrameMode.CROP, NormalizedPoint(0.25f, 0.75f)),
                flips = listOf(FlipRange(TimeRange(0, SEC), horizontal = true, vertical = false)),
                speeds = listOf(SpeedRange(TimeRange(SEC, 2 * SEC), speed = 1.5f)),
                overlays = emptyList(),
            ),
        )

        val decoded = roundTrip(spec)

        assertEquals(spec, decoded)
        assertEquals(emptyList<OverlaySpec>(), decoded?.effects?.overlays)
    }

    // ---- C10-C12: corrupted overlay extras ----

    @Test
    fun decodeStartSpec_C10_error_idArrayShortenedByOneReturnsNull() {
        val idA = "alpha-overlay"
        val idB = "beta-overlay"
        val spec = baseSpec(
            overlays = listOf(
                TextOverlay(idA, TimeRange(0, SEC), "hello"),
                ImageOverlay(idB, TimeRange(SEC, 2 * SEC), ImageSource("content://media/1")),
            ),
        )
        val intent = EditService.encodeStartIntent(context, spec)
        val idKey = findArrayKey(intent) { _, elements -> elements == listOf(idA, idB) }
        val original = requireNotNull(intent.extras).get(idKey)!!
        putRebuilt(intent, idKey, original, elementsOf(original)!!.dropLast(1))

        val decoded = EditService.decodeStartSpec(intent)

        assertNull(decoded)
    }

    @Test
    fun decodeStartSpec_C11_error_kindArrayOverwrittenWithUnknownValueReturnsNull() {
        val idA = "alpha-overlay"
        val idB = "beta-overlay"
        val spec = baseSpec(
            overlays = listOf(
                TextOverlay(idA, TimeRange(0, SEC), "hello"),
                ImageOverlay(idB, TimeRange(SEC, 2 * SEC), ImageSource("content://media/1")),
            ),
        )
        val intent = EditService.encodeStartIntent(context, spec)
        val idKey = findArrayKey(intent) { _, elements -> elements == listOf(idA, idB) }
        val kindKey = findArrayKey(intent) { key, elements ->
            key != idKey && elements.size == 2 && elements.all { it is String } && elements != listOf(idA, idB)
        }
        val original = requireNotNull(intent.extras).get(kindKey)!!
        val corrupted = elementsOf(original)!!.toMutableList().also { it[0] = "STICKER" }
        putRebuilt(intent, kindKey, original, corrupted)

        val decoded = EditService.decodeStartSpec(intent)

        assertNull(decoded)
    }

    @Test
    fun decodeStartSpec_C12_error_oneOverlayArrayElementRemovedWithoutTrimmingOthersReturnsNull() {
        val idA = "alpha-overlay"
        val idB = "beta-overlay"
        val spec = baseSpec(
            overlays = listOf(
                TextOverlay(idA, TimeRange(0, SEC), "hello"),
                ImageOverlay(idB, TimeRange(SEC, 2 * SEC), ImageSource("content://media/1")),
            ),
        )
        val intent = EditService.encodeStartIntent(context, spec)
        val idKey = findArrayKey(intent) { _, elements -> elements == listOf(idA, idB) }
        val targetKey = findArrayKey(intent) { key, elements -> key != idKey && elements.size == 2 }
        val original = requireNotNull(intent.extras).get(targetKey)!!
        putRebuilt(intent, targetKey, original, elementsOf(original)!!.dropLast(1))

        val decoded = EditService.decodeStartSpec(intent)

        assertNull(decoded)
    }

    @Test
    fun decodeStartSpec_C13_error_duplicateIdAcrossTextAndImageOverlayReturnsNull() {
        val spec = baseSpec(
            overlays = listOf(
                TextOverlay("dup", TimeRange(0, SEC), "first"),
                ImageOverlay("dup", TimeRange(SEC, 2 * SEC), ImageSource("content://media/1")),
            ),
        )
        val intent = EditService.encodeStartIntent(context, spec)

        val decoded = EditService.decodeStartSpec(intent)

        assertNull(decoded)
    }

    // ---- C13-C14: existing validateEffects path swallowed into null ----

    @Test
    fun decodeStartSpec_C13_error_duplicateOverlayIdReturnsNull() {
        val spec = baseSpec(
            overlays = listOf(
                TextOverlay("dup", TimeRange(0, SEC), "first"),
                TextOverlay("dup", TimeRange(SEC, 2 * SEC), "second"),
            ),
        )
        val intent = EditService.encodeStartIntent(context, spec)

        val decoded = EditService.decodeStartSpec(intent)

        assertNull(decoded)
    }

    @Test
    fun decodeStartSpec_C14_error_reversedOverlayRangeReturnsNull() {
        val spec = baseSpec(overlays = listOf(TextOverlay("t1", TimeRange(2 * SEC, SEC), "hello")))
        val intent = EditService.encodeStartIntent(context, spec)

        val decoded = EditService.decodeStartSpec(intent)

        assertNull(decoded)
    }

    // ---- C15-C16: edge cases ----

    @Test
    fun decodeStartSpec_C15_edge_intentWithNoOverlayExtrasAtAllDecodesToEmptyOverlaysNotNull() {
        val overlay = TextOverlay("t1", TimeRange(0, SEC), "hello")
        val specWithOverlay = baseSpec(overlays = listOf(overlay))
        val specWithoutOverlay = baseSpec(overlays = emptyList())
        val withIntent = EditService.encodeStartIntent(context, specWithOverlay)
        val withoutIntent = EditService.encodeStartIntent(context, specWithoutOverlay)

        val overlayOnlyKeys = requireNotNull(withIntent.extras).keySet().filter { key ->
            val withSize = elementsOf(requireNotNull(withIntent.extras).get(key))?.size ?: 0
            val withoutSize = elementsOf(withoutIntent.extras?.get(key))?.size ?: 0
            withSize > 0 && withoutSize == 0
        }
        overlayOnlyKeys.forEach(withIntent::removeExtra)

        val decoded = EditService.decodeStartSpec(withIntent)

        assertNotNull(decoded)
        assertEquals(emptyList<OverlaySpec>(), decoded?.effects?.overlays)
    }

    @Test
    fun decodeStartSpec_C16_edge_textWithNewlineAndNonAsciiCharactersRoundTripsExactly() {
        val overlay = TextOverlay("t1", TimeRange(0, SEC), "안녕\n세계")
        val spec = baseSpec(overlays = listOf(overlay))

        val decoded = roundTrip(spec)

        assertEquals("안녕\n세계", (decoded?.effects?.overlays?.single() as TextOverlay).text)
    }

    // ---- helpers ----

    private fun baseSpec(overlays: List<OverlaySpec>): EditSpec = EditSpec(
        inputPath = "/movies/a.mp4",
        keepRanges = listOf(TimeRange(0, 10 * SEC)),
        cutMode = CutMode.PRECISE,
        effects = EditEffects(overlays = overlays),
    )

    private fun roundTrip(spec: EditSpec): EditSpec? =
        EditService.decodeStartSpec(EditService.encodeStartIntent(context, spec))

    private fun findArrayKey(intent: Intent, predicate: (key: String, elements: List<Any?>) -> Boolean): String {
        val bundle = requireNotNull(intent.extras)
        return bundle.keySet().first { key ->
            val elements = elementsOf(bundle.get(key)) ?: return@first false
            predicate(key, elements)
        }
    }

    private fun elementsOf(value: Any?): List<Any?>? = when (value) {
        is LongArray -> value.toList()
        is IntArray -> value.toList()
        is FloatArray -> value.toList()
        is DoubleArray -> value.toList()
        is BooleanArray -> value.toList()
        is Array<*> -> value.toList()
        else -> null
    }

    private fun putRebuilt(intent: Intent, key: String, original: Any, elements: List<Any?>) {
        when (original) {
            is LongArray -> intent.putExtra(key, elements.map { it as Long }.toLongArray())
            is IntArray -> intent.putExtra(key, elements.map { it as Int }.toIntArray())
            is FloatArray -> intent.putExtra(key, elements.map { it as Float }.toFloatArray())
            is DoubleArray -> intent.putExtra(key, elements.map { it as Double }.toDoubleArray())
            is BooleanArray -> intent.putExtra(key, elements.map { it as Boolean }.toBooleanArray())
            is Array<*> -> intent.putExtra(key, elements.map { it as String }.toTypedArray())
            else -> error("unsupported array type for key=$key: ${original::class}")
        }
    }
}
