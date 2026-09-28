package com.doggy.clip_manager.feature.editor

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.DragAndDropSourceScope
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.platform.LocalContext
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.editor.CutMode
import com.doggy.clip_manager.core.editor.EditService
import com.doggy.clip_manager.core.editor.EditState
import com.doggy.clip_manager.core.editor.FlipRange
import com.doggy.clip_manager.core.editor.FrameMode
import com.doggy.clip_manager.core.editor.ImageOverlay
import com.doggy.clip_manager.core.editor.OverlaySpec
import com.doggy.clip_manager.core.editor.SlowState
import com.doggy.clip_manager.core.editor.SpeedRange
import com.doggy.clip_manager.core.editor.TextOverlay
import com.doggy.clip_manager.core.editor.TimeRange
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
    val padding = dimensionResource(R.dimen.feature_editor_overlay_padding_horizontal)
    val contentColor = colorResource(R.color.feature_editor_content)

    Column(
        modifier
            .background(colorResource(R.color.feature_editor_scrim))
            .verticalScroll(rememberScrollState())
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(padding),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { viewModel.toggleFullscreen() }) {
                Icon(
                    imageVector = if (viewModel.fullscreen) ClipIcons.FullscreenExit else ClipIcons.Fullscreen,
                    contentDescription = stringResource(
                        if (viewModel.fullscreen) R.string.feature_editor_exit_fullscreen else R.string.feature_editor_fullscreen,
                    ),
                    tint = contentColor,
                )
            }
        }
        val durationMs = (viewModel.durationUs / US_PER_MS).toFloat()
        val selection = viewModel.selection
        Text(
            stringResource(
                R.string.feature_editor_selection,
                formatTime(selection.startUs / US_PER_MS),
                formatTime(selection.endUs / US_PER_MS),
            ),
            color = contentColor,
            style = MaterialTheme.typography.bodySmall,
        )
        EditorRangeSlider(
            range = selection,
            durationUs = viewModel.durationUs,
            enabled = viewModel.durationUs > 0L,
            onRangeChange = { start, end -> viewModel.setSelection(start, end) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(padding), verticalAlignment = Alignment.CenterVertically) {
            CutMode.entries.forEach { mode ->
                FilterChip(
                    selected = viewModel.cutMode == mode,
                    onClick = { viewModel.chooseCutMode(mode) },
                    label = {
                        Text(
                            stringResource(
                                if (mode == CutMode.FAST) R.string.feature_editor_cut_fast
                                else R.string.feature_editor_cut_precise,
                            ),
                        )
                    },
                )
            }
            val defaultText = stringResource(R.string.feature_editor_text_default)
            AssistChip(
                onClick = { viewModel.addTextOverlay(defaultText) },
                label = { Text(stringResource(R.string.feature_editor_add_text)) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(padding), verticalAlignment = Alignment.CenterVertically) {
            RatioPreset.entries.forEach { preset ->
                FilterChip(
                    selected = viewModel.ratioPreset == preset,
                    onClick = { viewModel.chooseRatio(preset) },
                    label = { Text(stringResource(ratioLabel(preset))) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(padding), verticalAlignment = Alignment.CenterVertically) {
            FrameMode.entries.forEach { mode ->
                FilterChip(
                    selected = viewModel.frameMode == mode,
                    onClick = { viewModel.chooseFrameMode(mode) },
                    // ORIGINAL ignores frameMode (F1), so the chips stay visible but inert rather
                    // than being hidden and shifting the panel layout.
                    enabled = viewModel.ratioPreset != RatioPreset.ORIGINAL,
                    label = { Text(stringResource(frameModeLabel(mode))) },
                )
            }
        }

        SpeedSection(viewModel, contentColor, padding)
        FlipSection(viewModel, contentColor, padding)

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
                        if (state.slowState == SlowState.STALLED) R.string.feature_editor_stalled
                        else R.string.feature_editor_slow,
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
            Text(stringResource(R.string.feature_editor_export))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverlayRow(
    overlay: OverlaySpec,
    selected: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val dragDecorationColor = colorResource(R.color.feature_editor_drag_decoration)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    Row(
        Modifier
            .fillMaxWidth()
            // The panel Column scrolls (verticalScroll), so a row near an edge can be only
            // partially visible when its long press fires; bringIntoViewRequester lets the drag
            // block ask the ancestor scroll container to reveal it before the shadow starts.
            .bringIntoViewRequester(bringIntoViewRequester)
            // Long-press-drag onto EditorToolboxStrip stows this overlay (F4, A3). The default
            // start detector (the non-experimental dragAndDropSource(drawDragDecoration,
            // transferData) overload) races the child TextButtons' own clickable for the initial
            // Down in the (later) Main pass and loses every time, so drag never starts (D3, verified
            // on clip_tablet_1280x800: long-press-drag produced no shadow and the panel scrolled
            // instead). Detecting the long press ourselves in PointerEventPass.Initial lets this Row
            // (the ancestor) see every Down before a child can claim it, while a released-early Down
            // (a plain tap) is left untouched for the child to handle normally. This requires the
            // experimental raw-pointer-input overload (block), opted into below.
            .dragAndDropSource(
                drawDragDecoration = { drawRect(dragDecorationColor) },
                block = overlayRowDragBlock(overlay.id, bringIntoViewRequester),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSelect, modifier = Modifier.weight(1f)) {
            Text(
                text = overlayLabel(overlay),
                color = if (selected) MaterialTheme.colorScheme.primary else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.feature_editor_remove)) }
    }
}

private const val OVERLAY_DRAG_LABEL = "overlay_id"

/**
 * A tap must still fall through to the row's TextButtons: only a Down held for the platform's long
 * press timeout without being released or moved beyond touch slop starts the transfer; a short tap
 * or an early drag-away is left completely unconsumed so the child's own clickable, or the ancestor
 * scroll, handles it exactly as if this modifier weren't here.
 *
 * `bringIntoView()` is a plain suspend fun, not restricted to `awaitPointerEventScope`, so the long
 * press is detected in its own `awaitPointerEventScope` call and the scroll request happens between
 * that call and `startTransfer` rather than inside it (an `awaitEachGesture` block cannot host it).
 */
@OptIn(ExperimentalFoundationApi::class)
private fun overlayRowDragBlock(
    overlayId: String,
    bringIntoViewRequester: BringIntoViewRequester,
): suspend DragAndDropSourceScope.() -> Unit = {
    while (true) {
        val longPressed = awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downPosition = down.position
            try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    var stillPressed = true
                    while (stillPressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        stillPressed = change != null &&
                            change.pressed &&
                            (change.position - downPosition).getDistance() <= viewConfiguration.touchSlop
                    }
                }
                false
            } catch (timeout: PointerEventTimeoutCancellationException) {
                true
            }
        }
        if (longPressed) {
            bringIntoViewRequester.bringIntoView()
            startTransfer(DragAndDropTransferData(clipData = ClipData.newPlainText(OVERLAY_DRAG_LABEL, overlayId)))
        }
    }
}

@Composable
private fun overlayLabel(overlay: OverlaySpec): String = when (overlay) {
    is TextOverlay -> stringResource(
        R.string.feature_editor_text_item,
        overlay.text.ifBlank { stringResource(R.string.feature_editor_text_placeholder) },
        formatTime(overlay.range.startUs / US_PER_MS),
        formatTime(overlay.range.endUs / US_PER_MS),
    )
    is ImageOverlay -> stringResource(
        R.string.feature_editor_image_item,
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
        label = { Text(stringResource(R.string.feature_editor_text_label)) },
        // A blank value is rejected by the overlay session, so the field simply keeps the last
        // non-blank text rather than letting the user empty it.
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    LabelledSlider(R.string.feature_editor_font_size, overlay.style.fontSizePt, MIN_FONT_SIZE_PT..MAX_FONT_SIZE_PT, contentColor) {
        onChange(overlay.copy(style = overlay.style.copy(fontSizePt = it)))
    }
    LabelledSlider(R.string.feature_editor_position_x, overlay.style.positionX, 0f..1f, contentColor) {
        onChange(overlay.copy(style = overlay.style.copy(positionX = it)))
    }
    LabelledSlider(R.string.feature_editor_position_y, overlay.style.positionY, 0f..1f, contentColor) {
        onChange(overlay.copy(style = overlay.style.copy(positionY = it)))
    }
    Text(stringResource(R.string.feature_editor_text_color), color = contentColor, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.feature_editor_swatch_spacing))) {
        TEXT_PALETTE.forEachIndexed { index, colorArgb ->
            ColorSwatch(
                color = Color(colorArgb),
                selected = colorArgb == overlay.style.colorArgb,
                contentDescription = stringResource(R.string.feature_editor_swatch_color, index + 1),
                onClick = { onChange(overlay.copy(style = overlay.style.copy(colorArgb = colorArgb))) },
            )
        }
    }
    Text(stringResource(R.string.feature_editor_text_background), color = contentColor, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.feature_editor_swatch_spacing))) {
        ColorSwatch(
            color = null,
            selected = overlay.style.backgroundArgb == null,
            contentDescription = stringResource(R.string.feature_editor_background_none),
            onClick = { onChange(overlay.copy(style = overlay.style.copy(backgroundArgb = null))) },
        )
        TEXT_PALETTE.forEachIndexed { index, colorArgb ->
            val backgroundArgb = backgroundOf(colorArgb)
            ColorSwatch(
                color = Color(backgroundArgb),
                selected = backgroundArgb == overlay.style.backgroundArgb,
                contentDescription = stringResource(R.string.feature_editor_swatch_background, index + 1),
                onClick = { onChange(overlay.copy(style = overlay.style.copy(backgroundArgb = backgroundArgb))) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.feature_editor_overlay_padding_horizontal))) {
        Text(stringResource(R.string.feature_editor_text_align), color = contentColor, style = MaterialTheme.typography.bodySmall)
        FilterChip(
            selected = overlay.style.centerAligned,
            onClick = { onChange(overlay.copy(style = overlay.style.copy(centerAligned = true))) },
            label = { Text(stringResource(R.string.feature_editor_align_center)) },
        )
        FilterChip(
            selected = !overlay.style.centerAligned,
            onClick = { onChange(overlay.copy(style = overlay.style.copy(centerAligned = false))) },
            label = { Text(stringResource(R.string.feature_editor_align_left)) },
        )
    }
}

/** [color] null renders the "no background" option: an outlined circle with no fill. */
@Composable
private fun ColorSwatch(
    color: Color?,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val size = dimensionResource(R.dimen.feature_editor_swatch_size)
    val borderWidth = dimensionResource(
        if (selected) R.dimen.feature_editor_swatch_border_width_selected else R.dimen.feature_editor_swatch_border_width,
    )
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else colorResource(R.color.feature_editor_swatch_border)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .let { if (color != null) it.background(color) else it }
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
    )
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
    EditState.Idle -> stringResource(R.string.feature_editor_idle)
    is EditState.Running -> stringResource(R.string.feature_editor_running, (state.progress * 100).toInt())
    is EditState.Completed -> stringResource(R.string.feature_editor_completed, state.outputPath)
    is EditState.Failed -> stringResource(R.string.feature_editor_failed, state.error::class.java.simpleName)
    EditState.Cancelled -> stringResource(R.string.feature_editor_cancelled)
}

private fun ratioLabel(preset: RatioPreset): Int = when (preset) {
    RatioPreset.ORIGINAL -> R.string.feature_editor_ratio_original
    RatioPreset.SQUARE -> R.string.feature_editor_ratio_square
    RatioPreset.WIDE -> R.string.feature_editor_ratio_wide
    RatioPreset.TALL -> R.string.feature_editor_ratio_tall
    RatioPreset.CLASSIC -> R.string.feature_editor_ratio_classic
}

private fun frameModeLabel(mode: FrameMode): Int = when (mode) {
    FrameMode.CROP -> R.string.feature_editor_mode_crop
    FrameMode.FIT -> R.string.feature_editor_mode_fit
    FrameMode.STRETCH -> R.string.feature_editor_mode_stretch
}

/** Reused by the selection range and by each speed/flip range row so they share one slider behaviour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorRangeSlider(
    range: TimeRange,
    durationUs: Long,
    enabled: Boolean,
    onRangeChange: (startUs: Long, endUs: Long) -> Unit,
) {
    val durationMs = (durationUs / US_PER_MS).toFloat()
    RangeSlider(
        value = (range.startUs / US_PER_MS).toFloat()..(range.endUs / US_PER_MS).toFloat(),
        valueRange = 0f..maxOf(durationMs, 1f),
        enabled = enabled,
        onValueChange = { value -> onRangeChange(value.start.toLong() * US_PER_MS, value.endInclusive.toLong() * US_PER_MS) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSection(
    viewModel: EditorViewModel,
    contentColor: androidx.compose.ui.graphics.Color,
    padding: androidx.compose.ui.unit.Dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(padding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.feature_editor_speed_section),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            AssistChip(
                onClick = { viewModel.addSpeed(1f) },
                label = { Text(stringResource(R.string.feature_editor_speed_add)) },
            )
        }
        viewModel.speeds.forEachIndexed { index, speedRange ->
            SpeedRow(
                speedRange = speedRange,
                durationUs = viewModel.durationUs,
                contentColor = contentColor,
                onRangeChange = { start, end -> viewModel.updateSpeed(index, speedRange.copy(range = TimeRange(start, end))) },
                onSpeedChange = { speed -> viewModel.updateSpeed(index, speedRange.copy(speed = speed)) },
                onRemove = { viewModel.removeSpeed(index) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedRow(
    speedRange: SpeedRange,
    durationUs: Long,
    contentColor: androidx.compose.ui.graphics.Color,
    onRangeChange: (startUs: Long, endUs: Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        stringResource(
                            R.string.feature_editor_speed_item,
                            speedRange.speed,
                            formatTime(speedRange.range.startUs / US_PER_MS),
                            formatTime(speedRange.range.endUs / US_PER_MS),
                        ),
                        color = contentColor,
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    SPEED_STEPS.forEach { step ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feature_editor_speed_step, step)) },
                            onClick = {
                                onSpeedChange(step)
                                expanded = false
                            },
                        )
                    }
                }
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.feature_editor_remove)) }
        }
        EditorRangeSlider(range = speedRange.range, durationUs = durationUs, enabled = true, onRangeChange = onRangeChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlipSection(
    viewModel: EditorViewModel,
    contentColor: androidx.compose.ui.graphics.Color,
    padding: androidx.compose.ui.unit.Dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(padding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.feature_editor_flip_section),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            AssistChip(
                onClick = { viewModel.addFlip(horizontal = true, vertical = false) },
                label = { Text(stringResource(R.string.feature_editor_flip_add)) },
            )
        }
        viewModel.flips.forEachIndexed { index, flipRange ->
            FlipRow(
                flipRange = flipRange,
                durationUs = viewModel.durationUs,
                contentColor = contentColor,
                onRangeChange = { start, end -> viewModel.updateFlip(index, flipRange.copy(range = TimeRange(start, end))) },
                onHorizontalChange = { value -> viewModel.updateFlip(index, flipRange.copy(horizontal = value)) },
                onVerticalChange = { value -> viewModel.updateFlip(index, flipRange.copy(vertical = value)) },
                onRemove = { viewModel.removeFlip(index) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlipRow(
    flipRange: FlipRange,
    durationUs: Long,
    contentColor: androidx.compose.ui.graphics.Color,
    onRangeChange: (startUs: Long, endUs: Long) -> Unit,
    onHorizontalChange: (Boolean) -> Unit,
    onVerticalChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.feature_editor_flip_item,
                    formatTime(flipRange.range.startUs / US_PER_MS),
                    formatTime(flipRange.range.endUs / US_PER_MS),
                ),
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = flipRange.horizontal,
                onClick = { onHorizontalChange(!flipRange.horizontal) },
                label = { Text(stringResource(R.string.feature_editor_flip_horizontal)) },
            )
            FilterChip(
                selected = flipRange.vertical,
                onClick = { onVerticalChange(!flipRange.vertical) },
                label = { Text(stringResource(R.string.feature_editor_flip_vertical)) },
            )
            TextButton(onClick = onRemove) { Text(stringResource(R.string.feature_editor_remove)) }
        }
        EditorRangeSlider(range = flipRange.range, durationUs = durationUs, enabled = true, onRangeChange = onRangeChange)
    }
}

private const val MIN_FONT_SIZE_PT = 8f
private const val MAX_FONT_SIZE_PT = 96f
