package com.doggy.clip_manager.feature.sidelayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.doggy.clip_manager.core.data.operation.TrashEntry

@Composable
fun TrashDialog(
    entries: List<TrashEntry>,
    onRestore: (String) -> Unit,
    onEmpty: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("휴지통") },
        text = {
            Column {
                if (entries.isEmpty()) {
                    Text("휴지통이 비어 있습니다")
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(entries, key = { it.id }) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.name) },
                                trailingContent = {
                                    TextButton(onClick = { onRestore(entry.id) }) { Text("복원") }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onEmpty) { Text("비우기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}
