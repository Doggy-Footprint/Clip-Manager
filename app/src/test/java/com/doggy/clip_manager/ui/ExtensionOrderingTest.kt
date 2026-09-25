package com.doggy.clip_manager.ui

import com.doggy.clip_manager.core.extension.BuiltInTab
import com.doggy.clip_manager.ui.fakes.FakeBuiltInTabOverride
import com.doggy.clip_manager.ui.fakes.FakeSettingsSection
import com.doggy.clip_manager.ui.fakes.FakeTabExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * VO1 (FR2, C1, C2, C3): orderedTabExtensions equivalence partitions
 * {distinct orders, equal orders, empty}.
 */
class OrderedTabExtensionsTest {

    @Test
    fun distinctOrders_sortsAscendingByOrder_c1() {
        val b = FakeTabExtension(id = "b", order = 2)
        val a = FakeTabExtension(id = "a", order = 1)

        val result = orderedTabExtensions(setOf(b, a))

        assertEquals(listOf(a, b), result)
    }

    @Test
    fun equalOrders_tieBreaksByIdAscending_c2() {
        val y = FakeTabExtension(id = "y", order = 5)
        val x = FakeTabExtension(id = "x", order = 5)

        val result = orderedTabExtensions(setOf(y, x))

        assertEquals(listOf(x, y), result)
    }

    @Test
    fun empty_returnsEmptyList_c3() {
        val result = orderedTabExtensions(emptySet())

        assertTrue(result.isEmpty())
    }
}

/**
 * VO2 (FR8, C13, C3): orderedSettingsSections equivalence partitions
 * {distinct orders, equal orders, empty}.
 */
class OrderedSettingsSectionsTest {

    @Test
    fun distinctOrders_sortsAscendingByOrder() {
        // No explicit Case id backs this partition (spec Cases table only lists C13
        // and C3 for VO2); expected order is derived directly from FR8's stated
        // "order asc then id asc" rule, mirroring C1's fixture shape for tabs.
        val z = FakeSettingsSection(id = "z", order = 5)
        val a = FakeSettingsSection(id = "a", order = 1)

        val result = orderedSettingsSections(setOf(z, a))

        assertEquals(listOf(a, z), result)
    }

    @Test
    fun equalOrders_tieBreaksByIdAscending_c13() {
        val b = FakeSettingsSection(id = "b", order = 3)
        val a = FakeSettingsSection(id = "a", order = 3)

        val result = orderedSettingsSections(setOf(b, a))

        assertEquals(listOf(a, b), result)
    }

    @Test
    fun empty_returnsEmptyList_c3() {
        val result = orderedSettingsSections(emptySet())

        assertTrue(result.isEmpty())
    }
}

/**
 * VO3 (FR5, C3, C4, C5): overridesByTarget equivalence partitions
 * {empty, unique targets, duplicate target}.
 */
class OverridesByTargetTest {

    @Test
    fun empty_returnsEmptyMap_c3() {
        val result = overridesByTarget(emptySet())

        assertTrue(result.isEmpty())
    }

    @Test
    fun uniqueTargets_mapsEachTargetToItsInstance_c5() {
        val images = FakeBuiltInTabOverride(target = BuiltInTab.IMAGES)
        val files = FakeBuiltInTabOverride(target = BuiltInTab.FILES)

        val result = overridesByTarget(setOf(images, files))

        assertEquals(2, result.size)
        assertTrue(result[BuiltInTab.IMAGES] === images)
        assertTrue(result[BuiltInTab.FILES] === files)
    }

    @Test
    fun duplicateTarget_throwsIllegalStateExceptionNamingTarget_c4() {
        val first = FakeBuiltInTabOverride(target = BuiltInTab.IMAGES)
        val second = FakeBuiltInTabOverride(target = BuiltInTab.IMAGES)

        try {
            overridesByTarget(setOf(first, second))
            fail("Expected IllegalStateException for duplicate IMAGES override target")
        } catch (e: IllegalStateException) {
            assertTrue(
                "exception message should mention IMAGES, was: ${e.message}",
                e.message?.contains("IMAGES") == true,
            )
        }
    }
}
