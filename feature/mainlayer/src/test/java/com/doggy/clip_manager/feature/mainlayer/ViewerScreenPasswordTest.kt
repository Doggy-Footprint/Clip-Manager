package com.doggy.clip_manager.feature.mainlayer

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V13 (R8,R17,C17,Q4), `ViewerScreen` half (encrypted CBZ), item 5/5: "CBZ 뷰어 `암호` 후 페이지
 * 표시" - password-dialog-display, wrong-password-message, and correct-password page-indicator
 * (`1/N`) are all covered here.
 *
 * The encrypted CBZ fixture (`feature/mainlayer/src/test/resources/archives/secret.cbz`) is
 * an independent, committed binary built once with the system `zip` CLI (A1: CBZ is a ZIP
 * container), not with the implementation under test:
 * `zip -P 'cbz-secret' secret.zip page1.jpg && mv secret.zip secret.cbz`. It contains exactly
 * one image entry (`page1.jpg`), so the expected page indicator after the correct password is
 * `1/1` - an independent oracle from how the fixture was built, not from running the
 * implementation.
 *
 * D12 fix note: an earlier run of this file found `CbzViewerContent` (composed from
 * `ViewerScreen`'s `ViewerContent.Cbz` branch) called its own `hiltViewModel()` instead of
 * using the `ViewerViewModel` passed into `ViewerScreen`, which crashed outside a Hilt-
 * installed composition; that has since been fixed (D12), so the correct-password path below
 * no longer needs Hilt test infrastructure.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ViewerScreenPasswordTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun copyFixture(): File {
        val target = tempFolder.newFile("secret.cbz")
        javaClass.getResourceAsStream("/archives/secret.cbz")!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    @Test
    fun openEncryptedCbz_V13_showsPasswordDialog() {
        val cbz = copyFixture()
        val viewModel = ViewerViewModel(context, ArchiveRepository())

        composeTestRule.setContent {
            ViewerScreen(source = FileOperationSource.Local(cbz), onClose = {}, viewModel = viewModel)
        }
        composeTestRule.waitUntilAtLeastOneExists(hasText("암호"), 5_000)

        composeTestRule.onNodeWithText("암호").assertExists()
    }

    @Test
    fun openEncryptedCbz_V13_wrongPassword_showsWrongPasswordMessage() {
        val cbz = copyFixture()
        val viewModel = ViewerViewModel(context, ArchiveRepository())

        composeTestRule.setContent {
            ViewerScreen(source = FileOperationSource.Local(cbz), onClose = {}, viewModel = viewModel)
        }
        composeTestRule.waitUntilAtLeastOneExists(hasText("암호"), 5_000)

        composeTestRule.onNode(hasSetTextAction()).performTextInput("wrong-cbz-password")
        composeTestRule.onNodeWithText("확인").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText("암호가 올바르지 않습니다"), 5_000)

        composeTestRule.onNodeWithText("암호가 올바르지 않습니다").assertExists()
    }

    @Test
    fun openEncryptedCbz_V13_correctPassword_showsPageIndicator() {
        val cbz = copyFixture()
        val viewModel = ViewerViewModel(context, ArchiveRepository())

        composeTestRule.setContent {
            ViewerScreen(source = FileOperationSource.Local(cbz), onClose = {}, viewModel = viewModel)
        }
        composeTestRule.waitUntilAtLeastOneExists(hasText("암호"), 5_000)

        composeTestRule.onNode(hasSetTextAction()).performTextInput("cbz-secret")
        composeTestRule.onNodeWithText("확인").performClick()
        // secret.cbz has exactly one image entry (page1.jpg), so the expected indicator is 1/1 -
        // derived from the fixture's own contents (built above with the `zip` CLI), not from
        // reading the implementation.
        composeTestRule.waitUntilAtLeastOneExists(hasText("1/1"), 5_000)

        composeTestRule.onNodeWithText("1/1").assertExists()
        composeTestRule.onNodeWithText("암호").assertDoesNotExist()
    }
}
