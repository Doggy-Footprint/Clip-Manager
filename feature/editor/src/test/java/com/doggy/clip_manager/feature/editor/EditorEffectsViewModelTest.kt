package com.doggy.clip_manager.feature.editor

import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.editor.FlipRange
import com.doggy.clip_manager.core.editor.FrameMode
import com.doggy.clip_manager.core.editor.SpeedRange
import com.doggy.clip_manager.core.editor.TimeRange
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEC = 1_000_000L

// Verification Obligations V5-V8 of spec 547fb213daf79144-editor-effects-ui.md v1.
@UnstableApi
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35])
class EditorEffectsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // obligation V5 / F2,F4,Q3 / C9,C10,C11: addSpeed/updateSpeed overlap and step validation

    @Test
    fun speedSteps_obligationV5_normal_matchesTheSevenEngineAllowedSteps() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f), SPEED_STEPS)
    }

    @Test
    fun addSpeed_obligationV5_normal_everySpeedStepIsAccepted() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 1_000 * SEC)

        SPEED_STEPS.forEachIndexed { i, step ->
            viewModel.setSelection(i * 10 * SEC, i * 10 * SEC + 5 * SEC)

            assertTrue("speed=$step should be accepted", viewModel.addSpeed(step))
        }

        assertEquals(SPEED_STEPS, viewModel.speeds.map { it.speed })
    }

    @Test
    fun addSpeed_obligationV5_error_case11_aSpeedOutsideTheAllowedStepsIsRejected() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)

        val accepted = viewModel.addSpeed(3f)

        assertFalse(accepted)
        assertTrue(viewModel.speeds.isEmpty())
    }

    @Test
    fun updateSpeed_obligationV5_error_case11b_aSpeedOutsideTheAllowedStepsIsRejected() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)
        viewModel.addSpeed(2f)
        val before = viewModel.speeds

        val accepted = viewModel.updateSpeed(0, SpeedRange(TimeRange(0L, 4 * SEC), 3f))

        assertFalse(accepted)
        assertEquals(before, viewModel.speeds)
    }

    @Test
    fun addSpeedAndUpdateSpeed_obligationV5_error_case9_anOverlappingRangeIsRejectedByBothAddAndUpdate() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)
        viewModel.addSpeed(2f)
        viewModel.setSelection(6 * SEC, 8 * SEC)
        viewModel.addSpeed(1.25f)
        val before = viewModel.speeds

        viewModel.setSelection(3 * SEC, 5 * SEC)
        val addRejected = viewModel.addSpeed(1.5f)
        val updateRejected = viewModel.updateSpeed(1, SpeedRange(TimeRange(3 * SEC, 5 * SEC), 1.5f))

        assertFalse("overlapping add should be rejected", addRejected)
        assertFalse("overlapping update should be rejected", updateRejected)
        assertEquals(before, viewModel.speeds)
    }

    @Test
    fun addSpeed_obligationV5_boundary_case10_touchingRangesAreAllowedNotOverlapping() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)
        viewModel.addSpeed(2f)

        viewModel.setSelection(4 * SEC, 6 * SEC)
        val accepted = viewModel.addSpeed(1.5f)

        assertTrue(accepted)
        assertEquals(
            listOf(SpeedRange(TimeRange(0L, 4 * SEC), 2f), SpeedRange(TimeRange(4 * SEC, 6 * SEC), 1.5f)),
            viewModel.speeds,
        )
    }

    // obligation V6 / F5,Q3 / C12: addFlip/updateFlip reject "no axis selected"

    @Test
    fun addFlip_obligationV6_error_case12_bothAxesFalseIsRejected() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)

        val accepted = viewModel.addFlip(horizontal = false, vertical = false)

        assertFalse(accepted)
        assertTrue(viewModel.flips.isEmpty())
    }

    @Test
    fun updateFlip_obligationV6_error_bothAxesFalseIsRejectedAndLeavesTheEntryUnchanged() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)
        viewModel.addFlip(horizontal = true, vertical = false)
        val before = viewModel.flips

        val accepted = viewModel.updateFlip(0, FlipRange(TimeRange(0L, 4 * SEC), horizontal = false, vertical = false))

        assertFalse(accepted)
        assertEquals(before, viewModel.flips)
    }

    // obligation V7 / F3 / C14: updateSpeed/updateFlip store clampSelection(duration, start, end)

    @Test
    fun updateSpeed_obligationV7_boundary_case14_anOutOfBoundsRangeIsStoredClamped() {
        val duration = 10 * SEC
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", duration)
        viewModel.setSelection(1 * SEC, 2 * SEC)
        viewModel.addSpeed(1.25f)

        val accepted = viewModel.updateSpeed(0, SpeedRange(TimeRange(-1 * SEC, duration + 1 * SEC), 1.5f))

        assertTrue(accepted)
        val expectedRange = clampSelection(duration, -1 * SEC, duration + 1 * SEC)
        assertEquals(expectedRange, viewModel.speeds[0].range)
        assertEquals(1.5f, viewModel.speeds[0].speed)
    }

    @Test
    fun updateFlip_obligationV7_boundary_case14_anOutOfBoundsRangeIsStoredClamped() {
        val duration = 10 * SEC
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", duration)
        viewModel.setSelection(1 * SEC, 2 * SEC)
        viewModel.addFlip(horizontal = true, vertical = false)

        val accepted = viewModel.updateFlip(
            0,
            FlipRange(TimeRange(-1 * SEC, duration + 1 * SEC), horizontal = true, vertical = true),
        )

        assertTrue(accepted)
        val expectedRange = clampSelection(duration, -1 * SEC, duration + 1 * SEC)
        assertEquals(expectedRange, viewModel.flips[0].range)
        assertTrue(viewModel.flips[0].horizontal)
        assertTrue(viewModel.flips[0].vertical)
    }

    // obligation V8 / begin reset rule / C13

    @Test
    fun begin_obligationV8_edge_case13a_openingADifferentFileResetsEffectsToDefaults() {
        val viewModel = EditorViewModel()
        viewModel.begin("/a.mp4", 10 * SEC)
        viewModel.chooseRatio(RatioPreset.WIDE)
        viewModel.chooseFrameMode(FrameMode.FIT)
        viewModel.setSelection(0L, 2 * SEC)
        viewModel.addSpeed(2f)
        viewModel.addFlip(horizontal = true, vertical = false)

        viewModel.begin("/b.mp4", 10 * SEC)

        assertEquals(RatioPreset.ORIGINAL, viewModel.ratioPreset)
        assertEquals(FrameMode.CROP, viewModel.frameMode)
        assertTrue(viewModel.flips.isEmpty())
        assertTrue(viewModel.speeds.isEmpty())
    }

    @Test
    fun begin_obligationV8_edge_case13b_reopeningAfterEndKeepsTheEffects() {
        val viewModel = EditorViewModel()
        viewModel.begin("/a.mp4", 10 * SEC)
        viewModel.chooseRatio(RatioPreset.WIDE)
        viewModel.chooseFrameMode(FrameMode.FIT)
        viewModel.setSelection(0L, 2 * SEC)
        viewModel.addSpeed(2f)
        viewModel.addFlip(horizontal = true, vertical = false)
        val speedsBefore = viewModel.speeds
        val flipsBefore = viewModel.flips

        viewModel.end()
        viewModel.begin("/a.mp4", 10 * SEC)

        assertEquals(RatioPreset.WIDE, viewModel.ratioPreset)
        assertEquals(FrameMode.FIT, viewModel.frameMode)
        assertEquals(speedsBefore, viewModel.speeds)
        assertEquals(flipsBefore, viewModel.flips)
    }

    @Test
    fun begin_obligationV8_edge_case13note_reopeningTheOriginalFileAfterASecondFileResetsEffects() {
        // C13 note: the session remembers only the last opened path, so A -> B -> A resets, unlike A -> end() -> A (case13b).
        val viewModel = EditorViewModel()
        viewModel.begin("/a.mp4", 10 * SEC)
        viewModel.chooseRatio(RatioPreset.WIDE)
        viewModel.chooseFrameMode(FrameMode.FIT)
        viewModel.setSelection(0L, 2 * SEC)
        viewModel.addSpeed(2f)
        viewModel.addFlip(horizontal = true, vertical = false)

        viewModel.begin("/b.mp4", 10 * SEC)
        viewModel.begin("/a.mp4", 10 * SEC)

        assertEquals(RatioPreset.ORIGINAL, viewModel.ratioPreset)
        assertEquals(FrameMode.CROP, viewModel.frameMode)
        assertTrue(viewModel.flips.isEmpty())
        assertTrue(viewModel.speeds.isEmpty())
    }

    @Test
    fun removeSpeed_obligationV8_edge_outOfRangeIndexDoesNotThrowAndLeavesTheListUnchanged() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)
        viewModel.addSpeed(2f)
        val before = viewModel.speeds

        viewModel.removeSpeed(5)

        assertEquals(before, viewModel.speeds)
    }

    @Test
    fun removeFlip_obligationV8_edge_outOfRangeIndexDoesNotThrowAndLeavesTheListUnchanged() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)
        viewModel.addFlip(horizontal = true, vertical = false)
        val before = viewModel.flips

        viewModel.removeFlip(5)

        assertEquals(before, viewModel.flips)
    }

    @Test
    fun updateSpeed_obligationV8_edge_outOfRangeIndexReturnsFalseWithoutThrowing() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)

        val accepted = viewModel.updateSpeed(0, SpeedRange(TimeRange(0L, 4 * SEC), 1.5f))

        assertFalse(accepted)
        assertTrue(viewModel.speeds.isEmpty())
    }

    @Test
    fun updateFlip_obligationV8_edge_outOfRangeIndexReturnsFalseWithoutThrowing() {
        val viewModel = EditorViewModel()
        viewModel.begin("/in.mp4", 10 * SEC)
        viewModel.setSelection(0L, 4 * SEC)

        val accepted = viewModel.updateFlip(0, FlipRange(TimeRange(0L, 4 * SEC), horizontal = true, vertical = false))

        assertFalse(accepted)
        assertTrue(viewModel.flips.isEmpty())
    }
}
