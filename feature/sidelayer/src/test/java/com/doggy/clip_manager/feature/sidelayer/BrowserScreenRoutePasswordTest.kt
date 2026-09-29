package com.doggy.clip_manager.feature.sidelayer

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationRepository
import com.doggy.clip_manager.core.data.operation.TrashRepository
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * V13 (R8,R17,C17,Q4), `BrowserScreenRoute` half (encrypted ZIP folder open), items 1-4/5:
 * 폴더 열기 시 `암호` 표시, 틀린 암호 → `암호가 올바르지 않습니다`, 맞는 암호 → 항목 표시,
 * `취소` → 원래 목록. Item 5 (CBZ viewer password) is in `ViewerScreenPasswordTest.kt`.
 *
 * The encrypted ZIP fixture (`feature/sidelayer/src/test/resources/archives/secret.zip`) is
 * an independent, committed binary built once with the system `zip` CLI, not with the
 * implementation under test:
 * `zip -P 'route-secret' secret.zip inside.txt` (inside.txt contains "inside-the-zip").
 * feature:sidelayer's test classpath does not have zip4j (core:data declares it
 * `implementation`, not `api`, so it isn't visible here), so this reuses the same
 * committed-binary-fixture approach as A10's 7z fixture rather than building the zip
 * programmatically.
 *
 * `@Config(sdk = [29])`: same `Environment.isExternalStorageManager()` Robolectric
 * limitation as `BrowserScreenRouteDeleteTest` (see its class doc for the full explanation).
 *
 * `viewModel.openArchive`'s password check runs a real `ArchiveRepository.list` call on
 * `Dispatchers.IO`; `composeTestRule.waitForIdle()` only waits for Compose's own idle state,
 * not for that separate real-thread coroutine, so assertions after tapping the archive poll
 * `browserViewModel.passwordRequest`/`passwordError` directly first (an early attempt without
 * this polling was flaky - 2 of 4 tests here failed nondeterministically).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BrowserScreenRoutePasswordTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val correctPassword = "route-secret"

    private fun copyFixture(dir: File): File {
        val target = File(dir, "secret.zip")
        javaClass.getResourceAsStream("/archives/secret.zip")!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() <= deadline) { "condition not met within ${timeoutMs}ms" }
            Thread.sleep(5)
        }
    }

    private fun setUpRoute(dir: File): BrowserViewModel {
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val archives = ArchiveRepository()
        val browserViewModel = BrowserViewModel(TestDirFileRepository(dir), archives)
        val trash = TrashRepository(context, archives) { System.currentTimeMillis() }
        val operations = OperationViewModel(FileOperationRepository(context, archives), archives, trash)

        composeTestRule.setContent {
            BrowserScreenRoute(
                onOpenVideo = {},
                viewModel = browserViewModel,
                operations = operations,
            )
        }
        composeTestRule.waitForIdle()
        return browserViewModel
    }

    @Test
    fun openEncryptedZip_V13_tappingShowsPasswordDialog() {
        val dir = tempFolder.newFolder("pw-root-1")
        copyFixture(dir)
        val browserViewModel = setUpRoute(dir)

        composeTestRule.onNodeWithText("secret.zip").performClick()
        awaitUntil { browserViewModel.passwordRequest.value != null }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("암호").assertExists()
    }

    @Test
    fun openEncryptedZip_V13_wrongPassword_showsWrongPasswordMessage() {
        val dir = tempFolder.newFolder("pw-root-2")
        copyFixture(dir)
        val browserViewModel = setUpRoute(dir)

        composeTestRule.onNodeWithText("secret.zip").performClick()
        awaitUntil { browserViewModel.passwordRequest.value != null }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("totally-wrong")
        composeTestRule.onNodeWithText("확인").performClick()
        awaitUntil { browserViewModel.passwordError.value }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("암호가 올바르지 않습니다").assertExists()
    }

    @Test
    fun openEncryptedZip_V13_correctPassword_showsEntries() {
        val dir = tempFolder.newFolder("pw-root-3")
        copyFixture(dir)
        val browserViewModel = setUpRoute(dir)

        composeTestRule.onNodeWithText("secret.zip").performClick()
        awaitUntil { browserViewModel.passwordRequest.value != null }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(correctPassword)
        composeTestRule.onNodeWithText("확인").performClick()
        awaitUntil { browserViewModel.passwordRequest.value == null }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("inside.txt").assertExists()
    }

    @Test
    fun openEncryptedZip_V13_cancel_returnsToOriginalListing() {
        val dir = tempFolder.newFolder("pw-root-4")
        copyFixture(dir)
        val sibling = File(dir, "sibling.mp4").apply { writeBytes(ByteArray(1)) }
        val browserViewModel = setUpRoute(dir)

        composeTestRule.onNodeWithText("secret.zip").performClick()
        awaitUntil { browserViewModel.passwordRequest.value != null }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("취소").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("암호").assertDoesNotExist()
        composeTestRule.onNodeWithText("secret.zip").assertExists()
        composeTestRule.onNodeWithText(sibling.name).assertExists()
    }
}
