package com.doggy.clip_manager.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEC = 1_000_000L

/**
 * Verification obligation V4 of spec ad27e5f2d8c95388-editor-toolbox-fullscreen-style-restore.md v1:
 * boundary value analysis (2-value) + equivalence partitioning over TEXT_PALETTE, backgroundOf,
 * and update->exportSpec text style (C11, C12). Expected values come from the spec's A5 palette
 * decision and the literal formula in the Signatures section
 * (`backgroundOf(colorArgb) == (colorArgb and 0xffffff) or 0x80000000`), never from the
 * implementation under test.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35])
class EditorTextStyleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun newViewModel() = EditorViewModel(SavedStateHandle())

    // C12: TEXT_PALETTE has 8 opaque, unique entries including white and black

    @Test
    fun TEXT_PALETTE_obligationV4_boundary_case12_hasEightOpaqueUniqueColorsIncludingWhiteAndBlack() {
        assertEquals(8, TEXT_PALETTE.size)
        assertTrue("every palette color must be fully opaque", TEXT_PALETTE.all { (it and 0xff000000L) == 0xff000000L })
        assertEquals("palette colors must be unique", TEXT_PALETTE.size, TEXT_PALETTE.toSet().size)
        assertTrue(TEXT_PALETTE.contains(0xffffffffL))
        assertTrue(TEXT_PALETTE.contains(0xff000000L))
    }

    // C12: backgroundOf keeps the RGB and forces alpha 0x80, checked against the literal formula

    @Test
    fun backgroundOf_obligationV4_boundary_case12_appliesAlpha0x80ToTheSameRgbOnALiteralInput() {
        assertEquals(0x80123456L, backgroundOf(0xff123456L))
    }

    @Test
    fun backgroundOf_obligationV4_boundary_case12_matchesTheFormulaOnTheFirstAndLastPaletteEntries() {
        val first = TEXT_PALETTE.first()
        val last = TEXT_PALETTE.last()

        assertEquals((first and 0xffffffL) or 0x80000000L, backgroundOf(first))
        assertEquals((last and 0xffffffL) or 0x80000000L, backgroundOf(last))
    }

    // C11: palette color + non-null background (via backgroundOf) + left alignment reach exportSpec

    @Test
    fun update_obligationV4_normal_case11_paletteColorNonNullBackgroundAndLeftAlignReflectInExportSpec() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addTextOverlay("hello")
        val overlay = viewModel.overlays.value.first() as TextOverlay
        val chosenColor = TEXT_PALETTE[2]
        val chosenBackground = backgroundOf(TEXT_PALETTE[5])

        viewModel.update(
            overlay.copy(
                style = overlay.style.copy(
                    colorArgb = chosenColor,
                    backgroundArgb = chosenBackground,
                    centerAligned = false,
                ),
            ),
        )

        val style = ((requireNotNull(viewModel.exportSpec()).effects.overlays.first()) as TextOverlay).style
        assertEquals(chosenColor, style.colorArgb)
        assertEquals(chosenBackground, style.backgroundArgb)
        assertFalse(style.centerAligned)
    }

    // C11 boundary (background = null): exportSpec keeps backgroundArgb null (no background)

    @Test
    fun update_obligationV4_boundary_nullBackgroundReflectsAsNullInExportSpec() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addTextOverlay("hello")
        val overlay = viewModel.overlays.value.first() as TextOverlay

        viewModel.update(overlay.copy(style = overlay.style.copy(backgroundArgb = null, centerAligned = false)))

        val style = ((requireNotNull(viewModel.exportSpec()).effects.overlays.first()) as TextOverlay).style
        assertNull(style.backgroundArgb)
    }

    // C11 boundary (centerAligned = true): exportSpec reflects the centered alignment

    @Test
    fun update_obligationV4_boundary_centerAlignedTrueReflectsInExportSpec() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addTextOverlay("hello")
        val overlay = viewModel.overlays.value.first() as TextOverlay

        viewModel.update(overlay.copy(style = overlay.style.copy(colorArgb = TEXT_PALETTE[0], centerAligned = true)))

        val style = ((requireNotNull(viewModel.exportSpec()).effects.overlays.first()) as TextOverlay).style
        assertTrue(style.centerAligned)
    }
}
