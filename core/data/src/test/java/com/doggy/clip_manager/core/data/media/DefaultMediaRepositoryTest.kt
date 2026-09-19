package com.doggy.clip_manager.core.data.media

import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.core.model.MediaQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultMediaRepositoryTest {

    private class ThrowingMediaSource : MediaSource {
        override suspend fun query(query: MediaQuery): List<MediaEntry> =
            throw IllegalStateException("source failure")
    }

    private class HealthyMediaSource(private val entries: List<MediaEntry>) : MediaSource {
        override suspend fun query(query: MediaQuery): List<MediaEntry> = entries
    }

    @Test
    fun query_C8_error_exceptionFromOneSourceIsAbsorbedAndHealthySourceResultsReturned() = runBlocking {
        val entry1 = MediaEntry(
            uri = "v1",
            filePath = "/storage/emulated/0/Movies/a.mp4",
            displayName = "A",
            kind = MediaKind.VIDEO,
            bucketName = null,
            dateModifiedSeconds = 200,
            durationMs = null,
        )
        val entry2 = MediaEntry(
            uri = "v2",
            filePath = "/storage/emulated/0/Movies/b.mp4",
            displayName = "B",
            kind = MediaKind.VIDEO,
            bucketName = null,
            dateModifiedSeconds = 100,
            durationMs = null,
        )
        val repository = DefaultMediaRepository(
            sources = setOf(ThrowingMediaSource(), HealthyMediaSource(listOf(entry1, entry2))),
            filters = emptySet(),
        )

        val result = repository.query(MediaQuery(setOf(MediaKind.VIDEO)))

        assertEquals(listOf(entry1, entry2), result)
    }
}
