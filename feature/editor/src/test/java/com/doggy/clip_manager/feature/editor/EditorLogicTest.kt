package com.doggy.clip_manager.feature.editor

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

    // contract E1: clampSelection

    @Test
    fun clampSelection_contractE1_normal_keepsAnInRangeSelection() {
        assertEquals(TimeRange(1 * SEC, 4 * SEC), clampSelection(10 * SEC, 1 * SEC, 4 * SEC))
    }

    @Test
    fun clampSelection_contractE1_edge_clampsBeyondDuration() {
        assertEquals(TimeRange(2 * SEC, 10 * SEC), clampSelection(10 * SEC, 2 * SEC, 99 * SEC))
    }

    @Test
    fun clampSelection_contractE1_edge_clampsBelowZero() {
        assertEquals(TimeRange(0L, 4 * SEC), clampSelection(10 * SEC, -5 * SEC, 4 * SEC))
    }

    @Test
    fun clampSelection_contractE1_edge_aFullyNegativeRangeBecomesTheMinimumAtZero() {
        assertEquals(TimeRange(0L, MIN_SELECTION_US), clampSelection(10 * SEC, -5 * SEC, -1 * SEC))
    }

    @Test
    fun clampSelection_contractE1_edge_enforcesMinimumLengthOnAnInvertedRange() {
        val result = clampSelection(10 * SEC, 5 * SEC, 1 * SEC)

        assertEquals(5 * SEC, result.startUs)
        assertEquals(5 * SEC + MIN_SELECTION_US, result.endUs)
    }

    @Test
    fun clampSelection_contractE1_edge_durationShorterThanMinimumCollapsesToWholeInput() {
        assertEquals(TimeRange(0L, 1_000L), clampSelection(1_000L, 0L, 1_000L))
    }

    @Test
    fun clampSelection_contractE1_edge_startPastTheLastValidStartIsPulledBack() {
        val result = clampSelection(10 * SEC, 10 * SEC, 10 * SEC)

        assertEquals(10 * SEC - MIN_SELECTION_US, result.startUs)
        assertEquals(10 * SEC, result.endUs)
    }

    // contract E2: toOutputOverlays maps the source timeline onto the kept spans

    @Test
    fun toOutputOverlays_contractE2_normal_shiftsSourceTimeByTheCutBeforeIt() {
        val overlay = text("a", TimeRange(4 * SEC, 6 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), listOf(overlay))

        assertEquals(listOf(text("a", TimeRange(1 * SEC, 3 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_contractE2_normal_clipsToTheKeptSpan() {
        val overlay = text("a", TimeRange(0L, 10 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), listOf(overlay))

        assertEquals(listOf(text("a", TimeRange(0L, 5 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_contractE2_edge_dropsAnOverlayEntirelyInsideTheCut() {
        val overlay = text("a", TimeRange(0L, 2 * SEC))

        assertTrue(toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), listOf(overlay)).isEmpty())
    }

    @Test
    fun toOutputOverlays_contractE2_boundary_anOverlayEndingAtTheKeptSpanStartIsDropped() {
        val overlay = text("a", TimeRange(2 * SEC, 3 * SEC))

        assertTrue(toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), listOf(overlay)).isEmpty())
    }

    @Test
    fun toOutputOverlays_contractE2_boundary_anOverlayStartingAtTheKeptSpanEndIsDropped() {
        val overlay = text("a", TimeRange(3 * SEC, 3 * SEC + 1))

        assertTrue(toOutputOverlays(listOf(TimeRange(0L, 3 * SEC)), listOf(overlay)).isEmpty())
    }

    @Test
    fun toOutputOverlays_contractE2_edge_noKeptSpanDropsEveryOverlay() {
        assertTrue(toOutputOverlays(emptyList(), listOf(text("a", TimeRange(0L, 10 * SEC)))).isEmpty())
    }

    // contract E3 / decision G2: a split overlay becomes one overlay per span with unique ids

    @Test
    fun toOutputOverlays_contractE3_edge_aSplitOverlayGetsDistinctIdsPerSpan() {
        val overlay = image("a", TimeRange(0L, 10 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(0L, 2 * SEC), TimeRange(5 * SEC, 6 * SEC)), listOf(overlay))

        assertEquals(listOf("a", "a#1"), result.map { it.id })
        assertEquals(listOf(TimeRange(0L, 2 * SEC), TimeRange(2 * SEC, 3 * SEC)), result.map { it.range })
    }

    @Test
    fun toOutputOverlays_contractE3_edge_aThreeWaySplitKeepsEveryIdUnique() {
        val overlay = text("a", TimeRange(0L, 20 * SEC))
        val keeps = listOf(TimeRange(0L, 2 * SEC), TimeRange(5 * SEC, 6 * SEC), TimeRange(10 * SEC, 14 * SEC))

        val result = toOutputOverlays(keeps, listOf(overlay))

        assertEquals(listOf("a", "a#1", "a#2"), result.map { it.id })
        assertEquals(3, result.map { it.id }.toSet().size)
        assertEquals(
            listOf(TimeRange(0L, 2 * SEC), TimeRange(2 * SEC, 3 * SEC), TimeRange(3 * SEC, 7 * SEC)),
            result.map { it.range },
        )
    }

    // contract E4: editorExportSpec

    @Test
    fun editorExportSpec_contractE4_normal_carriesTheSelectionAsTheOnlyKeepRange() {
        val spec = editorExportSpec("/in.mp4", TimeRange(1 * SEC, 5 * SEC), CutMode.PRECISE, listOf(text("a", TimeRange(2 * SEC, 3 * SEC))))

        assertEquals(listOf(TimeRange(1 * SEC, 5 * SEC)), spec.keepRanges)
        assertEquals("/in.mp4", spec.inputPath)
        assertEquals(CutMode.PRECISE, spec.cutMode)
        assertEquals(listOf(TimeRange(1 * SEC, 2 * SEC)), spec.effects.overlays.map { it.range })
    }

    @Test
    fun editorExportSpec_contractE4_normal_carriesTheChosenFastCutMode() {
        val spec = editorExportSpec("/in.mp4", TimeRange(1 * SEC, 5 * SEC), CutMode.FAST, emptyList())

        assertEquals(CutMode.FAST, spec.cutMode)
        assertTrue(spec.effects.overlays.isEmpty())
    }

    @Test
    fun editorExportSpec_contractE4_edge_dropsAnOverlayOutsideTheSelection() {
        val spec = editorExportSpec("/in.mp4", TimeRange(1 * SEC, 5 * SEC), CutMode.PRECISE, listOf(text("a", TimeRange(6 * SEC, 7 * SEC))))

        assertTrue(spec.effects.overlays.isEmpty())
    }

    private fun text(id: String, range: TimeRange) = TextOverlay(id = id, range = range, text = "t")

    private fun image(id: String, range: TimeRange) =
        ImageOverlay(id = id, range = range, source = ImageSource("content://x"))
}
