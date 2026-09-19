package com.doggy.clip_manager.core.data.media

import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

interface MediaRepository {
    suspend fun query(query: MediaQuery): List<MediaEntry>
}

internal class DefaultMediaRepository @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards MediaSource>,
    private val filters: Set<@JvmSuppressWildcards MediaFilter>,
) : MediaRepository {
    override suspend fun query(query: MediaQuery): List<MediaEntry> = coroutineScope {
        val results = sources.map { source -> async { runCatching { source.query(query) }.getOrDefault(emptyList()) } }
        MediaAggregation.combine(results.map { it.await() }, filters)
    }
}
