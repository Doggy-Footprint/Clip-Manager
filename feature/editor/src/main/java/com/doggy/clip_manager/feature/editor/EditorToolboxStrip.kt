package com.doggy.clip_manager.feature.editor

import android.content.ClipDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.TextOverlay

/**
 * Bottom row shown while editing (F4): one icon per stowed overlay. Placed in feature/editor so
 * both the editing side layer and the floating panel content (app module) reuse the same strip and
 * drop target. Accepts a plain-text drag carrying an overlay id (started by the overlay row in
 * [EditorToolPanel]) and calls [onStow] with it on drop.
 */
@Composable
fun EditorToolboxStrip(
    overlays: List<OverlaySpec>,
    stowedOverlayIds: Set<String>,
    onUnstow: (String) -> Unit,
    onStow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stowed = overlays.filter { it.id in stowedOverlayIds }
    val target = remember(onStow) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipData = event.toAndroidDragEvent().clipData ?: return false
                if (clipData.itemCount == 0) return false
                val id = clipData.getItemAt(0).text?.toString() ?: return false
                onStow(id)
                return true
            }
        }
    }
    val padding = dimensionResource(R.dimen.feature_editor_overlay_padding_horizontal)
    Row(
        modifier
            .background(colorResource(R.color.feature_editor_scrim))
            .dragAndDropTarget(
                shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
                target = target,
            )
            .horizontalScroll(rememberScrollState())
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(padding),
    ) {
        stowed.forEach { overlay ->
            IconButton(onClick = { onUnstow(overlay.id) }) {
                Icon(
                    imageVector = if (overlay is TextOverlay) ClipIcons.File else ClipIcons.Image,
                    contentDescription = stringResource(
                        if (overlay is TextOverlay) R.string.feature_editor_toolbox_text else R.string.feature_editor_toolbox_image,
                    ),
                    tint = colorResource(R.color.feature_editor_content),
                )
            }
        }
    }
}
