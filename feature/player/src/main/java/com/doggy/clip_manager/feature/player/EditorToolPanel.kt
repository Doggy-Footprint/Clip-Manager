package com.doggy.clip_manager.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.platform.LocalContext
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.EditService
import com.doggy.clip_manager.core.editor.EditState
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.SlowState
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.ui.formatTime

private const val US_PER_MS = 1_000L

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorToolPanel(
    viewModel: EditorViewModel,
    overlays: List<OverlaySpec>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val padding = dimensionResource(R.dimen.feature_player_overlay_padding_horizontal)
    val contentColor = colorResource(R.color.feature_player_content)

    Column(
        modifier
            .background(colorResource(R.color.feature_player_scrim))
            .verticalScroll(rememberScrollState())
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(padding),
    ) {
        val durationMs = (viewModel.durationUs / US_PER_MS).toFloat()
        val selection = viewModel.selection
        Text(
            stringResource(
                R.string.feature_player_editor_selection,
                formatTime(selection.startUs / US_PER_MS),
                formatTime(selection.endUs / US_PER_MS),
            ),
            color = contentColor,
            style = MaterialTheme.typography.bodySmall,
        )
        RangeSlider(
            value = (selection.startUs / US_PER_MS).toFloat()..(selection.endUs / US_PER_MS).toFloat(),
            valueRange = 0f..maxOf(durationMs, 1f),
            enabled = viewModel.durationUs > 0L,
            onValueChange = { range ->
                viewModel.setSelection(range.start.toLong() * US_PER_MS, range.endInclusive.toLong() * US_PER_MS)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(padding), verticalAlignment = Alignment.CenterVertically) {
            CutMode.entries.forEach { mode ->
                FilterChip(
                    selected = viewModel.cutMode == mode,
                    onClick = { viewModel.chooseCutMode(mode) },
                    label = {
                        Text(
                            stringResource(
                                if (mode == CutMode.FAST) R.string.feature_player_editor_cut_fast
                                else R.string.feature_player_editor_cut_precise,
                            ),
                        )
                    },
                )
            }
            val defaultText = stringResource(R.string.feature_player_editor_text_default)
            AssistChip(
                onClick = { viewModel.addTextOverlay(defaultText) },
                label = { Text(stringResource(R.string.feature_player_editor_add_text)) },
            )
        }

        overlays.forEach { overlay ->
            OverlayRow(
                overlay = overlay,
                selected = overlay.id == viewModel.selectedOverlayId,
                contentColor = contentColor,
                onSelect = { viewModel.select(if (overlay.id == viewModel.selectedOverlayId) null else overlay.id) },
                onRemove = { viewModel.remove(overlay.id) },
            )
            val selectedText = overlay as? TextOverlay
            if (selectedText != null && overlay.id == viewModel.selectedOverlayId) {
                TextOverlayControls(selectedText, contentColor) { viewModel.update(it) }
            }
        }

        val state = viewModel.exportState
        if (state is EditState.Running) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            if (state.slowState != SlowState.NORMAL) {
                Text(
                    stringResource(
                        if (state.slowState == SlowState.STALLED) R.string.feature_player_editor_stalled
                        else R.string.feature_player_editor_slow,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(exportStatus(state), color = contentColor, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Button(
            onClick = { viewModel.exportSpec()?.let { EditService.start(context, it) } },
            enabled = viewModel.path != null && !isExporting(state),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feature_player_editor_export))
        }
    }
}

@Composable
private fun OverlayRow(
    overlay: OverlaySpec,
    selected: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelect, modifier = Modifier.weight(1f)) {
            Text(
                text = overlayLabel(overlay),
                color = if (selected) MaterialTheme.colorScheme.primary else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.feature_player_editor_remove)) }
    }
}

@Composable
private fun overlayLabel(overlay: OverlaySpec): String = when (overlay) {
    is TextOverlay -> stringResource(
        R.string.feature_player_editor_text_item,
        overlay.text.ifBlank { stringResource(R.string.feature_player_editor_text_placeholder) },
        formatTime(overlay.range.startUs / US_PER_MS),
        formatTime(overlay.range.endUs / US_PER_MS),
    )
    is ImageOverlay -> stringResource(
        R.string.feature_player_editor_image_item,
        formatTime(overlay.range.startUs / US_PER_MS),
        formatTime(overlay.range.endUs / US_PER_MS),
    )
}

@Composable
private fun TextOverlayControls(
    overlay: TextOverlay,
    contentColor: androidx.compose.ui.graphics.Color,
    onChange: (TextOverlay) -> Unit,
) {
    OutlinedTextField(
        value = overlay.text,
        onValueChange = { onChange(overlay.copy(text = it)) },
        label = { Text(stringResource(R.string.feature_player_editor_text_label)) },
        // A blank value is rejected by the overlay session, so the field simply keeps the last
        // non-blank text rather than letting the user empty it.
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    LabelledSlider(R.string.feature_player_editor_font_size, overlay.style.fontSizePt, MIN_FONT_SIZE_PT..MAX_FONT_SIZE_PT, contentColor) {
        onChange(overlay.copy(style = overlay.style.copy(fontSizePt = it)))
    }
    LabelledSlider(R.string.feature_player_editor_position_x, overlay.style.positionX, 0f..1f, contentColor) {
        onChange(overlay.copy(style = overlay.style.copy(positionX = it)))
    }
    LabelledSlider(R.string.feature_player_editor_position_y, overlay.style.positionY, 0f..1f, contentColor) {
        onChange(overlay.copy(style = overlay.style.copy(positionY = it)))
    }
}

@Composable
private fun LabelledSlider(
    labelRes: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    contentColor: androidx.compose.ui.graphics.Color,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), color = contentColor, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, valueRange = range, onValueChange = onChange, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun exportStatus(state: EditState): String = when (state) {
    EditState.Idle -> stringResource(R.string.feature_player_editor_idle)
    is EditState.Running -> stringResource(R.string.feature_player_editor_running, (state.progress * 100).toInt())
    is EditState.Completed -> stringResource(R.string.feature_player_editor_completed, state.outputPath)
    is EditState.Failed -> stringResource(R.string.feature_player_editor_failed, state.error::class.java.simpleName)
    EditState.Cancelled -> stringResource(R.string.feature_player_editor_cancelled)
}

private const val MIN_FONT_SIZE_PT = 8f
private const val MAX_FONT_SIZE_PT = 96f
