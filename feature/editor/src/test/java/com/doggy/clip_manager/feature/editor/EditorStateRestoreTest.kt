package com.doggy.clip_manager.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.FrameMode
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.OverlayTransform
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.editor.TextOverlayStyle
import com.doggy.clip_manager.core.model.ImageAsset
import com.doggy.clip_manager.core.model.ImageSource
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEC = 1_000_000L

/**
 * Verification obligation V2 of spec ad27e5f2d8c95388-editor-toolbox-fullscreen-style-restore.md v1:
 * equivalence partitioning over the 13 F8 fields (each set to a non-default value in its own
 * isolated test, per the spec's coverage-item list) plus the 2 overlay-kind partitions (every
 * TextOverlayStyle field non-default, every OverlayTransform field non-default), plus C13
 * (fullscreen toggle survives restore). Each restored `EditorViewModel` is built from a brand
 * new `SavedStateHandle` copied from the original handle's keys()/get() values (never the same
 * instance), per V2's explicit "no instance reuse" requirement.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35])
class EditorStateRestoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun restoredFrom(handle: SavedStateHandle) = EditorViewModel(copySavedStateHandle(handle))

    private fun assertFullyRestored(original: EditorViewModel, restored: EditorViewModel) {
        assertEquals(original.editing, restored.editing)
        assertEquals(original.path, restored.path)
        assertEquals(original.durationUs, restored.durationUs)
        assertEquals(original.selection, restored.selection)
        assertEquals(original.cutMode, restored.cutMode)
        assertEquals(original.ratioPreset, restored.ratioPreset)
        assertEquals(original.frameMode, restored.frameMode)
        assertEquals(original.flips, restored.flips)
        assertEquals(original.speeds, restored.speeds)
        assertEquals(original.overlays.value, restored.overlays.value)
        assertEquals(original.stowedOverlayIds, restored.stowedOverlayIds)
        assertEquals(original.selectedOverlayId, restored.selectedOverlayId)
        assertEquals(original.fullscreen, restored.fullscreen)
        assertEquals(original.exportSpec(), restored.exportSpec())
    }

    // Field 1/13: editing (default false; begin() makes it true)

    @Test
    fun restore_obligationV2_normal_field01_editingNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 2/13: path (default null; begin() sets it)

    @Test
    fun restore_obligationV2_normal_field02_pathNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 3/13: durationUs (default 0; begin() sets it)

    @Test
    fun restore_obligationV2_normal_field03_durationUsNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 20 * SEC)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 4/13: selection

    @Test
    fun restore_obligationV2_normal_field04_selectionNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.setSelection(2 * SEC, 8 * SEC)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 5/13: cutMode (default PRECISE; FAST is non-default)

    @Test
    fun restore_obligationV2_normal_field05_cutModeNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.chooseCutMode(CutMode.FAST)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 6/13: ratioPreset (default ORIGINAL, confirmed by EditorEffectsViewModelTest)

    @Test
    fun restore_obligationV2_normal_field06_ratioPresetNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.chooseRatio(RatioPreset.WIDE)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 7/13: frameMode (default CROP, confirmed by EditorEffectsViewModelTest)

    @Test
    fun restore_obligationV2_normal_field07_frameModeNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.chooseFrameMode(FrameMode.FIT)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 8/13: flips (default empty list)

    @Test
    fun restore_obligationV2_normal_field08_flipsNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.setSelection(0L, 4 * SEC)
        original.addFlip(horizontal = true, vertical = false)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 9/13: speeds (default empty list)

    @Test
    fun restore_obligationV2_normal_field09_speedsNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.setSelection(0L, 4 * SEC)
        original.addSpeed(1.5f)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 10/13: overlays (default empty list)

    @Test
    fun restore_obligationV2_normal_field10_overlaysNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.addTextOverlay("hello")

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 11/13: stowedOverlayIds (default empty set)

    @Test
    fun restore_obligationV2_normal_field11_stowedOverlayIdsNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.addTextOverlay("hello")
        original.stow(original.overlays.value.first().id)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 12/13: selectedOverlayId (default null)

    @Test
    fun restore_obligationV2_normal_field12_selectedOverlayIdNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.addTextOverlay("hello")
        original.select(original.overlays.value.first().id)

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Field 13/13: fullscreen (default false)

    @Test
    fun restore_obligationV2_normal_field13_fullscreenNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.toggleFullscreen()

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
    }

    // Overlay-kind partition 1/2: every TextOverlayStyle field non-default

    @Test
    fun restore_obligationV2_normal_overlayKind1_everyTextOverlayStyleFieldNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.addTextOverlay("hello")
        val overlay = original.overlays.value.first() as TextOverlay
        val nonDefaultStyle = TextOverlayStyle(
            positionX = 0.2f,
            positionY = 0.3f,
            fontSizePt = 30f,
            colorArgb = 0xff000000,
            backgroundArgb = 0x80112233,
            centerAligned = false,
        )
        original.update(overlay.copy(text = "hi", style = nonDefaultStyle))

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
        assertEquals(nonDefaultStyle, (restored.overlays.value.first() as TextOverlay).style)
    }

    // Overlay-kind partition 2/2: every OverlayTransform field non-default

    @Test
    fun restore_obligationV2_normal_overlayKind2_everyOverlayTransformFieldNonDefaultIsRestored() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)
        original.begin("/a.mp4", 10 * SEC)
        original.addImageOverlay(ImageAsset(ImageSource("content://img"), "img"))
        val overlay = original.overlays.value.first() as ImageOverlay
        val nonDefaultTransform = OverlayTransform(
            positionX = 0.1f,
            positionY = 0.2f,
            scale = 2f,
            alpha = 0.5f,
            rotationDegrees = 45f,
        )
        original.update(overlay.copy(transform = nonDefaultTransform))

        val restored = restoredFrom(handle)

        assertFullyRestored(original, restored)
        assertEquals(nonDefaultTransform, (restored.overlays.value.first() as ImageOverlay).transform)
    }

    // C13: toggleFullscreen() twice returns to false, and that false survives restore

    @Test
    fun toggleFullscreen_obligationV2_normal_case13_twoTogglesReturnToFalseAndRestoreKeepsIt() {
        val handle = SavedStateHandle()
        val original = EditorViewModel(handle)

        original.toggleFullscreen()
        assertEquals(true, original.fullscreen)
        original.toggleFullscreen()

        assertFalse(original.fullscreen)
        val restored = restoredFrom(handle)
        assertFalse(restored.fullscreen)
        assertFullyRestored(original, restored)
    }
}
