package com.doggy.clip_manager.core.data.media

import com.doggy.clip_manager.core.model.MediaEntry

/**
 * Drops entries from the aggregated result. Path exclude/include rules join by adding another
 * `@Binds @IntoSet` binding; an empty filter set lets everything through.
 */
fun interface MediaFilter {
    fun accepts(entry: MediaEntry): Boolean
}

object MediaAggregation {
    fun combine(results: List<List<MediaEntry>>, filters: Set<MediaFilter>): List<MediaEntry> =
        results
            .flatten()
            .filter { entry -> filters.all { it.accepts(entry) } }
            .distinctBy { it.uri }
            .sortedWith(
                compareByDescending<MediaEntry> { it.dateModifiedSeconds }
                    .thenBy { it.displayName.lowercase() }
            )
}
