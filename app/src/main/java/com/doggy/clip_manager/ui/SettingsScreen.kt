package com.doggy.clip_manager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import com.doggy.clip_manager.R
import com.doggy.clip_manager.core.extension.SettingsSection

@Composable
fun SettingsScreen(
    sections: List<SettingsSection>,
    modifier: Modifier = Modifier,
    scrollToSectionId: String? = null,
    onScrolledToSection: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<String, Int>() }
    val targetOffset = scrollToSectionId?.let { sectionOffsets[it] }
    val targetKnown = scrollToSectionId != null && sections.any { it.id == scrollToSectionId }
    LaunchedEffect(scrollToSectionId, targetOffset, targetKnown) {
        if (scrollToSectionId == null) return@LaunchedEffect
        if (targetOffset != null) {
            scrollState.animateScrollTo(targetOffset)
            onScrolledToSection()
        } else if (!targetKnown) {
            onScrolledToSection()
        }
    }
    Column(modifier.verticalScroll(scrollState)) {
        Text(stringResource(R.string.settings_public_section_title))
        var isHi by rememberSaveable { mutableStateOf(true) }
        Button(onClick = { isHi = !isHi }) {
            Text(stringResource(if (isHi) R.string.settings_clicker_hi else R.string.settings_clicker_bye))
        }
        sections.forEach { section ->
            Column(
                Modifier.onGloballyPositioned { sectionOffsets[section.id] = it.positionInParent().y.toInt() },
            ) {
                Text(section.title())
                section.Content()
            }
        }
    }
}
