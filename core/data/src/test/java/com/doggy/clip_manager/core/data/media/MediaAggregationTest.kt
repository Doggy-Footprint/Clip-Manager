package com.doggy.clip_manager.core.data.media

import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAggregationTest {

    private fun entry(
        uri: String,
        filePath: String? = "/storage/emulated/0/Movies/$uri.mp4",
        displayName: String,
        kind: MediaKind = MediaKind.VIDEO,
        dateModifiedSeconds: Long,
    ) = MediaEntry(
        uri = uri,
        filePath = filePath,
        displayName = displayName,
        kind = kind,
        bucketName = null,
        dateModifiedSeconds = dateModifiedSeconds,
        durationMs = null,
    )

    @Test
    fun combine_C1_normal_mergesSourcesAndSortsByDateDescThenNameAscCaseInsensitive() {
        // Same dateModifiedSeconds (300) with names "Banana" vs "apple": a case-sensitive
        // comparator would keep "Banana" first (uppercase 'B' < lowercase 'a' in ASCII), while the
        // contract requires the case-insensitive alphabetical order ("apple" before "Banana").
        val banana = entry(uri = "v1", displayName = "Banana", dateModifiedSeconds = 300)
        val apple = entry(uri = "v2", displayName = "apple", dateModifiedSeconds = 300)
        val cherry = entry(uri = "v3", displayName = "Cherry", dateModifiedSeconds = 200)
        val sourceA = listOf(banana, cherry)
        val sourceB = listOf(apple)

        val result = MediaAggregation.combine(listOf(sourceA, sourceB), emptySet())

        assertEquals(listOf(apple, banana, cherry), result)
    }

    @Test
    fun combine_C2_normal_filterRemovesRejectedEntriesAndKeepsOrderOfSurvivors() {
        // Already in the target sort order (unique, strictly descending dates) so the expected
        // outcome is unambiguous regardless of whether combine re-sorts after filtering.
        val kept1 = entry(uri = "v1", filePath = "/keep/a.mp4", displayName = "A", dateModifiedSeconds = 300)
        val rejected = entry(uri = "v2", filePath = "/reject/b.mp4", displayName = "B", dateModifiedSeconds = 200)
        val kept2 = entry(uri = "v3", filePath = "/keep/c.mp4", displayName = "C", dateModifiedSeconds = 100)
        val filter = MediaFilter { !(it.filePath ?: "").startsWith("/reject/") }

        val result = MediaAggregation.combine(listOf(listOf(kept1, rejected, kept2)), setOf(filter))

        assertEquals(listOf(kept1, kept2), result)
    }

    @Test
    fun combine_C6a_boundary_emptySourceListsProduceEmptyResult() {
        val result = MediaAggregation.combine(listOf(emptyList(), emptyList()), emptySet())

        assertEquals(emptyList<MediaEntry>(), result)
    }

    @Test
    fun combine_C7_boundary_duplicateUriAcrossSourcesAppearsOnce() {
        val duplicate = entry(uri = "dup", displayName = "Same", dateModifiedSeconds = 500)
        val other = entry(uri = "other", displayName = "Other", dateModifiedSeconds = 100)
        val sourceA = listOf(duplicate)
        val sourceB = listOf(duplicate, other)

        val result = MediaAggregation.combine(listOf(sourceA, sourceB), emptySet())

        assertEquals(1, result.count { it.uri == "dup" })
        assertEquals(listOf(duplicate, other), result)
    }

    @Test
    fun combine_C11_edge_filterRejectingEverythingProducesEmptyResult() {
        val entries = listOf(
            entry(uri = "v1", displayName = "A", dateModifiedSeconds = 300),
            entry(uri = "v2", displayName = "B", dateModifiedSeconds = 200),
        )
        val rejectAll = MediaFilter { false }

        val result = MediaAggregation.combine(listOf(entries), setOf(rejectAll))

        assertEquals(emptyList<MediaEntry>(), result)
    }

    @Test
    fun combine_C12_normal_entryMustPassAllFiltersNotJustOne() {
        // Only passesBoth satisfies both conditions; passesKindOnly and passesDateOnly each
        // satisfy exactly one, which must be enough to exclude them under an `all` combinator
        // but would wrongly survive under `any` (or under "only the first filter applies").
        val passesBoth = entry(uri = "v1", displayName = "A", kind = MediaKind.VIDEO, dateModifiedSeconds = 300)
        val passesKindOnly = entry(uri = "v2", displayName = "B", kind = MediaKind.VIDEO, dateModifiedSeconds = 100)
        val passesDateOnly = entry(uri = "v3", displayName = "C", kind = MediaKind.AUDIO, dateModifiedSeconds = 300)
        val passesNeither = entry(uri = "v4", displayName = "D", kind = MediaKind.AUDIO, dateModifiedSeconds = 100)
        val isVideo = MediaFilter { it.kind == MediaKind.VIDEO }
        val isRecent = MediaFilter { it.dateModifiedSeconds >= 200 }

        val result = MediaAggregation.combine(
            listOf(listOf(passesBoth, passesKindOnly, passesDateOnly, passesNeither)),
            setOf(isVideo, isRecent),
        )

        assertEquals(listOf(passesBoth), result)
    }

    @Test
    fun combine_C13_edge_duplicateUriKeepsFirstSeenSourceEntry() {
        val fromA = entry(uri = "dup", displayName = "FromA", dateModifiedSeconds = 300)
        val fromB = entry(uri = "dup", displayName = "FromB", dateModifiedSeconds = 999)

        val result = MediaAggregation.combine(listOf(listOf(fromA), listOf(fromB)), emptySet())

        assertEquals(listOf(fromA), result)
    }

    @Test
    fun combine_C15_normal_filterAppliesToEverySourceNotOnlyTheFirst() {
        // Each source carries one rejected entry, so applying the filter to results[0] alone would
        // leave sourceB's rejected entry in the result.
        val keptA = entry(uri = "v1", filePath = "/keep/a.mp4", displayName = "A", dateModifiedSeconds = 400)
        val rejectedA = entry(uri = "v2", filePath = "/reject/b.mp4", displayName = "B", dateModifiedSeconds = 300)
        val keptB = entry(uri = "v3", filePath = "/keep/c.mp4", displayName = "C", dateModifiedSeconds = 200)
        val rejectedB = entry(uri = "v4", filePath = "/reject/d.mp4", displayName = "D", dateModifiedSeconds = 100)
        val filter = MediaFilter { !(it.filePath ?: "").startsWith("/reject/") }

        val result = MediaAggregation.combine(
            listOf(listOf(keptA, rejectedA), listOf(keptB, rejectedB)),
            setOf(filter),
        )

        assertEquals(listOf(keptA, keptB), result)
    }
}
