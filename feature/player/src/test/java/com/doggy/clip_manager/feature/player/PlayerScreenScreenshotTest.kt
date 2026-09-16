package com.doggy.clip_manager.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.doggy.clip_manager.core.designsystem.theme.ClipTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-160dpi")
class PlayerScreenScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // P1: opened, 16:9 video, position 65_000ms (1:05), duration 185_000ms (3:05), paused, controls visible.
    private val baseState = PlayerUiState(
        title = "Test Video",
        opened = true,
        videoAspectRatio = 16f / 9f,
        positionMs = 65_000,
        durationMs = 185_000,
        isPlaying = false,
        controlsVisible = true,
    )

    private val darkContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.fillMaxSize().background(Color.DarkGray))
    }

    private val whiteContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.fillMaxSize().background(Color.White))
    }

    private val videoContentTag = "playerVideoContent"

    private val taggedDarkContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.testTag(videoContentTag).background(Color.DarkGray))
    }

    @Test
    fun P1_normal_pausedShowsPlayIconAndTimes() {
        capture("P1_normal_paused", baseState, darkContent)

        composeTestRule.onNodeWithText("1:05", substring = true).assertExists()
        composeTestRule.onNodeWithText("3:05", substring = true).assertExists()
    }

    @Test
    fun P2_normal_playingShowsPauseIcon() {
        val state = baseState.copy(isPlaying = true)

        capture("P2_normal_playing", state, darkContent)

        composeTestRule.onNodeWithText("1:05", substring = true).assertExists()
        composeTestRule.onNodeWithText("3:05", substring = true).assertExists()
    }

    @Test
    fun P3_boundary_oneHourAndAboveShowsHhMmSs() {
        val state = baseState.copy(durationMs = 3_723_000, positionMs = 3_600_000)

        capture("P3_boundary_hourFormat", state, darkContent)

        composeTestRule.onNodeWithText("01:00:00", substring = true).assertExists()
        composeTestRule.onNodeWithText("01:02:03", substring = true).assertExists()
    }

    @Test
    fun P4_edge_whiteVideoContentKeepsOverlayReadable() {
        capture("P4_edge_whiteContent", baseState, whiteContent)

        composeTestRule.onNodeWithText("1:05", substring = true).assertExists()
        composeTestRule.onNodeWithText("3:05", substring = true).assertExists()
    }

    @Config(sdk = [35], qualifiers = "w360dp-h640dp-480dpi")
    @Test
    fun P5_edge_smallScreenLayoutNotClipped() {
        capture("P5_edge_smallScreen", baseState, darkContent)

        composeTestRule.onNodeWithText("1:05", substring = true).assertExists()
        composeTestRule.onNodeWithText("3:05", substring = true).assertExists()
    }

    @Test
    fun P6_edge_controlsHiddenShowsOnlyVideo() {
        val state = baseState.copy(controlsVisible = false)

        capture("P6_edge_controlsHidden", state, darkContent)

        composeTestRule.onNodeWithText("1:05", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("3:05", substring = true).assertDoesNotExist()
    }

    @Test
    fun P7_edge_notOpenedShowsFailureTextAndSkipsVideoContent() {
        val state = baseState.copy(opened = false)
        var videoContentInvocations = 0
        val trackedContent: @Composable (Modifier) -> Unit = { modifier ->
            videoContentInvocations++
            darkContent(modifier)
        }

        capture("P7_edge_openFailed", state, trackedContent)

        assertEquals(0, videoContentInvocations)
        val openFailedText = composeTestRule.activity.getString(R.string.feature_player_open_failed)
        composeTestRule.onNodeWithText(openFailedText, substring = true).assertExists()
    }

    @Test
    fun P8_edge_nullAspectRatioFillsWholeScreen() {
        val state = baseState.copy(videoAspectRatio = null)

        capture("P8_edge_nullAspect", state, taggedDarkContent)

        val videoBounds = composeTestRule.onNodeWithTag(videoContentTag, useUnmergedTree = true).getBoundsInRoot()
        val rootBounds = composeTestRule.onRoot().getBoundsInRoot()
        assertEquals(rootBounds.width, videoBounds.width)
        assertEquals(rootBounds.height, videoBounds.height)
        composeTestRule.onNodeWithText("1:05", substring = true).assertExists()
    }

    private fun capture(name: String, state: PlayerUiState, videoContent: @Composable (Modifier) -> Unit) {
        composeTestRule.setContent {
            ClipTheme(dynamicColor = false) {
                Surface {
                    PlayerScreen(
                        uiState = state,
                        onToggleControls = {},
                        onTogglePlayback = {},
                        onSkip = {},
                        onSeekChange = {},
                        onSeekFinished = {},
                        videoContent = videoContent,
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/PlayerScreen_$name.png")
    }
}
