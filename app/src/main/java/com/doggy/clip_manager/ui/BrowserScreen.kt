package com.doggy.clip_manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doggy.clip_manager.browser.BrowserViewModel
import com.doggy.clip_manager.browser.FileEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenVideo: (String) -> Unit,
    onExit: () -> Unit,
    viewModel: BrowserViewModel = viewModel()
) {
    BackHandler {
        if (!viewModel.navigateUp()) onExit()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(viewModel.currentDir.absolutePath) }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(viewModel.entries) { entry ->
                FileRow(entry) {
                    if (entry.isDirectory) {
                        viewModel.open(entry)
                    } else if (entry.isVideo) {
                        onOpenVideo(entry.file.absolutePath)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    val prefix = if (entry.isDirectory) "📁" else if (entry.isVideo) "▶" else "•"
    ListItem(
        headlineContent = { Text("$prefix ${entry.file.name}") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
