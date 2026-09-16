package com.doggy.clip_manager.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.doggy.clip_manager.core.designsystem.component.ClipTopAppBar
import com.doggy.clip_manager.core.designsystem.component.EmptyState
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.core.designsystem.theme.ClipTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35], qualifiers = "w360dp-h640dp-480dpi")
class ThemeScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun components_light() = capture(darkTheme = false, name = "light")

    @Test
    fun components_dark() = capture(darkTheme = true, name = "dark")

    private fun capture(darkTheme: Boolean, name: String) {
        composeTestRule.setContent {
            ClipTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface {
                    Column {
                        ClipTopAppBar(title = "Title", subtitle = "/storage/emulated/0", onNavigateUp = {})
                        ListItem(
                            headlineContent = { Text("Folder") },
                            leadingContent = { Icon(ClipIcons.Folder, contentDescription = null) },
                        )
                        Button(onClick = {}, modifier = Modifier.padding(16.dp)) { Text("Button") }
                        EmptyState(icon = ClipIcons.Tag, title = "Empty", body = "Body", actionLabel = "Action", onAction = {})
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/ClipTheme_$name.png")
    }
}
