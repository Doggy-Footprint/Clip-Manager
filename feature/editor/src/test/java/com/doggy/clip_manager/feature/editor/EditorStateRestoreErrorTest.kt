package com.doggy.clip_manager.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.util.UnstableApi
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.FrameMode
import com.doggy.clip_manager.core.editor.TimeRange
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

/**
 * Verification obligation V3 of spec ad27e5f2d8c95388-editor-toolbox-fullscreen-style-restore.md v3:
 * error guessing over empty/corrupt saved values. We are not given the `SavedStateHandle` key
 * names (they belong to the Implementation), so we corrupt the saved state key-agnostically: take
 * a handle where every F8 field is non-default, then for every key, regardless of its stored
 * type, try replacing just that key's value with the garbage string "}{garbage" (probes keys of
 * any type, since a wrong-typed value is itself a corrupted encoding); and, for keys whose
 * original value is already a String, also try "NOT_A_CONSTANT" (a well-formed but unknown enum
 * name) and "[{}]" (a well-formed but malformed list/JSON shape). For every corruption we check
 * *all 13* F8 fields (not just overlays), each against "original value or that field's documented
 * default" — a single-field oracle would pass even if an unrelated field silently corrupted to
 * some other, undocumented, wrong value. We also track, across the whole sweep, whether each of
 * overlays/flips/speeds/ratioPreset/frameMode/cutMode was ever actually driven to its default by
 * some corruption; if none of the tried keys/variants ever reaches a given field's storage, the
 * "original-or-default" assertion would vacuously pass without ever having exercised that field,
 * so we fail loudly instead.
 *
 * The v2 Errors section added two rows that this test also checks per corruption, not just
 * "original or empty": (1) a corrupted `selection` with a restored `durationUs > 0` must fall
 * back to the default segment a fresh `begin(path, durationUs)` would produce (read from a
 * throwaway `EditorViewModel`, never hard-coded, since that default's exact value isn't specified
 * here); (2) a restored `selectedOverlayId`/`stowedOverlayIds` naming an id absent from the
 * restored `overlays` must have that dangling id removed (selection -> null) — checked
 * unconditionally, which also covers corrupting only the overlays-backing key while
 * selection/stow keys stay valid.
 *
 * `path` is the one F8 field whose type is an unconstrained String: any string is a
 * syntactically legal path (the referenced file may simply not exist, which the VM cannot and
 * need not detect at restore time), so a String-corruption that happens to land on the
 * path-backing key is not something the VM can reject — the injected string itself is accepted
 * as an outcome for `path`, in addition to the original value or the documented default (null).
 * No other F8 field has this shape: `selectedOverlayId`/`stowedOverlayIds` are also String-typed
 * but are constrained (must name a restored overlay, enforced by the Errors row 3 check above),
 * and every other field is a Boolean/Long/enum/TimeRange/list, not a free-form String.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35])
class EditorStateRestoreErrorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // C9: 빈 SavedStateHandle으로 생성 -> 모든 필드가 기존 기본값

    @Test
    fun constructor_obligationV3_boundary_case9_anEmptyHandleStartsAtEveryDefault() {
        val viewModel = EditorViewModel(SavedStateHandle())

        assertFalse(viewModel.editing)
        assertNull(viewModel.path)
        assertEquals(0L, viewModel.durationUs)
        assertEquals(TimeRange(0L, 0L), viewModel.selection)
        assertEquals(CutMode.PRECISE, viewModel.cutMode)
        assertTrue(viewModel.overlays.value.isEmpty())
        assertTrue(viewModel.stowedOverlayIds.isEmpty())
        assertNull(viewModel.selectedOverlayId)
        assertFalse(viewModel.fullscreen)
        assertTrue(viewModel.flips.isEmpty())
        assertTrue(viewModel.speeds.isEmpty())
        assertEquals(RatioPreset.ORIGINAL, viewModel.ratioPreset)
        assertEquals(FrameMode.CROP, viewModel.frameMode)
        assertNull(viewModel.exportSpec())
    }

    // C10 + Errors: 오버레이/목록/enum 저장 키 손상 -> 예외 없이 해당 필드는 기본값 또는 복원값으로 시작

    @Test
    fun constructor_obligationV3_error_case10_corruptingAnyStoredKeyLeavesEveryF8FieldOriginalOrDefault() {
        val populatedHandle = SavedStateHandle()
        val populated = EditorViewModel(populatedHandle)
        populated.begin("/a.mp4", 20 * SEC)
        populated.setSelection(2 * SEC, 8 * SEC)
        populated.chooseCutMode(CutMode.FAST)
        populated.chooseRatio(RatioPreset.WIDE)
        populated.chooseFrameMode(FrameMode.FIT)
        populated.addFlip(horizontal = true, vertical = false)
        populated.addSpeed(1.5f)
        populated.addTextOverlay("hello")
        populated.addImageOverlay(ImageAsset(ImageSource("content://img"), "img"))
        val textId = populated.overlays.value.first().id
        val imageId = populated.overlays.value.last().id
        populated.stow(imageId)
        populated.select(textId)
        populated.toggleFullscreen()

        val originalEditing = populated.editing
        val originalPath = populated.path
        val originalDurationUs = populated.durationUs
        val originalSelection = populated.selection
        val originalCutMode = populated.cutMode
        val originalRatioPreset = populated.ratioPreset
        val originalFrameMode = populated.frameMode
        val originalFlips = populated.flips
        val originalSpeeds = populated.speeds
        val originalOverlays = populated.overlays.value
        val originalStowed = populated.stowedOverlayIds
        val originalSelectedOverlayId = populated.selectedOverlayId
        val originalFullscreen = populated.fullscreen

        val sawOverlaysDefault = booleanArrayOf(false)
        val sawFlipsDefault = booleanArrayOf(false)
        val sawSpeedsDefault = booleanArrayOf(false)
        val sawRatioPresetDefault = booleanArrayOf(false)
        val sawFrameModeDefault = booleanArrayOf(false)
        val sawCutModeDefault = booleanArrayOf(false)

        fun checkCorruption(corruptedValues: Map<String, Any?>, injectedValue: Any?, description: String) {
            val restored = EditorViewModel(SavedStateHandle(corruptedValues))

            fun <T> assertOriginalOrDefault(fieldName: String, actual: T, original: T, default: T) {
                assertTrue(
                    "$description: $fieldName was $actual, expected original ($original) or default ($default)",
                    actual == original || actual == default,
                )
            }

            assertOriginalOrDefault("editing", restored.editing, originalEditing, false)

            // path is an unconstrained String: any string is a syntactically legal path (the file
            // may simply not exist, which the VM cannot and need not detect at restore time), so a
            // String corruption that happens to land on the path-backing key does not corrupt it
            // in a way the VM could reject — the injected string itself is an acceptable outcome,
            // in addition to the original value or the documented default (null).
            assertTrue(
                "$description: path was ${restored.path}, expected original ($originalPath), default (null), " +
                    "or the injected value ($injectedValue)",
                restored.path == originalPath || restored.path == null ||
                    (injectedValue is String && restored.path == injectedValue),
            )

            assertOriginalOrDefault("durationUs", restored.durationUs, originalDurationUs, 0L)

            // Errors row 2 (spec v2): a corrupted selection with durationUs > 0 must fall back to
            // the same default segment a fresh begin(path, durationUs) would produce for that
            // path/duration, not to a hard-coded TimeRange(0,0). We read that default from a
            // throwaway EditorViewModel instead of guessing its shape, so the oracle stays
            // independent of the implementation under test.
            val selectionDefault = if (restored.path != null && restored.durationUs > 0L) {
                val probe = EditorViewModel(SavedStateHandle())
                probe.begin(restored.path!!, restored.durationUs)
                probe.selection
            } else {
                TimeRange(0L, 0L)
            }
            assertOriginalOrDefault("selection", restored.selection, originalSelection, selectionDefault)

            assertOriginalOrDefault("cutMode", restored.cutMode, originalCutMode, CutMode.PRECISE)
            assertOriginalOrDefault("ratioPreset", restored.ratioPreset, originalRatioPreset, RatioPreset.ORIGINAL)
            assertOriginalOrDefault("frameMode", restored.frameMode, originalFrameMode, FrameMode.CROP)
            assertOriginalOrDefault("flips", restored.flips, originalFlips, emptyList())
            assertOriginalOrDefault("speeds", restored.speeds, originalSpeeds, emptyList())
            assertOriginalOrDefault("overlays", restored.overlays.value, originalOverlays, emptyList())
            assertOriginalOrDefault("stowedOverlayIds", restored.stowedOverlayIds, originalStowed, emptySet())
            assertOriginalOrDefault("selectedOverlayId", restored.selectedOverlayId, originalSelectedOverlayId, null)
            assertOriginalOrDefault("fullscreen", restored.fullscreen, originalFullscreen, false)

            // Errors row 3 (spec v2): a restored selectedOverlayId/stowedOverlayIds that names an
            // overlay absent from the restored overlays must have that dangling id removed
            // (selection -> null). This is checked unconditionally for every corruption (including
            // when only the overlays-backing key is corrupted while selection/stow keys stay
            // valid), not just when overlays happens to fall all the way to empty.
            val restoredOverlayIds = restored.overlays.value.map { it.id }.toSet()
            restored.selectedOverlayId?.let { selectedId ->
                assertTrue(
                    "$description: selectedOverlayId=$selectedId does not exist among restored overlays $restoredOverlayIds",
                    selectedId in restoredOverlayIds,
                )
            }
            restored.stowedOverlayIds.forEach { stowedId ->
                assertTrue(
                    "$description: stowedOverlayIds contains $stowedId which does not exist among restored overlays $restoredOverlayIds",
                    stowedId in restoredOverlayIds,
                )
            }

            // exportSpec() must not throw either; once a path is restored, it must be non-null and
            // must carry whatever cutMode/overlays this same corruption left the fields holding.
            val exportSpec = restored.exportSpec()
            if (restored.path != null) {
                assertTrue("$description: exportSpec() was null despite a restored path", exportSpec != null)
                if (exportSpec != null) {
                    assertEquals("$description: exportSpec().cutMode mismatch", restored.cutMode, exportSpec.cutMode)
                }
            }

            if (restored.overlays.value.isEmpty()) sawOverlaysDefault[0] = true
            if (restored.flips.isEmpty()) sawFlipsDefault[0] = true
            if (restored.speeds.isEmpty()) sawSpeedsDefault[0] = true
            if (restored.ratioPreset == RatioPreset.ORIGINAL) sawRatioPresetDefault[0] = true
            if (restored.frameMode == FrameMode.CROP) sawFrameModeDefault[0] = true
            if (restored.cutMode == CutMode.PRECISE) sawCutModeDefault[0] = true
        }

        val allKeys = populatedHandle.keys()
        assertTrue("expected at least one saved key to corrupt", allKeys.isNotEmpty())

        for (corruptedKey in allKeys) {
            val originalValue = populatedHandle.get<Any?>(corruptedKey)

            // (a) every key, regardless of its original type: force it to a garbage String. A
            // wrong-typed value is itself a corrupted encoding, so this probes non-String keys too.
            val garbageValue = "}{garbage"
            val garbageValues = allKeys.associateWith { key -> if (key == corruptedKey) garbageValue else populatedHandle.get<Any?>(key) }
            checkCorruption(garbageValues, garbageValue, "key=$corruptedKey variant=garbageString")

            if (originalValue is String) {
                // (b) well-formed-but-wrong replacements, only meaningful where the original type
                // is already String: an unknown enum name, and a malformed list/JSON shape.
                for (wrongValue in listOf("NOT_A_CONSTANT", "[{}]")) {
                    val wrongValues = allKeys.associateWith { key -> if (key == corruptedKey) wrongValue else populatedHandle.get<Any?>(key) }
                    checkCorruption(wrongValues, wrongValue, "key=$corruptedKey variant=$wrongValue")
                }
            }
        }

        assertTrue("no corruption ever drove overlays to its default; the overlay-storage key(s) were never exercised", sawOverlaysDefault[0])
        assertTrue("no corruption ever drove flips to its default; the flips-storage key(s) were never exercised", sawFlipsDefault[0])
        assertTrue("no corruption ever drove speeds to its default; the speeds-storage key(s) were never exercised", sawSpeedsDefault[0])
        assertTrue("no corruption ever drove ratioPreset to its default; the ratioPreset-storage key was never exercised", sawRatioPresetDefault[0])
        assertTrue("no corruption ever drove frameMode to its default; the frameMode-storage key was never exercised", sawFrameModeDefault[0])
        assertTrue("no corruption ever drove cutMode to its default; the cutMode-storage key was never exercised", sawCutModeDefault[0])
    }

    private fun handleSnapshot(handle: SavedStateHandle): Map<String, Any?> =
        handle.keys().associateWith { key -> handle.get<Any?>(key) }

    // SavedStateHandle values may be arrays (LongArray, Array<String>, etc.), which use reference
    // identity for `equals`/`==`: re-writing an array with the same content back into the handle
    // yields a new array instance, so a raw `!=` on two snapshot values would report a change even
    // when nothing actually changed. `Arrays.deepEquals` on two 1-element wrapper arrays gives
    // content equality for both primitive and object arrays (and falls back to plain `equals` for
    // everything else), without needing to enumerate every possible array element type by hand.
    private fun rawValuesEqual(a: Any?, b: Any?): Boolean = java.util.Arrays.deepEquals(arrayOf(a), arrayOf(b))

    // Errors row 3 (spec v3 V3 coverage item "dangling 선택/도구함 id 제거 (도구함 2개 이상 중 일부만
    // dangling일 때 나머지 유지 포함)"): C10's single-stowed-overlay fixture cannot distinguish
    // "remove only the dangling id" from "clear the whole stowedOverlayIds set" — both produce an
    // empty result. This test stows two overlays and manufactures a handle where only one of them
    // is dangling, so only that one must be dropped.
    //
    // We cannot pick which raw key holds the stow set without guessing its name, so we identify it
    // key-agnostically through public-API differencing: removing a STOWED overlay changes both the
    // overlays-storage key(s) and the stow-storage key(s) (remove() also unstows); removing an
    // UNSTOWED overlay changes only the overlays-storage key(s). The key(s) that change in the
    // first case but not the second are therefore the stow-storage key(s) — no name assumed. We
    // then take the post-removal handle (overlays without Y) but reset only that stow key back to
    // its pre-removal value (still {X, Y}), producing a handle where overlays and the stow set
    // disagree about Y without ever touching the overlays-storage key(s) ourselves.
    @Test
    fun constructor_obligationV3_error_case10b_onlyTheDanglingStowedIdIsRemovedWhenOthersStayValid() {
        val handleA = SavedStateHandle()
        val vmA = EditorViewModel(handleA)
        vmA.begin("/a.mp4", 10 * SEC)
        vmA.addTextOverlay("x")
        vmA.addTextOverlay("y")
        vmA.addTextOverlay("z")
        val xId = vmA.overlays.value[0].id
        val yId = vmA.overlays.value[1].id
        val zId = vmA.overlays.value[2].id
        vmA.stow(xId)
        vmA.stow(yId)
        val originalSelection = vmA.selection
        // Whatever selectedOverlayId ends up being here (adding an overlay may select it), it
        // names Z, which is untouched by the manufactured X/Y-only disagreement below, so it must
        // survive restore unchanged — this is the "selection rules intact" complement to the
        // dangling-id removal already covered by C10's Errors-row-3 check.
        val originalSelectedOverlayId = vmA.selectedOverlayId

        val baseSnapshot = handleSnapshot(handleA)

        val handleRemoveStowed = SavedStateHandle(baseSnapshot)
        EditorViewModel(handleRemoveStowed).remove(yId)
        val afterRemoveStowed = handleSnapshot(handleRemoveStowed)

        val handleRemoveUnstowed = SavedStateHandle(baseSnapshot)
        EditorViewModel(handleRemoveUnstowed).remove(zId)
        val afterRemoveUnstowed = handleSnapshot(handleRemoveUnstowed)

        val changedByStowedRemoval = baseSnapshot.keys.filter { !rawValuesEqual(baseSnapshot[it], afterRemoveStowed[it]) }.toSet()
        val changedByUnstowedRemoval = baseSnapshot.keys.filter { !rawValuesEqual(baseSnapshot[it], afterRemoveUnstowed[it]) }.toSet()
        val stowOnlyKeys = changedByStowedRemoval - changedByUnstowedRemoval

        assertTrue(
            "could not key-agnostically isolate the stow-storage key(s): removing a stowed overlay " +
                "($yId) and removing an unstowed overlay ($zId) changed the exact same key set " +
                "($changedByStowedRemoval); overlays and stow may share a single storage key that " +
                "this differencing technique cannot disentangle without guessing a key name",
            stowOnlyKeys.isNotEmpty(),
        )

        // Post-removal state (overlays without Y), but the stow-storage key(s) put back to their
        // pre-removal value (still naming both X and Y) — a manufactured overlays/stow disagreement.
        val danglingStowMap = afterRemoveStowed.toMutableMap()
        for (key in stowOnlyKeys) {
            danglingStowMap[key] = baseSnapshot[key]
        }

        val restored = EditorViewModel(SavedStateHandle(danglingStowMap))

        assertEquals(setOf(xId, zId), restored.overlays.value.map { it.id }.toSet())
        assertEquals(
            "only the dangling id (y=$yId) should be dropped from stowedOverlayIds, the valid id " +
                "(x=$xId) must stay — a whole-set clear would wrongly produce an empty set here",
            setOf(xId),
            restored.stowedOverlayIds,
        )
        assertEquals(
            "selectedOverlayId names z=$zId, which isn't dangling, so it must be unaffected by the " +
                "manufactured x/y-only overlays/stow disagreement",
            originalSelectedOverlayId,
            restored.selectedOverlayId,
        )
        assertEquals(originalSelection, restored.selection)
    }
}
