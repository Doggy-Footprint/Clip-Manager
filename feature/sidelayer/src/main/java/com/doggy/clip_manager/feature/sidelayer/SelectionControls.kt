package com.doggy.clip_manager.feature.sidelayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons

@Composable
internal fun SelectionTopBar(selectionMode: Boolean, count: Int, onSelect: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.End) {
        if (selectionMode) {
            TextButton(onClick = onExit) { Text("선택 해제 ($count)") }
        } else {
            TextButton(onClick = onSelect) { Text("선택") }
        }
    }
}

@Composable
internal fun SelectionBottomBar(
    enabled: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onZip: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = onCopy, enabled = enabled) { Text("복사") }
        TextButton(onClick = onMove, enabled = enabled) { Text("이동") }
        TextButton(onClick = onZip, enabled = enabled) { Text("ZIP") }
        TextButton(onClick = onDelete, enabled = enabled) { Text("삭제") }
        IconButton(onClick = onInfo, enabled = enabled) {
            Icon(ClipIcons.MoreVert, contentDescription = "정보")
        }
    }
}

@Composable
internal fun PasteBar(onPaste: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.Center) {
        Button(onClick = onPaste) { Text("붙여넣기") }
    }
}

@Composable
internal fun ItemActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onZip: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onOpenAsFolder: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onOpenAsFolder != null) {
            DropdownMenuItem(text = { Text("폴더로 열기") }, onClick = { onDismiss(); onOpenAsFolder() })
        }
        DropdownMenuItem(text = { Text("복사") }, onClick = { onDismiss(); onCopy() })
        DropdownMenuItem(text = { Text("이동") }, onClick = { onDismiss(); onMove() })
        DropdownMenuItem(text = { Text("ZIP") }, onClick = { onDismiss(); onZip() })
        DropdownMenuItem(text = { Text("삭제") }, onClick = { onDismiss(); onDelete() })
        DropdownMenuItem(text = { Text("정보") }, onClick = { onDismiss(); onInfo() })
    }
}

@Composable
internal fun ConflictDialog(
    name: String,
    applyRemaining: Boolean,
    onApplyRemainingChange: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onAutoRename: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("이름 충돌") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, modifier = Modifier.weight(1f))
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = applyRemaining, onCheckedChange = onApplyRemainingChange)
                Text("남은 충돌에 적용")
                TextButton(onClick = onSkip) { Text("건너뛰기") }
                TextButton(onClick = onAutoRename) { Text("자동 이름 변경") }
                TextButton(onClick = onReplace) { Text("덮어쓰기") }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("취소") }
        },
    )
}
