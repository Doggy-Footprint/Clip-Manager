package com.doggy.clip_manager.ui.fakes

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.doggy.clip_manager.core.extension.BuiltInTab
import com.doggy.clip_manager.core.extension.BuiltInTabOverride
import com.doggy.clip_manager.core.extension.SettingsSection
import com.doggy.clip_manager.core.extension.TabExtension

/**
 * Minimal empty ImageVector: fakes only need a non-null instance to satisfy the
 * TabExtension.icon signature, no path data is required.
 */
private fun fakeIcon(): ImageVector =
    ImageVector.Builder(
        name = "fake-icon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).build()

class FakeTabExtension(
    override val id: String,
    override val order: Int,
    private val labelText: String = id,
) : TabExtension {
    override val icon: ImageVector = fakeIcon()

    @Composable
    override fun label(): String = labelText

    @Composable
    override fun Content(modifier: Modifier) {
        Text(text = "content-$id", modifier = modifier.padding(0.dp))
    }
}

class FakeSettingsSection(
    override val id: String,
    override val order: Int,
    private val titleText: String = id,
) : SettingsSection {
    @Composable
    override fun title(): String = titleText

    @Composable
    override fun Content() {
        Text(text = "content-$id")
    }
}

class FakeBuiltInTabOverride(
    override val target: BuiltInTab,
) : BuiltInTabOverride {
    @Composable
    override fun Content(modifier: Modifier, default: @Composable (Modifier) -> Unit) {
        default(modifier)
    }
}
