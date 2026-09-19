package com.doggy.clip_manager.feature.player

import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.model.ImageSource
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.editor.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEC = 1_000_000L

class EditorLogicTest {

    // clampSelection

    @Test
    fun clampSelection_E1_normal_keepsAnInRangeSelection() {
        assertEquals(TimeRange(1 * SEC, 4 * SEC), clampSelection(10 * SEC, 1 * SEC, 4 * SEC))
    }

    @Test
    fun clampSelection_E2_edge_clampsBeyondDuration() {
        assertEquals(TimeRange(2 * SEC, 10 * SEC), clampSelection(10 * SEC, 2 * SEC, 99 * SEC))
    }

    @Test
    fun clampSelection_E3_edge_enforcesMinimumLengthOnAnInvertedRange() {
        val result = clampSelection(10 * SEC, 5 * SEC, 1 * SEC)

        assertEquals(5 * SEC, result.startUs)
        assertEquals(5 * SEC + MIN_SELECTION_US, result.endUs)
    }

    @Test
    fun clampSelection_E4_edge_durationShorterThanMinimumCollapsesToWholeInput() {
        assertEquals(TimeRange(0L, 1_000L), clampSelection(1_000L, 0L, 1_000L))
    }

    @Test
    fun clampSelection_E5_edge_startPastTheLastValidStartIsPulledBack() {
        val result = clampSelection(10 * SEC, 10 * SEC, 10 * SEC)

        assertEquals(10 * SEC - MIN_SELECTION_US, result.startUs)
        assertEquals(10 * SEC, result.endUs)
    }

    // toOutputOverlays

    @Test
    fun toOutputOverlays_E6_normal_shiftsSourceTimeByTheCutBeforeIt() {
        val overlay = text("a", TimeRange(4 * SEC, 6 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), listOf(overlay))

        assertEquals(listOf(text("a", TimeRange(1 * SEC, 3 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_E7_normal_clipsToTheKeptSpan() {
        val overlay = text("a", TimeRange(0L, 10 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), listOf(overlay))

        assertEquals(listOf(text("a", TimeRange(0L, 5 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_E8_edge_dropsAnOverlayEntirelyInsideTheCut() {
        val overlay = text("a", TimeRange(0L, 2 * SEC))

        assertTrue(toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), listOf(overlay)).isEmpty())
    }

    @Test
    fun toOutputOverlays_E9_edge_aSplitOverlayGetsDistinctIdsPerSpan() {
        val overlay = image("a", TimeRange(0L, 10 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(0L, 2 * SEC), TimeRange(5 * SEC, 6 * SEC)), listOf(overlay))

        assertEquals(listOf("a", "a#1"), result.map { it.id })
        assertEquals(listOf(TimeRange(0L, 2 * SEC), TimeRange(2 * SEC, 3 * SEC)), result.map { it.range })
    }

    @Test
    fun toOutputOverlays_E10_boundary_anOverlayTouchingTheCutEdgeIsDropped() {
        val overlay = text("a", TimeRange(3 * SEC, 3 * SEC + 1))

        assertTrue(toOutputOverlays(listOf(TimeRange(0L, 3 * SEC)), listOf(overlay)).isEmpty())
    }

    // editorExportSpec

    @Test
    fun editorExportSpec_E11_normal_carriesTheSelectionAsTheOnlyKeepRange() {
        val spec = editorExportSpec("/in.mp4", TimeRange(1 * SEC, 5 * SEC), CutMode.PRECISE, listOf(text("a", TimeRange(2 * SEC, 3 * SEC))))

        assertEquals(listOf(TimeRange(1 * SEC, 5 * SEC)), spec.keepRanges)
        assertEquals(CutMode.PRECISE, spec.cutMode)
        assertEquals(listOf(TimeRange(1 * SEC, 2 * SEC)), spec.effects.overlays.map { it.range })
    }

    private fun text(id: String, range: TimeRange) = TextOverlay(id = id, range = range, text = "t")

    private fun image(id: String, range: TimeRange) =
        ImageOverlay(id = id, range = range, source = ImageSource("content://x"))
}
