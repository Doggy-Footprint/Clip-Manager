package com.doggy.clip_manager.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipTopAppBar(
    title: String,
    subtitle: String? = null,
    onNavigateUp: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onNavigateUp != null) {
                IconButton(onClick = onNavigateUp) { Icon(ClipIcons.ArrowBack, contentDescription = null) }
            }
        },
    )
}
