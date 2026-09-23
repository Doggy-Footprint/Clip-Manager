package com.doggy.clip_manager.feature.editor

import com.doggy.clip_manager.core.editor.ConcatStrategy
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.FlipRange
import com.doggy.clip_manager.core.editor.FrameLayout
import com.doggy.clip_manager.core.editor.FrameMode
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.NormalizedPoint
import com.doggy.clip_manager.core.model.ImageSource
import com.doggy.clip_manager.core.editor.SpeedRange
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

        val result = toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), emptyList(), listOf(overlay))

        assertEquals(listOf(text("a", TimeRange(1 * SEC, 3 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_contractE2_normal_clipsToTheKeptSpan() {
        val overlay = text("a", TimeRange(0L, 10 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), emptyList(), listOf(overlay))

        assertEquals(listOf(text("a", TimeRange(0L, 5 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_contractE2_edge_dropsAnOverlayEntirelyInsideTheCut() {
        val overlay = text("a", TimeRange(0L, 2 * SEC))

        assertTrue(toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), emptyList(), listOf(overlay)).isEmpty())
    }

    @Test
    fun toOutputOverlays_contractE2_boundary_anOverlayEndingAtTheKeptSpanStartIsDropped() {
        val overlay = text("a", TimeRange(2 * SEC, 3 * SEC))

        assertTrue(toOutputOverlays(listOf(TimeRange(3 * SEC, 8 * SEC)), emptyList(), listOf(overlay)).isEmpty())
    }

    @Test
    fun toOutputOverlays_contractE2_boundary_anOverlayStartingAtTheKeptSpanEndIsDropped() {
        val overlay = text("a", TimeRange(3 * SEC, 3 * SEC + 1))

        assertTrue(toOutputOverlays(listOf(TimeRange(0L, 3 * SEC)), emptyList(), listOf(overlay)).isEmpty())
    }

    @Test
    fun toOutputOverlays_contractE2_edge_noKeptSpanDropsEveryOverlay() {
        assertTrue(toOutputOverlays(emptyList(), emptyList(), listOf(text("a", TimeRange(0L, 10 * SEC)))).isEmpty())
    }

    // contract E3 / decision G2: a split overlay becomes one overlay per span with unique ids

    @Test
    fun toOutputOverlays_contractE3_edge_aSplitOverlayGetsDistinctIdsPerSpan() {
        val overlay = image("a", TimeRange(0L, 10 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(0L, 2 * SEC), TimeRange(5 * SEC, 6 * SEC)), emptyList(), listOf(overlay))

        assertEquals(listOf("a", "a#1"), result.map { it.id })
        assertEquals(listOf(TimeRange(0L, 2 * SEC), TimeRange(2 * SEC, 3 * SEC)), result.map { it.range })
    }

    @Test
    fun toOutputOverlays_contractE3_edge_aThreeWaySplitKeepsEveryIdUnique() {
        val overlay = text("a", TimeRange(0L, 20 * SEC))
        val keeps = listOf(TimeRange(0L, 2 * SEC), TimeRange(5 * SEC, 6 * SEC), TimeRange(10 * SEC, 14 * SEC))

        val result = toOutputOverlays(keeps, emptyList(), listOf(overlay))

        assertEquals(listOf("a", "a#1", "a#2"), result.map { it.id })
        assertEquals(3, result.map { it.id }.toSet().size)
        assertEquals(
            listOf(TimeRange(0L, 2 * SEC), TimeRange(2 * SEC, 3 * SEC), TimeRange(3 * SEC, 7 * SEC)),
            result.map { it.range },
        )
    }

    // contract E4: editorExportSpec (migrated to the 7-arg signature; flips/speeds default to empty
    // to keep the pre-existing effects-free behavior unchanged)

    @Test
    fun editorExportSpec_contractE4_normal_carriesTheSelectionAsTheOnlyKeepRange() {
        val spec = editorExportSpec(
            "/in.mp4", TimeRange(1 * SEC, 5 * SEC), CutMode.PRECISE,
            listOf(text("a", TimeRange(2 * SEC, 3 * SEC))),
            FrameLayout.Original, emptyList(), emptyList(),
        )

        assertEquals(listOf(TimeRange(1 * SEC, 5 * SEC)), spec.keepRanges)
        assertEquals("/in.mp4", spec.inputPath)
        assertEquals(CutMode.PRECISE, spec.cutMode)
        assertEquals(listOf(TimeRange(1 * SEC, 2 * SEC)), spec.effects.overlays.map { it.range })
    }

    @Test
    fun editorExportSpec_contractE4_normal_carriesTheChosenFastCutMode() {
        val spec = editorExportSpec(
            "/in.mp4", TimeRange(1 * SEC, 5 * SEC), CutMode.FAST, emptyList(),
            FrameLayout.Original, emptyList(), emptyList(),
        )

        assertEquals(CutMode.FAST, spec.cutMode)
        assertTrue(spec.effects.overlays.isEmpty())
    }

    @Test
    fun editorExportSpec_contractE4_edge_dropsAnOverlayOutsideTheSelection() {
        val spec = editorExportSpec(
            "/in.mp4", TimeRange(1 * SEC, 5 * SEC), CutMode.PRECISE,
            listOf(text("a", TimeRange(6 * SEC, 7 * SEC))),
            FrameLayout.Original, emptyList(), emptyList(),
        )

        assertTrue(spec.effects.overlays.isEmpty())
    }

    // obligation V1 / F1 / C1,C2: frameLayoutOf is exhaustively checked over all 5 presets x 3 modes

    @Test
    fun frameLayoutOf_obligationV1_normal_case1_wideCropMakesA16x9RatioWithCenteredCrop() {
        assertEquals(
            FrameLayout.Ratio(16, 9, FrameMode.CROP, NormalizedPoint(0.5f, 0.5f)),
            frameLayoutOf(RatioPreset.WIDE, FrameMode.CROP),
        )
    }

    @Test
    fun frameLayoutOf_obligationV1_edge_case2_originalIgnoresTheChosenMode() {
        assertEquals(FrameLayout.Original, frameLayoutOf(RatioPreset.ORIGINAL, FrameMode.FIT))
    }

    @Test
    fun frameLayoutOf_obligationV1_normal_everyPresetTimesEveryModeMatchesF1() {
        for (preset in RatioPreset.values()) {
            for (mode in FrameMode.values()) {
                val expected = if (preset == RatioPreset.ORIGINAL) {
                    FrameLayout.Original
                } else {
                    FrameLayout.Ratio(preset.width, preset.height, mode, NormalizedPoint(0.5f, 0.5f))
                }

                assertEquals("preset=$preset mode=$mode", expected, frameLayoutOf(preset, mode))
            }
        }
    }

    // obligation V2 / F7,Q1 / C3-C6: toOutputOverlays with speeds, expected µs computed by hand from F7:
    // outputTime(t) = sum of floor(pieceLenUs / speed) over every keep-intersect-speed piece before t
    // (unspecified speed treated as 1x), pieces from different keep spans are NOT connected by any gap.

    @Test
    fun toOutputOverlays_obligationV2_boundary_case3_aFasterSpeedShrinksTheOverlappingSpan() {
        val speeds = listOf(SpeedRange(TimeRange(0L, 4 * SEC), 2f))
        val overlay = text("a", TimeRange(2 * SEC, 6 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(0L, 10 * SEC)), speeds, listOf(overlay))

        // t=2s: the sub-piece before it inside the 2x piece is [0,2s] (2s long) -> floor(2_000_000/2)=1_000_000
        // t=6s: full 2x piece (4s/2=2s) + 2s of the untouched remainder (1x) -> 2s+2s=4s
        assertEquals(listOf(text("a", TimeRange(1 * SEC, 4 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_obligationV2_boundary_case4_aSlowerSpeedGrowsTheOverlappingSpan() {
        val speeds = listOf(SpeedRange(TimeRange(0L, 2 * SEC), 0.5f))
        val overlay = text("a", TimeRange(0L, 2 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(0L, 10 * SEC)), speeds, listOf(overlay))

        // t=0 -> 0; t=2s: whole 0.5x piece -> floor(2_000_000/0.5)=4_000_000
        assertEquals(listOf(text("a", TimeRange(0L, 4 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_obligationV2_boundary_case5_theKeepStartOffsetsTheSpeedPiece() {
        val speeds = listOf(SpeedRange(TimeRange(0L, 4 * SEC), 2f))
        val overlay = text("a", TimeRange(3 * SEC, 5 * SEC))

        val result = toOutputOverlays(listOf(TimeRange(2 * SEC, 10 * SEC)), speeds, listOf(overlay))

        // speed piece intersected with keep is [2s,4s]@2x, remainder [4s,10s]@1x
        // t=3s: 1s of the 2x piece before it -> floor(1_000_000/2)=500_000
        // t=5s: whole 2x piece (2s/2=1s) + 1s of the 1x remainder -> 1s+1s=2s
        assertEquals(listOf(text("a", TimeRange(500_000L, 2 * SEC))), result)
    }

    @Test
    fun toOutputOverlays_obligationV2_edge_case6_splitAcrossKeepsWithoutCountingTheCutGap() {
        val speeds = listOf(SpeedRange(TimeRange(0L, 6 * SEC), 2f))
        val keeps = listOf(TimeRange(0L, 2 * SEC), TimeRange(4 * SEC, 6 * SEC))
        val overlay = text("a", TimeRange(1 * SEC, 5 * SEC))

        val result = toOutputOverlays(keeps, speeds, listOf(overlay))

        // first keep [0,2s]@2x: t=1s -> floor(1_000_000/2)=500_000; t=2s -> floor(2_000_000/2)=1_000_000
        // second keep [4,6s]@2x: the cut [2,4s] contributes nothing.
        // t=4s -> 1_000_000 (from first keep only); t=5s -> 1_000_000 + floor(1_000_000/2)=1_500_000
        assertEquals(
            listOf(text("a", TimeRange(500_000L, 1_000_000L)), text("a#1", TimeRange(1_000_000L, 1_500_000L))),
            result,
        )
    }

    // obligation V4 / F6 / C8: editorExportSpec's 7-arg signature carries frameLayout/flips/speeds on the
    // original timeline unchanged, and overlays through toOutputOverlays(keepRanges, speeds, overlays).

    @Test
    fun editorExportSpec_obligationV4_normal_case8_carriesFrameLayoutFlipsSpeedsAndTransformedOverlays() {
        val frameLayout = FrameLayout.Ratio(16, 9, FrameMode.FIT, NormalizedPoint(0.5f, 0.5f))
        val flips = listOf(FlipRange(TimeRange(1 * SEC, 3 * SEC), horizontal = true, vertical = false))
        val speeds = listOf(SpeedRange(TimeRange(0L, 2 * SEC), 1.5f))
        val overlay = text("o", TimeRange(0L, 4 * SEC))

        val spec = editorExportSpec(
            "/in.mp4", TimeRange(0L, 4 * SEC), CutMode.PRECISE, listOf(overlay), frameLayout, flips, speeds,
        )

        assertEquals("/in.mp4", spec.inputPath)
        assertEquals(listOf(TimeRange(0L, 4 * SEC)), spec.keepRanges)
        assertEquals(CutMode.PRECISE, spec.cutMode)
        assertEquals(frameLayout, spec.effects.frameLayout)
        assertEquals(flips, spec.effects.flips)
        assertEquals(speeds, spec.effects.speeds)
        // t=0 -> 0; t=4s: floor(2_000_000/1.5)=1_333_333 (1.5x piece) + 2_000_000 (untouched remainder) = 3_333_333
        assertEquals(listOf(text("o", TimeRange(0L, 3_333_333L))), spec.effects.overlays)
    }

    // obligation V12 / F11 / C16,C17: editorExportSpec always carries concatStrategy = SEGMENT_CONCAT,
    // regardless of whether any effect/overlay is present.

    @Test
    fun editorExportSpec_obligationV12_normal_case16_effectsInputStillUsesSegmentConcat() {
        val frameLayout = FrameLayout.Ratio(16, 9, FrameMode.FIT, NormalizedPoint(0.5f, 0.5f))
        val flips = listOf(FlipRange(TimeRange(1 * SEC, 3 * SEC), horizontal = true, vertical = false))
        val speeds = listOf(SpeedRange(TimeRange(0L, 2 * SEC), 1.5f))
        val overlay = text("o", TimeRange(0L, 4 * SEC))

        val spec = editorExportSpec(
            "/in.mp4", TimeRange(0L, 4 * SEC), CutMode.PRECISE, listOf(overlay), frameLayout, flips, speeds,
        )

        assertEquals(ConcatStrategy.SEGMENT_CONCAT, spec.concatStrategy)
    }

    @Test
    fun editorExportSpec_obligationV12_normal_case16_singleOverlayOnlyStillUsesSegmentConcat() {
        val overlay = text("a", TimeRange(0L, 4 * SEC))

        val spec = editorExportSpec(
            "/in.mp4", TimeRange(0L, 4 * SEC), CutMode.PRECISE, listOf(overlay),
            FrameLayout.Original, emptyList(), emptyList(),
        )

        assertEquals(ConcatStrategy.SEGMENT_CONCAT, spec.concatStrategy)
    }

    @Test
    fun editorExportSpec_obligationV12_normal_case16_frameLayoutOnlyStillUsesSegmentConcat() {
        val frameLayout = FrameLayout.Ratio(16, 9, FrameMode.CROP, NormalizedPoint(0.5f, 0.5f))

        val spec = editorExportSpec(
            "/in.mp4", TimeRange(0L, 4 * SEC), CutMode.PRECISE, emptyList(),
            frameLayout, emptyList(), emptyList(),
        )

        assertEquals(ConcatStrategy.SEGMENT_CONCAT, spec.concatStrategy)
    }

    @Test
    fun editorExportSpec_obligationV12_edge_case17_noEffectsOrOverlaysStillUsesSegmentConcat() {
        val spec = editorExportSpec(
            "/in.mp4", TimeRange(0L, 4 * SEC), CutMode.PRECISE, emptyList(),
            FrameLayout.Original, emptyList(), emptyList(),
        )

        assertEquals(ConcatStrategy.SEGMENT_CONCAT, spec.concatStrategy)
    }

    private fun text(id: String, range: TimeRange) = TextOverlay(id = id, range = range, text = "t")

    private fun image(id: String, range: TimeRange) =
        ImageOverlay(id = id, range = range, source = ImageSource("content://x"))
}
