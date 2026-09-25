package com.doggy.clip_manager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.doggy.clip_manager.R
import com.doggy.clip_manager.core.extension.SettingsSection

@Composable
fun SettingsScreen(sections: List<SettingsSection>, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.settings_public_section_title))
        var isHi by rememberSaveable { mutableStateOf(true) }
        Button(onClick = { isHi = !isHi }) {
            Text(stringResource(if (isHi) R.string.settings_clicker_hi else R.string.settings_clicker_bye))
        }
        sections.forEach { section ->
            Text(section.title())
            section.Content()
        }
    }
}
