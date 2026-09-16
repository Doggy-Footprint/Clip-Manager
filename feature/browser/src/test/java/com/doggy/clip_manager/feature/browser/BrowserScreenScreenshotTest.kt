package com.doggy.clip_manager.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.doggy.clip_manager.core.designsystem.theme.ClipTheme
import com.doggy.clip_manager.core.model.FileEntry
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35], qualifiers = "w360dp-h640dp-480dpi")
class BrowserScreenScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val movies = File("/storage/emulated/0/Movies")

    @Test
    fun entries() = capture("entries") {
        BrowserScreen(
            uiState = BrowserUiState.Success(
                currentDir = movies,
                isAtRoot = false,
                entries = listOf(
                    FileEntry(File(movies, "2025"), isDirectory = true, isVideo = false),
                    FileEntry(File(movies, "clip.ts"), isDirectory = false, isVideo = true),
                    FileEntry(File(movies, "notes.txt"), isDirectory = false, isVideo = false),
                ),
            ),
            permissionGranted = true,
            onRequestPermission = {},
            onEntryClick = {},
            onNavigateUp = {},
        )
    }

    @Test
    fun emptyFolder() = capture("empty") {
        BrowserScreen(
            uiState = BrowserUiState.Success(movies, isAtRoot = false, entries = emptyList()),
            permissionGranted = true,
            onRequestPermission = {},
            onEntryClick = {},
            onNavigateUp = {},
        )
    }

    @Test
    fun permissionRequired() = capture("permission") {
        BrowserScreen(
            uiState = BrowserUiState.Loading,
            permissionGranted = false,
            onRequestPermission = {},
            onEntryClick = {},
            onNavigateUp = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            ClipTheme(dynamicColor = false) { Surface { content() } }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/BrowserScreen_$name.png")
    }
}
