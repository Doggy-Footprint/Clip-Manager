package com.doggy.clip_manager.feature.editor

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.TimeRange
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEC = 1_000_000L

@UnstableApi
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35])
class EditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // contract E4

    @Test
    fun exportSpec_contractE4_normal_handsTheSelectionAndOverlaysToTheExportSpec() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(1 * SEC, 5 * SEC)
        viewModel.chooseCutMode(CutMode.FAST)
        viewModel.addTextOverlay("hello")

        val spec = requireNotNull(viewModel.exportSpec())

        assertEquals("/in.mp4", spec.inputPath)
        assertEquals(listOf(TimeRange(1 * SEC, 5 * SEC)), spec.keepRanges)
        assertEquals(CutMode.FAST, spec.cutMode)
        assertEquals(listOf(TimeRange(0L, 4 * SEC)), spec.effects.overlays.map { it.range })
    }

    @Test
    fun exportSpec_contractE4_edge_isNullWithoutAnInput() {
        assertNull(EditorViewModel().exportSpec())
    }

    // decision G4

    @Test
    fun addTextOverlay_decisionG4_normal_theDefaultTextIsNotBlank() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(context.getString(R.string.feature_editor_text_default).isNotBlank())
    }
}
