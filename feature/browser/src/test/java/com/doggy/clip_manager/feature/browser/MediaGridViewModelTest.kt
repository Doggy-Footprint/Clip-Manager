package com.doggy.clip_manager.feature.browser

import com.doggy.clip_manager.core.data.media.MediaRepository
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.core.model.MediaQuery
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaGridViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun entry(uri: String, displayName: String, kind: MediaKind) = MediaEntry(
        uri = uri,
        filePath = "/storage/emulated/0/$displayName",
        displayName = displayName,
        kind = kind,
        bucketName = null,
        dateModifiedSeconds = 0L,
        durationMs = null,
    )

    private val videoEntries = listOf(
        entry(uri = "v1", displayName = "clip1.mp4", kind = MediaKind.VIDEO),
        entry(uri = "v2", displayName = "clip2.mp4", kind = MediaKind.VIDEO),
    )
    private val audioEntries = listOf(
        entry(uri = "a1", displayName = "song1.mp3", kind = MediaKind.AUDIO),
    )

    /** Records every [MediaQuery] it receives so tests can assert what kind was requested. */
    private class FakeMediaRepository(
        private val responsesByKind: Map<MediaKind, List<MediaEntry>>,
    ) : MediaRepository {
        val queries = mutableListOf<MediaQuery>()

        override suspend fun query(query: MediaQuery): List<MediaEntry> {
            queries += query
            return query.kinds.flatMap { responsesByKind[it].orEmpty() }
        }
    }

    @Test
    fun setKindThenReload_C3_normal_updatesStateToRepositoryVideoResults() = runTest {
        val repository = FakeMediaRepository(mapOf(MediaKind.VIDEO to videoEntries, MediaKind.AUDIO to audioEntries))
        val viewModel = MediaGridViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        viewModel.setKind(MediaKind.VIDEO)
        viewModel.reload()

        val state = viewModel.uiState.value as MediaGridUiState.Success
        assertEquals(videoEntries, state.entries)
        assertEquals(setOf(MediaKind.VIDEO), repository.queries.last().kinds)
    }

    @Test
    fun setKind_C4_normal_reQueriesWithNewKindAndUpdatesState() = runTest {
        val repository = FakeMediaRepository(mapOf(MediaKind.VIDEO to videoEntries, MediaKind.AUDIO to audioEntries))
        val viewModel = MediaGridViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.setKind(MediaKind.VIDEO)
        viewModel.reload()

        viewModel.setKind(MediaKind.AUDIO)

        val state = viewModel.uiState.value as MediaGridUiState.Success
        assertEquals(audioEntries, state.entries)
        assertEquals(setOf(MediaKind.AUDIO), repository.queries.last().kinds)
    }

    @Test
    fun reload_C14_normal_reQueriesCurrentKindAndPicksUpChangedRepositoryResult() = runTest {
        val repository = MutableMediaRepository(videoEntries)
        val viewModel = MediaGridViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.setKind(MediaKind.VIDEO)
        assertEquals(videoEntries, (viewModel.uiState.value as MediaGridUiState.Success).entries)

        val updated = videoEntries + entry(uri = "v3", displayName = "clip3.mp4", kind = MediaKind.VIDEO)
        repository.entries = updated
        viewModel.reload()

        assertEquals(updated, (viewModel.uiState.value as MediaGridUiState.Success).entries)
    }

    /** Returns whatever [entries] currently holds, so a test can change the answer between queries. */
    private class MutableMediaRepository(var entries: List<MediaEntry>) : MediaRepository {
        override suspend fun query(query: MediaQuery): List<MediaEntry> = entries
    }
}
