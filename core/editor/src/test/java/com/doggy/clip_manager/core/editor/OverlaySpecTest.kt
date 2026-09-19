package com.doggy.clip_manager.core.editor

import com.doggy.clip_manager.core.model.ImageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEC = 1_000_000L

class OverlaySpecTest {

    @Test
    fun textOverlayStyle_R1_normal_defaultIsBottomCenteredOpaqueWhiteSubtitlePresetRatherThanADedicatedSubtitleType() {
        val default = TextOverlayStyle.Default

        assertEquals(0.5f, default.positionX)
        assertEquals(1f, default.positionY)
        assertEquals(20f, default.fontSizePt)
        assertEquals(0xffffffff, default.colorArgb)
        assertEquals(null, default.backgroundArgb)
        assertTrue(default.centerAligned)
    }

    @Test
    fun overlayTransform_R2_normal_defaultIsCenteredUnitScaleFullAlphaNoRotation() {
        val default = OverlayTransform()

        assertEquals(0.5f, default.positionX)
        assertEquals(0.5f, default.positionY)
        assertEquals(1f, default.scale)
        assertEquals(1f, default.alpha)
        assertEquals(0f, default.rotationDegrees)
    }

    @Test
    fun validateOverlays_R3_error_blankIdThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(id = "", range = TimeRange(0, SEC))))
        }
    }

    @Test
    fun validateOverlays_R3_error_whitespaceOnlyIdThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(id = "   ", range = TimeRange(0, SEC))))
        }
    }

    @Test
    fun validateOverlays_R3_error_duplicateIdThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(
                    textOverlay(id = "a", range = TimeRange(0, SEC)),
                    textOverlay(id = "a", range = TimeRange(SEC, 2 * SEC)),
                ),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_zeroLengthRangeThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(range = TimeRange(SEC, SEC))))
        }
    }

    @Test
    fun validateOverlays_R3_error_startAfterEndRangeThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(range = TimeRange(2 * SEC, SEC))))
        }
    }

    @Test
    fun validateOverlays_R3_error_blankTextThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(text = "   ")))
        }
    }

    @Test
    fun validateOverlays_R3_error_textStylePositionXBelowZeroThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(style = TextOverlayStyle(positionX = -0.01f))))
        }
    }

    @Test
    fun validateOverlays_R3_error_textStylePositionXAboveOneThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(style = TextOverlayStyle(positionX = 1.01f))))
        }
    }

    @Test
    fun validateOverlays_R3_error_textStylePositionXNaNThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(style = TextOverlayStyle(positionX = Float.NaN))))
        }
    }

    @Test
    fun validateOverlays_R3_error_textStylePositionXInfiniteThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(textOverlay(style = TextOverlayStyle(positionX = Float.POSITIVE_INFINITY))),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_textStylePositionYNaNThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(style = TextOverlayStyle(positionY = Float.NaN))))
        }
    }

    @Test
    fun validateOverlays_R3_error_textStylePositionYInfiniteThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(textOverlay(style = TextOverlayStyle(positionY = Float.NEGATIVE_INFINITY))),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_fontSizeZeroThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(style = TextOverlayStyle(fontSizePt = 0f))))
        }
    }

    @Test
    fun validateOverlays_R3_error_fontSizeNaNThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(style = TextOverlayStyle(fontSizePt = Float.NaN))))
        }
    }

    @Test
    fun validateOverlays_R3_error_fontSizeInfiniteThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(textOverlay(style = TextOverlayStyle(fontSizePt = Float.POSITIVE_INFINITY))),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformPositionXBelowZeroThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(positionX = -0.01f))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformPositionXAboveOneThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(positionX = 1.01f))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformPositionXNaNThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(positionX = Float.NaN))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformPositionXInfiniteThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(imageOverlay(transform = OverlayTransform(positionX = Float.POSITIVE_INFINITY))),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformPositionYNaNThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(positionY = Float.NaN))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformPositionYInfiniteThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(imageOverlay(transform = OverlayTransform(positionY = Float.NEGATIVE_INFINITY))),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformScaleZeroThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(scale = 0f))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformScaleNaNThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(scale = Float.NaN))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformScaleInfiniteThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(imageOverlay(transform = OverlayTransform(scale = Float.POSITIVE_INFINITY))),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformAlphaBelowZeroThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(alpha = -0.01f))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformAlphaAboveOneThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(alpha = 1.01f))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformAlphaNaNThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(alpha = Float.NaN))))
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformAlphaInfiniteThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(imageOverlay(transform = OverlayTransform(alpha = Float.POSITIVE_INFINITY))),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformRotationNaNThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(imageOverlay(transform = OverlayTransform(rotationDegrees = Float.NaN))),
            )
        }
    }

    @Test
    fun validateOverlays_R3_error_imageTransformRotationInfiniteThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(
                listOf(imageOverlay(transform = OverlayTransform(rotationDegrees = Float.POSITIVE_INFINITY))),
            )
        }
    }

    @Test
    fun validateOverlays_R4_normal_mixedValidTextAndImageOverlaysDoNotThrow() {
        EditPlanner.validateOverlays(
            listOf(
                textOverlay(id = "t1", range = TimeRange(0, SEC), text = "hello"),
                imageOverlay(id = "i1", range = TimeRange(SEC, 2 * SEC)),
            ),
        )
    }

    @Test
    fun validateOverlays_R4_boundary_positionAndAlphaAtZeroAndOneAreAccepted() {
        EditPlanner.validateOverlays(
            listOf(
                textOverlay(id = "t1", range = TimeRange(0, SEC), style = TextOverlayStyle(positionX = 0f, positionY = 0f)),
                textOverlay(id = "t2", range = TimeRange(SEC, 2 * SEC), style = TextOverlayStyle(positionX = 1f, positionY = 1f)),
                imageOverlay(
                    id = "i1",
                    range = TimeRange(2 * SEC, 3 * SEC),
                    transform = OverlayTransform(positionX = 0f, positionY = 0f, alpha = 0f),
                ),
                imageOverlay(
                    id = "i2",
                    range = TimeRange(3 * SEC, 4 * SEC),
                    transform = OverlayTransform(positionX = 1f, positionY = 1f, alpha = 1f),
                ),
            ),
        )
    }

    @Test
    fun normalizeOutputOverlays_R5_normal_overlayStartingBeforeZeroIsClippedToZero() {
        val result = EditPlanner.normalizeOutputOverlays(
            outputDurationUs = 10 * SEC,
            overlays = listOf(textOverlay(id = "t1", range = TimeRange(-2 * SEC, 3 * SEC))),
        )

        assertEquals(listOf(textOverlay(id = "t1", range = TimeRange(0, 3 * SEC))), result)
    }

    @Test
    fun normalizeOutputOverlays_R5_normal_overlayEndingAfterDurationIsClippedToDuration() {
        val result = EditPlanner.normalizeOutputOverlays(
            outputDurationUs = 10 * SEC,
            overlays = listOf(textOverlay(id = "t1", range = TimeRange(8 * SEC, 15 * SEC))),
        )

        assertEquals(listOf(textOverlay(id = "t1", range = TimeRange(8 * SEC, 10 * SEC))), result)
    }

    @Test
    fun normalizeOutputOverlays_R5_normal_overlayFullyInsideDurationIsUntouched() {
        val overlay = textOverlay(id = "t1", range = TimeRange(2 * SEC, 4 * SEC))

        val result = EditPlanner.normalizeOutputOverlays(outputDurationUs = 10 * SEC, overlays = listOf(overlay))

        assertEquals(listOf(overlay), result)
    }

    @Test
    fun normalizeOutputOverlays_R5_normal_preservesConcreteTypeAndFieldsOfTextOverlay() {
        val style = TextOverlayStyle(positionX = 0.2f, positionY = 0.3f, fontSizePt = 32f)
        val overlay = TextOverlay(id = "t1", range = TimeRange(2 * SEC, 4 * SEC), text = "caption", style = style)

        val result = EditPlanner.normalizeOutputOverlays(outputDurationUs = 10 * SEC, overlays = listOf(overlay))

        assertEquals(listOf(overlay), result)
        assertTrue(result.single() is TextOverlay)
        assertEquals("caption", (result.single() as TextOverlay).text)
        assertEquals(style, (result.single() as TextOverlay).style)
    }

    @Test
    fun normalizeOutputOverlays_R5_normal_preservesConcreteTypeAndFieldsOfImageOverlay() {
        val transform = OverlayTransform(positionX = 0.2f, positionY = 0.8f, scale = 1.5f, alpha = 0.5f, rotationDegrees = 90f)
        val source = ImageSource("content://media/1")
        val overlay = ImageOverlay(id = "i1", range = TimeRange(2 * SEC, 4 * SEC), source = source, transform = transform)

        val result = EditPlanner.normalizeOutputOverlays(outputDurationUs = 10 * SEC, overlays = listOf(overlay))

        assertEquals(listOf(overlay), result)
        assertTrue(result.single() is ImageOverlay)
        assertEquals(source, (result.single() as ImageOverlay).source)
        assertEquals(transform, (result.single() as ImageOverlay).transform)
    }

    @Test
    fun normalizeOutputOverlays_R6_boundary_rangeEndingExactlyAtDurationIsKept() {
        val result = EditPlanner.normalizeOutputOverlays(
            outputDurationUs = 10 * SEC,
            overlays = listOf(textOverlay(id = "t1", range = TimeRange(5 * SEC, 10 * SEC))),
        )

        assertEquals(listOf(textOverlay(id = "t1", range = TimeRange(5 * SEC, 10 * SEC))), result)
    }

    @Test
    fun normalizeOutputOverlays_R6_boundary_rangeStartingExactlyAtDurationIsDropped() {
        val result = EditPlanner.normalizeOutputOverlays(
            outputDurationUs = 10 * SEC,
            overlays = listOf(textOverlay(id = "t1", range = TimeRange(10 * SEC, 12 * SEC))),
        )

        assertEquals(emptyList<OverlaySpec>(), result)
    }

    @Test
    fun normalizeOutputOverlays_R6_boundary_rangeEntirelyAfterDurationIsDropped() {
        val result = EditPlanner.normalizeOutputOverlays(
            outputDurationUs = 10 * SEC,
            overlays = listOf(textOverlay(id = "t1", range = TimeRange(11 * SEC, 13 * SEC))),
        )

        assertEquals(emptyList<OverlaySpec>(), result)
    }

    @Test
    fun overlayEditSession_R7_normal_addAppendsOverlayToState() {
        val session = OverlayEditSession()
        val overlay = textOverlay(id = "t1", range = TimeRange(0, SEC))

        session.add(overlay)

        assertEquals(listOf(overlay), session.overlays.value)
    }

    @Test
    fun overlayEditSession_R7_error_addingDuplicateIdThrowsAndLeavesListUnchanged() {
        val session = OverlayEditSession()
        val original = textOverlay(id = "t1", range = TimeRange(0, SEC))
        session.add(original)

        assertThrows(InvalidEffectException::class.java) {
            session.add(textOverlay(id = "t1", range = TimeRange(SEC, 2 * SEC)))
        }

        assertEquals(listOf(original), session.overlays.value)
    }

    @Test
    fun overlayEditSession_R7_normal_updateReplacesMatchingOverlay() {
        val session = OverlayEditSession()
        session.add(textOverlay(id = "t1", range = TimeRange(0, SEC), text = "hello"))
        val updated = textOverlay(id = "t1", range = TimeRange(0, SEC), text = "goodbye")

        session.update(updated)

        assertEquals(listOf(updated), session.overlays.value)
    }

    @Test
    fun overlayEditSession_R7_error_updatingUnknownIdThrowsInvalidEffect() {
        val session = OverlayEditSession()

        assertThrows(InvalidEffectException::class.java) {
            session.update(textOverlay(id = "missing", range = TimeRange(0, SEC)))
        }

        assertEquals(emptyList<OverlaySpec>(), session.overlays.value)
    }

    @Test
    fun overlayEditSession_R7_error_updatingWithInvalidValueThrowsAndLeavesListUnchanged() {
        val session = OverlayEditSession()
        val original = textOverlay(id = "t1", range = TimeRange(0, SEC), text = "hello")
        session.add(original)

        assertThrows(InvalidEffectException::class.java) {
            session.update(textOverlay(id = "t1", range = TimeRange(0, SEC), text = "   "))
        }

        assertEquals(listOf(original), session.overlays.value)
    }

    @Test
    fun overlayEditSession_R7_normal_removeOfKnownIdDropsIt() {
        val session = OverlayEditSession()
        session.add(textOverlay(id = "t1", range = TimeRange(0, SEC)))

        session.remove("t1")

        assertEquals(emptyList<OverlaySpec>(), session.overlays.value)
    }

    @Test
    fun overlayEditSession_R7_edge_removeOfUnknownIdIsNoOp() {
        val session = OverlayEditSession()
        val original = textOverlay(id = "t1", range = TimeRange(0, SEC))
        session.add(original)

        session.remove("missing")

        assertEquals(listOf(original), session.overlays.value)
    }

    @Test
    fun overlayEditSession_R7_normal_clearRemovesAllOverlays() {
        val session = OverlayEditSession()
        session.add(textOverlay(id = "t1", range = TimeRange(0, SEC)))
        session.add(imageOverlay(id = "i1", range = TimeRange(SEC, 2 * SEC)))

        session.clear()

        assertEquals(emptyList<OverlaySpec>(), session.overlays.value)
    }

    @Test
    fun editEffects_R8_normal_overlaysMakeIsPresentTrue() {
        val effects = EditEffects(overlays = listOf(textOverlay(id = "t1", range = TimeRange(0, SEC))))

        assertTrue(effects.isPresent)
    }

    @Test
    fun editEffects_R8_normal_noOverlaysOrOtherEffectsMeansIsPresentFalse() {
        assertFalse(EditEffects().isPresent)
    }

    @Test
    fun effectiveCutMode_R8_normal_onlyOverlaysPresentForcesRequestedFastToPrecise() {
        val effects = EditEffects(overlays = listOf(textOverlay(id = "t1", range = TimeRange(0, SEC))))

        assertEquals(CutMode.PRECISE, EditPlanner.effectiveCutMode(CutMode.FAST, effects))
    }

    @Test
    fun normalizeOutputOverlays_R9_normal_processesEveryOverlayInTheListNotJustTheFirst() {
        val untouched = textOverlay(id = "t1", range = TimeRange(1 * SEC, 3 * SEC))
        val straddling = textOverlay(id = "t2", range = TimeRange(8 * SEC, 15 * SEC))
        val pastEnd = textOverlay(id = "t3", range = TimeRange(11 * SEC, 13 * SEC))

        val result = EditPlanner.normalizeOutputOverlays(
            outputDurationUs = 10 * SEC,
            overlays = listOf(untouched, straddling, pastEnd),
        )

        assertEquals(
            listOf(untouched, textOverlay(id = "t2", range = TimeRange(8 * SEC, 10 * SEC))),
            result,
        )
    }

    @Test
    fun normalizeOutputOverlays_R9_error_blankIdThrowsInvalidEffectBeforeClipping() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.normalizeOutputOverlays(
                outputDurationUs = 10 * SEC,
                overlays = listOf(textOverlay(id = "", range = TimeRange(-2 * SEC, 3 * SEC))),
            )
        }
    }

    @Test
    fun normalizeOutputOverlays_R9_error_duplicateIdAcrossElementsThrowsInvalidEffectBeforeClipping() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.normalizeOutputOverlays(
                outputDurationUs = 10 * SEC,
                overlays = listOf(
                    textOverlay(id = "t1", range = TimeRange(0, SEC)),
                    textOverlay(id = "t1", range = TimeRange(11 * SEC, 13 * SEC)),
                ),
            )
        }
    }

    @Test
    fun validateOverlays_R10_error_textStylePositionYBelowZeroThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(style = TextOverlayStyle(positionY = -0.01f))))
        }
    }

    @Test
    fun validateOverlays_R10_error_textStylePositionYAboveOneThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(textOverlay(style = TextOverlayStyle(positionY = 1.01f))))
        }
    }

    @Test
    fun validateOverlays_R10_error_imageTransformPositionYBelowZeroThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(positionY = -0.01f))))
        }
    }

    @Test
    fun validateOverlays_R10_error_imageTransformPositionYAboveOneThrowsInvalidEffect() {
        assertThrows(InvalidEffectException::class.java) {
            EditPlanner.validateOverlays(listOf(imageOverlay(transform = OverlayTransform(positionY = 1.01f))))
        }
    }

    @Test
    fun overlayEditSession_R11_normal_updatingOneOverlayLeavesTheOtherUntouchedAndPreservesOrder() {
        val session = OverlayEditSession()
        val first = textOverlay(id = "t1", range = TimeRange(0, SEC), text = "first")
        val second = textOverlay(id = "t2", range = TimeRange(SEC, 2 * SEC), text = "second")
        session.add(first)
        session.add(second)
        val updatedFirst = textOverlay(id = "t1", range = TimeRange(0, SEC), text = "first-updated")

        session.update(updatedFirst)

        assertEquals(listOf(updatedFirst, second), session.overlays.value)
    }

    /** F1: id is the identity key, so update targets the slot holding it and never renames an overlay. */
    @Test
    fun overlayEditSession_F1_edge_updatingWithAnIdCopiedFromAnotherOverlayOverwritesThatOverlaysSlot() {
        val session = OverlayEditSession()
        val first = textOverlay(id = "t1", range = TimeRange(0, SEC), text = "first")
        val second = textOverlay(id = "t2", range = TimeRange(SEC, 2 * SEC), text = "second")
        session.add(first)
        session.add(second)

        session.update(second.copy(id = first.id))

        assertEquals(
            listOf(second.copy(id = "t1"), second),
            session.overlays.value,
        )
    }

    @Test
    fun normalizeOutputOverlays_R12_boundary_rangeEntirelyBeforeZeroIsDropped() {
        val result = EditPlanner.normalizeOutputOverlays(
            outputDurationUs = 10 * SEC,
            overlays = listOf(textOverlay(id = "t1", range = TimeRange(-3 * SEC, -1 * SEC))),
        )

        assertEquals(emptyList<OverlaySpec>(), result)
    }

    private fun textOverlay(
        id: String = "t",
        range: TimeRange = TimeRange(0, SEC),
        text: String = "hello",
        style: TextOverlayStyle = TextOverlayStyle.Default,
    ): TextOverlay = TextOverlay(id = id, range = range, text = text, style = style)

    private fun imageOverlay(
        id: String = "i",
        range: TimeRange = TimeRange(0, SEC),
        source: ImageSource = ImageSource("content://media/1"),
        transform: OverlayTransform = OverlayTransform(),
    ): ImageOverlay = ImageOverlay(id = id, range = range, source = source, transform = transform)
}
