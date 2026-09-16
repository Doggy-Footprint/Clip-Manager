package com.doggy.clip_manager.feature.browser

import com.doggy.clip_manager.core.model.FileEntry
import com.doggy.clip_manager.core.testing.repository.TestFileRepository
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class BrowserViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val root = File("/storage")
    private val movies = File(root, "Movies")
    private val repository = TestFileRepository(
        rootDir = root,
        entriesByDir = mapOf(
            root to listOf(FileEntry(movies, isDirectory = true, isVideo = false)),
            movies to listOf(FileEntry(File(movies, "a.ts"), isDirectory = false, isVideo = true)),
        ),
    )

    @Test
    fun open_directory_listsChildrenAndLeavesRoot() = runTest {
        val viewModel = BrowserViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        viewModel.open(FileEntry(movies, isDirectory = true, isVideo = false))

        val state = viewModel.uiState.value as BrowserUiState.Success
        assertEquals(movies, state.currentDir)
        assertFalse(state.isAtRoot)
        assertEquals(listOf("a.ts"), state.entries.map { it.file.name })
    }

    @Test
    fun navigateUp_atRoot_returnsFalse() = runTest {
        val viewModel = BrowserViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        assertFalse(viewModel.navigateUp())
        assertTrue((viewModel.uiState.value as BrowserUiState.Success).isAtRoot)
    }

    @Test
    fun navigateUp_fromChild_returnsToRoot() = runTest {
        val viewModel = BrowserViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.open(FileEntry(movies, isDirectory = true, isVideo = false))

        assertTrue(viewModel.navigateUp())
        assertEquals(root, (viewModel.uiState.value as BrowserUiState.Success).currentDir)
    }
}
