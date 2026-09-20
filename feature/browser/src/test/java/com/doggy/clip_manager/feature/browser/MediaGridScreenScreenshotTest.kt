package com.doggy.clip_manager.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.doggy.clip_manager.core.designsystem.theme.ClipTheme
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
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
class MediaGridScreenScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Unresolvable in Robolectric (no real MediaStore/Coil backing), so these entries also
    // exercise C10: thumbnail load failure must still render an icon + file name, not a blank cell.
    private val videoEntry = MediaEntry(
        uri = "content://media/external/video/media/1",
        filePath = "/storage/emulated/0/Movies/clip.mp4",
        displayName = "clip.mp4",
        kind = MediaKind.VIDEO,
        bucketName = "Movies",
        dateModifiedSeconds = 300,
        durationMs = 12_000,
    )
    private val audioEntry = MediaEntry(
        uri = "content://media/external/audio/media/2",
        filePath = "/storage/emulated/0/Music/song.mp3",
        displayName = "song.mp3",
        kind = MediaKind.AUDIO,
        bucketName = "Music",
        dateModifiedSeconds = 200,
        durationMs = 180_000,
    )
    private val imageEntry = MediaEntry(
        uri = "content://media/external/images/media/3",
        filePath = "/storage/emulated/0/Pictures/photo.jpg",
        displayName = "photo.jpg",
        kind = MediaKind.IMAGE,
        bucketName = "Pictures",
        dateModifiedSeconds = 100,
        durationMs = null,
    )

    @Test
    fun entries_C5_normal_C10_edge() = capture("entries") {
        MediaGridScreen(
            uiState = MediaGridUiState.Success(listOf(videoEntry, audioEntry, imageEntry)),
            permissionGranted = true,
            onRequestPermission = {},
            onEntryClick = {},
            selectedUri = videoEntry.uri,
        )
    }

    @Test
    fun empty_C6b_boundary() = capture("empty") {
        MediaGridScreen(
            uiState = MediaGridUiState.Success(emptyList()),
            permissionGranted = true,
            onRequestPermission = {},
            onEntryClick = {},
        )
    }

    @Test
    fun permissionRequired_C9_edge() = capture("permission") {
        MediaGridScreen(
            uiState = MediaGridUiState.Loading,
            permissionGranted = false,
            onRequestPermission = {},
            onEntryClick = {},
        )
    }

    @Test
    fun permissionRequiredOverLoadedEntries_C17_edge() = capture("permission_with_entries") {
        MediaGridScreen(
            uiState = MediaGridUiState.Success(listOf(videoEntry, audioEntry, imageEntry)),
            permissionGranted = false,
            onRequestPermission = {},
            onEntryClick = {},
        )
    }

    // selectedUri points at the second entry so a "always highlight index 0" implementation
    // produces a different image than this golden.
    @Test
    fun selectionFollowsSelectedUri_C18_normal() = capture("selected_second") {
        MediaGridScreen(
            uiState = MediaGridUiState.Success(listOf(videoEntry, audioEntry, imageEntry)),
            permissionGranted = true,
            onRequestPermission = {},
            onEntryClick = {},
            selectedUri = audioEntry.uri,
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            ClipTheme(dynamicColor = false) { Surface { content() } }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/MediaGridScreen_$name.png")
    }
}
