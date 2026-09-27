package com.doggy.clip_manager.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.model.ImageAsset
import com.doggy.clip_manager.core.model.ImageSource
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

// Verification obligation V1 of spec ad27e5f2d8c95388-editor-toolbox-fullscreen-style-restore.md v1:
// state transition technique, 8 transitions (C1-C7), 100% coverage.
@UnstableApi
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35])
class EditorToolboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun newViewModel() = EditorViewModel(SavedStateHandle())

    // C1: 텍스트·이미지 오버레이 추가 후 이미지 stow -> stowedOverlayIds={이미지 id}, overlays와 exportSpec().overlays에 둘 다 존재

    @Test
    fun stow_obligationV1_normal_case1_addedThenStowedOverlayStaysInOverlaysAndExportSpec() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addTextOverlay("hello")
        viewModel.addImageOverlay(ImageAsset(ImageSource("content://img"), "img"))
        val imageId = viewModel.overlays.value.filterIsInstance<ImageOverlay>().first().id

        viewModel.stow(imageId)

        assertEquals(setOf(imageId), viewModel.stowedOverlayIds)
        assertTrue(viewModel.overlays.value.any { it.id == imageId })
        val spec = requireNotNull(viewModel.exportSpec())
        assertTrue(spec.effects.overlays.any { it.id == imageId })
    }

    // C2: 선택된 오버레이를 stow -> selectedOverlayId == null

    @Test
    fun stow_obligationV1_normal_case2_stowingTheSelectedOverlayClearsTheSelection() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addTextOverlay("hello")
        val textId = viewModel.overlays.value.first().id
        viewModel.select(textId)
        assertEquals(textId, viewModel.selectedOverlayId)

        viewModel.stow(textId)

        assertNull(viewModel.selectedOverlayId)
    }

    // C3: 도구함 오버레이 select(id) -> 선택 변화 없음

    @Test
    fun select_obligationV1_normal_case3_selectingAStowedOverlayIsIgnored() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addTextOverlay("hello")
        viewModel.addImageOverlay(ImageAsset(ImageSource("content://img"), "img"))
        val textId = viewModel.overlays.value.filterIsInstance<TextOverlay>().first().id
        val imageId = viewModel.overlays.value.filterIsInstance<ImageOverlay>().first().id
        viewModel.select(textId)
        viewModel.stow(imageId)

        viewModel.select(imageId)

        assertEquals(textId, viewModel.selectedOverlayId)
    }

    // C4: unstow(id) -> 도구함에서 빠지고 selectedOverlayId == id

    @Test
    fun unstow_obligationV1_normal_case4_removesFromToolboxAndSelectsTheOverlay() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addImageOverlay(ImageAsset(ImageSource("content://img"), "img"))
        val imageId = viewModel.overlays.value.first().id
        viewModel.stow(imageId)

        viewModel.unstow(imageId)

        assertFalse(viewModel.stowedOverlayIds.contains(imageId))
        assertEquals(imageId, viewModel.selectedOverlayId)
    }

    // C5: 도구함 오버레이 remove(id) -> overlays와 도구함 모두에서 사라짐

    @Test
    fun remove_obligationV1_edge_case5_removingAStowedOverlayDropsItFromOverlaysAndToolbox() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addImageOverlay(ImageAsset(ImageSource("content://img"), "img"))
        val imageId = viewModel.overlays.value.first().id
        viewModel.stow(imageId)

        viewModel.remove(imageId)

        assertTrue(viewModel.overlays.value.none { it.id == imageId })
        assertFalse(viewModel.stowedOverlayIds.contains(imageId))
    }

    // C6: 도구함이 있는 상태에서 다른 path로 begin -> 도구함 빈 집합

    @Test
    fun begin_obligationV1_edge_case6_openingADifferentFileEmptiesTheToolbox() {
        val viewModel = newViewModel()
        viewModel.begin("/a.mp4", 10 * SEC)
        viewModel.addImageOverlay(ImageAsset(ImageSource("content://img"), "img"))
        val imageId = viewModel.overlays.value.first().id
        viewModel.stow(imageId)
        assertEquals(setOf(imageId), viewModel.stowedOverlayIds)

        viewModel.begin("/b.mp4", 10 * SEC)

        assertTrue(viewModel.stowedOverlayIds.isEmpty())
    }

    // C7 (stow branch): 없는 id로 stow -> 상태 불변, 예외 없음

    @Test
    fun stow_obligationV1_error_case7_anUnknownIdIsIgnoredWithoutChangingState() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addTextOverlay("hello")
        val textId = viewModel.overlays.value.first().id
        viewModel.select(textId)
        val stowedBefore = viewModel.stowedOverlayIds
        val selectedBefore = viewModel.selectedOverlayId
        val overlaysBefore = viewModel.overlays.value

        viewModel.stow("does-not-exist")

        assertEquals(stowedBefore, viewModel.stowedOverlayIds)
        assertEquals(selectedBefore, viewModel.selectedOverlayId)
        assertEquals(overlaysBefore, viewModel.overlays.value)
    }

    // C7 (unstow branch): 없는 id로 unstow -> 상태 불변, 예외 없음

    @Test
    fun unstow_obligationV1_error_case7_anUnknownIdIsIgnoredWithoutChangingState() {
        val viewModel = newViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.addImageOverlay(ImageAsset(ImageSource("content://img"), "img"))
        val imageId = viewModel.overlays.value.first().id
        viewModel.stow(imageId)
        val stowedBefore = viewModel.stowedOverlayIds
        val selectedBefore = viewModel.selectedOverlayId
        val overlaysBefore = viewModel.overlays.value

        viewModel.unstow("does-not-exist")

        assertEquals(stowedBefore, viewModel.stowedOverlayIds)
        assertEquals(selectedBefore, viewModel.selectedOverlayId)
        assertEquals(overlaysBefore, viewModel.overlays.value)
    }
}
