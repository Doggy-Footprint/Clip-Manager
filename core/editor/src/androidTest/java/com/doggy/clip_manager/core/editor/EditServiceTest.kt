package com.doggy.clip_manager.core.editor

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val SEC = 1_000_000L

@RunWith(AndroidJUnit4::class)
class EditServiceTest {

    private lateinit var context: Context
    private lateinit var workDir: File
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workDir = File(context.cacheDir, "edit-service-test-${System.nanoTime()}").apply { mkdirs() }
        notificationManager = context.getSystemService(NotificationManager::class.java)
    }

    private fun copyFixture(name: String): File {
        val dest = File(workDir, name)
        context.assets.open(name).use { input -> dest.outputStream().use { input.copyTo(it) } }
        return dest
    }

    private fun awaitJobFor(spec: EditSpec, timeoutMs: Long = 10_000): EditJob = runBlocking {
        withTimeout(timeoutMs) { EditJobs.current.filterNotNull().first { it.spec == spec } }
    }

    private fun awaitFinalState(job: EditJob, timeoutMs: Long = 120_000): EditState = runBlocking {
        var last: EditState = EditState.Idle
        withTimeout(timeoutMs) {
            job.state.takeWhile { state ->
                last = state
                state !is EditState.Completed && state !is EditState.Failed && state !is EditState.Cancelled
            }.collect {}
        }
        last
    }

    /** Polls until no notification with NOTIFICATION_ID is active, or fails after [timeoutMs]. */
    private fun awaitNotificationGone(timeoutMs: Long = 10_000) = runBlocking {
        withTimeout(timeoutMs) {
            while (notificationManager.activeNotifications.any { it.id == EditService.NOTIFICATION_ID }) {
                delay(200)
            }
        }
    }

    /**
     * On API 33+, posting a notification requires POST_NOTIFICATIONS, which is not granted to test
     * APKs by default; grant it so activeNotifications actually reflects the service's notification.
     */
    private fun grantPostNotificationsPermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
    }

    /**
     * Polls (NotificationManagerService posts asynchronously, observed ~200ms after a Running state)
     * for the NOTIFICATION_ID notification until it appears or [job] reaches a terminal state,
     * bounded by [timeoutMs]. Returns null if the job finished first without ever posting one.
     */
    private fun awaitActiveNotification(job: EditJob, timeoutMs: Long = 10_000): StatusBarNotification? = runBlocking {
        withTimeout(timeoutMs) {
            var found: StatusBarNotification? = null
            while (found == null) {
                found = notificationManager.activeNotifications.firstOrNull { it.id == EditService.NOTIFICATION_ID }
                if (found == null) {
                    val state = job.state.value
                    val isTerminal = state is EditState.Completed || state is EditState.Failed || state is EditState.Cancelled
                    if (isTerminal) break
                    delay(100)
                }
            }
            found
        }
    }

    @Test
    fun start_C37_normal_publishesCurrentJobAndCancelableNotificationThenReachesTerminalState() {
        grantPostNotificationsPermission()
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        EditService.start(context, spec)
        val job = awaitJobFor(spec)
        assertEquals(spec, job.spec)

        runBlocking { withTimeout(30_000) { job.state.first { it is EditState.Running } } }
        val activeNotification = awaitActiveNotification(job)
        assertTrue("expected an active notification with NOTIFICATION_ID while running", activeNotification != null)
        val actions = activeNotification!!.notification.actions.orEmpty()
        assertTrue("expected a cancel action on the notification", actions.isNotEmpty())

        // PendingIntent does not expose its wrapped Intent's action through any public API, so the
        // only externally-observable way to assert this action targets ACTION_CANCEL is to fire it
        // and check it actually cancels the job (the same effect C38 verifies via an explicit intent).
        actions.single().actionIntent.send()

        val finalState = awaitFinalState(job)
        assertTrue(
            "firing the notification's cancel action must cancel the job, proving it targets ACTION_CANCEL",
            finalState is EditState.Cancelled,
        )
    }

    @Test
    fun onStartCommand_C38_normal_actionCancelIntentCancelsRunningJob() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        EditService.start(context, spec)
        val job = awaitJobFor(spec)
        runBlocking { withTimeout(30_000) { job.state.first { it is EditState.Running } } }

        context.startService(Intent(context, EditService::class.java).setAction(EditService.ACTION_CANCEL))

        val finalState = awaitFinalState(job)
        assertTrue(finalState is EditState.Cancelled)
    }

    @Test
    fun onTerminalState_C41_normal_serviceStopsForegroundClearsNotificationAndAcceptsNewJob() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val firstSpec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(3 * SEC, 5 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        EditService.start(context, firstSpec)
        val firstJob = awaitJobFor(firstSpec)
        val firstFinalState = awaitFinalState(firstJob)
        assertTrue(firstFinalState is EditState.Completed)

        awaitNotificationGone()

        val secondSpec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC)),
            cutMode = CutMode.PRECISE,
        )
        EditService.start(context, secondSpec)
        val secondJob = awaitJobFor(secondSpec)
        val secondFinalState = awaitFinalState(secondJob)
        assertTrue(secondFinalState is EditState.Completed)
    }

    @Test
    fun encodeDecodeStartSpec_C14_normal_roundTripsEveryEffectField() {
        val spec = EditSpec(
            inputPath = "/input.mp4",
            keepRanges = listOf(TimeRange(0, SEC), TimeRange(2 * SEC, 3 * SEC)),
            cutMode = CutMode.FAST,
            concatStrategy = ConcatStrategy.SEGMENT_CONCAT,
            effects = EditEffects(
                frameLayout = FrameLayout.Ratio(3, 4, FrameMode.FIT, NormalizedPoint(1f, 0f)),
                flips = listOf(FlipRange(TimeRange(0, SEC), true, false)),
                speeds = listOf(SpeedRange(TimeRange(2 * SEC, 3 * SEC), 1.25f)),
            ),
        )

        assertEquals(spec, EditService.decodeStartSpec(EditService.encodeStartIntent(context, spec)))
    }

    @Test
    fun decodeStartSpec_C14_error_missingPayloadReturnsNullWithoutStartingJob() {
        val currentBefore = EditJobs.current.value
        val malformed = Intent(context, EditService::class.java).replaceExtras(Bundle())

        assertEquals(null, EditService.decodeStartSpec(malformed))
        assertEquals(currentBefore, EditJobs.current.value)
    }

    @Test
    fun decodeStartSpec_C14_error_partialFlipArraysReturnNullWithoutStartingJob() {
        val intent = validEffectsIntent().apply { removeExtra(EditService.EXTRA_FLIP_HORIZONTAL) }

        assertDecodeRefuses(intent)
    }

    @Test
    fun decodeStartSpec_C14_error_mismatchedFlipArraysReturnNullWithoutStartingJob() {
        val intent = validEffectsIntent().apply {
            putExtra(EditService.EXTRA_FLIP_ENDS, longArrayOf(SEC, 2 * SEC))
        }

        assertDecodeRefuses(intent)
    }

    @Test
    fun decodeStartSpec_C14_error_partialSpeedArraysReturnNullWithoutStartingJob() {
        val intent = validEffectsIntent().apply { removeExtra(EditService.EXTRA_SPEED_VALUES) }

        assertDecodeRefuses(intent)
    }

    @Test
    fun decodeStartSpec_C14_error_mismatchedSpeedArraysReturnNullWithoutStartingJob() {
        val intent = validEffectsIntent().apply {
            putExtra(EditService.EXTRA_SPEED_VALUES, floatArrayOf(1.25f, 1.5f))
        }

        assertDecodeRefuses(intent)
    }

    @Test
    fun decodeStartSpec_C14_error_nonPositiveFrameWidthReturnsNullWithoutStartingJob() {
        val intent = validEffectsIntent().apply { putExtra(EditService.EXTRA_FRAME_WIDTH, 0) }

        assertDecodeRefuses(intent)
    }

    @Test
    fun decodeStartSpec_C14_error_outOfBoundsCropCoordinateReturnsNullWithoutStartingJob() {
        val intent = validEffectsIntent().apply { putExtra(EditService.EXTRA_CROP_X, 1.01f) }

        assertDecodeRefuses(intent)
    }

    private fun validEffectsIntent(): Intent = EditService.encodeStartIntent(
        context,
        EditSpec(
            inputPath = "/input.mp4",
            keepRanges = listOf(TimeRange(0, SEC)),
            cutMode = CutMode.PRECISE,
            effects = EditEffects(
                frameLayout = FrameLayout.Ratio(1, 1, FrameMode.CROP),
                flips = listOf(FlipRange(TimeRange(0, SEC), true, false)),
                speeds = listOf(SpeedRange(TimeRange(0, SEC), 1.25f)),
            ),
        ),
    )

    private fun assertDecodeRefuses(intent: Intent) {
        val currentBefore = EditJobs.current.value

        assertEquals(null, EditService.decodeStartSpec(intent))
        assertEquals(currentBefore, EditJobs.current.value)
    }
}
