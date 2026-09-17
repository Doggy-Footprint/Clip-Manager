package com.doggy.clip_manager.core.editor

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val SEC = 1_000_000L

// One video frame at 30fps: PRECISE/FAST length tolerance from I1/C29/C30 ("길이 오차 ±1프레임").
private const val FRAME_TOLERANCE_US = 33_400L

private class CountingCoefficientStore : CoefficientStore {
    data class Put(val mode: CutMode, val msPerOutputSecond: Double)

    val puts = mutableListOf<Put>()
    private val values = mutableMapOf<CutMode, Double>()

    override fun get(mode: CutMode): Double? = values[mode]

    override fun put(mode: CutMode, msPerOutputSecond: Double) {
        puts.add(Put(mode, msPerOutputSecond))
        values[mode] = msPerOutputSecond
    }
}

@RunWith(AndroidJUnit4::class)
class VideoEditorTest {

    private lateinit var context: Context
    private lateinit var workDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workDir = File(context.cacheDir, "video-editor-test-${System.nanoTime()}").apply { mkdirs() }
    }

    private fun newEditor(store: CoefficientStore = CountingCoefficientStore()): VideoEditor {
        val timeModel = ExpectedTimeModel(
            store = store,
            defaults = mapOf(CutMode.FAST to 50.0, CutMode.PRECISE to 500.0),
        )
        return VideoEditor(context, timeModel)
    }

    private fun copyFixture(name: String): File {
        val dest = File(workDir, name)
        context.assets.open(name).use { input -> dest.outputStream().use { input.copyTo(it) } }
        return dest
    }

    /** Collects every emitted [EditState] up to and including the first terminal one. */
    private fun awaitStates(job: EditJob, timeoutMs: Long = 120_000): List<EditState> = runBlocking {
        val states = mutableListOf<EditState>()
        withTimeout(timeoutMs) {
            job.state.takeWhile { state ->
                states.add(state)
                state !is EditState.Completed && state !is EditState.Failed && state !is EditState.Cancelled
            }.collect {}
        }
        states
    }

    private fun logBench(strategy: ConcatStrategy, mode: CutMode, elapsedMs: Long) {
        Log.i("EditorBench", "strategy=$strategy mode=$mode elapsedMs=$elapsedMs")
    }

    /** Relative paths of every file under [dir], recursively. */
    private fun snapshotFiles(dir: File): Set<String> =
        dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.toSet()

    /** Blocks until [job] emits at least one Running state. */
    private fun awaitRunning(job: EditJob, timeoutMs: Long = 30_000) = runBlocking {
        withTimeout(timeoutMs) { job.state.first { it is EditState.Running } }
    }

    // --- C29: PRECISE, both concat strategies -------------------------------------------------

    @Test
    fun start_C29_normal_preciseSingleCompositionProducesTrimmedConcatenatedOutput() {
        assertPreciseTrimAndConcat(ConcatStrategy.SINGLE_COMPOSITION)
    }

    @Test
    fun start_C29_normal_preciseSegmentConcatProducesTrimmedConcatenatedOutput() {
        assertPreciseTrimAndConcat(ConcatStrategy.SEGMENT_CONCAT)
    }

    private fun assertPreciseTrimAndConcat(strategy: ConcatStrategy) {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)),
            cutMode = CutMode.PRECISE,
            concatStrategy = strategy,
        )

        val startMs = System.currentTimeMillis()
        val job = editor.start(spec)
        val states = awaitStates(job)
        val elapsedMs = System.currentTimeMillis() - startMs
        logBench(strategy, CutMode.PRECISE, elapsedMs)

        val completed = states.last() as EditState.Completed
        val output = File(completed.outputPath)
        assertEquals(input.parentFile, output.parentFile)
        assertTrue(output.exists())

        val durationUs = containerDurationUs(output.absolutePath)
        assertTrue(
            "expected ~8s, got ${durationUs}us",
            kotlin.math.abs(durationUs - 8 * SEC) <= FRAME_TOLERANCE_US,
        )

        assertColorClose(FixtureAssets.SEGMENT_COLORS[1], frameColorAt(output.absolutePath, 1_900_000))
        assertColorClose(FixtureAssets.SEGMENT_COLORS[4], frameColorAt(output.absolutePath, 2_050_000))

        assertTrue(hasAudioTrack(output.absolutePath))
        val audioDurationUs = audioTrackDurationUs(output.absolutePath)!!
        assertTrue(kotlin.math.abs(audioDurationUs - durationUs) <= 100_000)
    }

    // --- C30: FAST, both concat strategies, keyframe snapping ---------------------------------

    @Test
    fun start_C30_normal_fastSingleCompositionSnapsToKeyframesAndPreservesInputBytes() {
        assertFastSnapAndInputUntouched(ConcatStrategy.SINGLE_COMPOSITION)
    }

    @Test
    fun start_C30_normal_fastSegmentConcatSnapsToKeyframesAndPreservesInputBytes() {
        assertFastSnapAndInputUntouched(ConcatStrategy.SEGMENT_CONCAT)
    }

    private fun assertFastSnapAndInputUntouched(strategy: ConcatStrategy) {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val inputHashBefore = input.readBytes().contentHashCode()
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(1_500_000, 3 * SEC), TimeRange(5 * SEC, 10 * SEC)),
            cutMode = CutMode.FAST,
            concatStrategy = strategy,
        )

        val startMs = System.currentTimeMillis()
        val job = editor.start(spec)
        val states = awaitStates(job)
        val elapsedMs = System.currentTimeMillis() - startMs
        logBench(strategy, CutMode.FAST, elapsedMs)

        val completed = states.last() as EditState.Completed
        val output = File(completed.outputPath)

        // snap([(1.5s,3s),(5s,10s)], keyframes every 1s) => [(1s,3s),(5s,10s)] => 7s total.
        val durationUs = containerDurationUs(output.absolutePath)
        assertTrue(
            "expected ~7s, got ${durationUs}us",
            kotlin.math.abs(durationUs - 7 * SEC) <= FRAME_TOLERANCE_US,
        )

        assertColorClose(FixtureAssets.SEGMENT_COLORS[1], frameColorAt(output.absolutePath, 500_000))

        assertEquals(inputHashBefore, input.readBytes().contentHashCode())
    }

    // --- C31: clip (single keep range) ---------------------------------------------------------

    @Test
    fun start_C31_normal_clippingSingleRangeKeepsOnlyThatSegment() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(3 * SEC, 5 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)

        val completed = states.last() as EditState.Completed
        val output = File(completed.outputPath)
        val durationUs = containerDurationUs(output.absolutePath)
        assertTrue(kotlin.math.abs(durationUs - 2 * SEC) <= FRAME_TOLERANCE_US)
        assertColorClose(FixtureAssets.SEGMENT_COLORS[3], frameColorAt(output.absolutePath, 0))
    }

    // --- C32: Running progress observed before Completed ---------------------------------------

    @Test
    fun start_C32_normal_progressIsMonotonicAndEndsCompleted() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)

        val runningStates = states.filterIsInstance<EditState.Running>()
        assertTrue("expected at least one Running state", runningStates.isNotEmpty())
        assertAllWithinUnitRange(runningStates)
        assertNonDecreasing(runningStates)
        assertTrue(states.last() is EditState.Completed)
    }

    private fun assertAllWithinUnitRange(states: List<EditState.Running>) {
        assertTrue(states.all { it.progress in 0f..1f })
    }

    private fun assertNonDecreasing(states: List<EditState.Running>) {
        val progressValues = states.map { it.progress }
        assertEquals(progressValues.sorted(), progressValues)
    }

    // --- C33: cancel while running --------------------------------------------------------------

    @Test
    fun cancel_C33_normal_runningJobCancelsAndLeavesNoOutputOrTempFiles() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val cacheEntriesBefore = snapshotFiles(context.cacheDir)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        awaitRunning(job)
        job.cancel()
        val states = awaitStates(job)

        assertTrue(states.last() is EditState.Cancelled)

        val cacheEntriesAfter = snapshotFiles(context.cacheDir)
        // Only the input fixture we copied ourselves may remain; the job must not leave anything else.
        val newEntries = cacheEntriesAfter - cacheEntriesBefore
        assertTrue("unexpected leftover cache entries: $newEntries", newEntries.isEmpty())
    }

    // --- C42: cancel immediately after start, before Running is ever observed ---------------------

    @Test
    fun cancel_C42_edge_cancelImmediatelyAfterStartEndsCancelledNotStuckIdle() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val cacheEntriesBefore = snapshotFiles(context.cacheDir)
        val store = CountingCoefficientStore()
        val editor = newEditor(store)
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        job.cancel()
        val states = awaitStates(job)

        assertTrue(states.last() is EditState.Cancelled)

        val cacheEntriesAfter = snapshotFiles(context.cacheDir)
        val newEntries = cacheEntriesAfter - cacheEntriesBefore
        assertTrue("unexpected leftover cache entries: $newEntries", newEntries.isEmpty())
        assertTrue(store.puts.isEmpty())
    }

    // --- C34: cancel after completion is a no-op -------------------------------------------------

    @Test
    fun cancel_C34_edge_cancelAfterCompletionKeepsCompletedStateAndOutput() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(3 * SEC, 5 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)
        val completed = states.last() as EditState.Completed

        job.cancel()

        assertEquals(completed, job.state.value)
        assertTrue(File(completed.outputPath).exists())
    }

    // --- C35: unreadable input -------------------------------------------------------------------

    @Test
    fun start_C35_error_missingInputFailsWithInputNotReadable() {
        val missing = File(workDir, "does-not-exist.mp4")
        val entriesBefore = snapshotFiles(workDir)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = missing.absolutePath,
            keepRanges = listOf(TimeRange(0, SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)

        val failed = states.last() as EditState.Failed
        assertTrue(failed.error is InputNotReadableException)
        val entriesAfter = snapshotFiles(workDir)
        assertEquals(entriesBefore, entriesAfter)
    }

    // --- C36: FAST with a track that cannot be stream-copied into mp4 ----------------------------

    @Test
    fun start_C36_error_fastModeWithVorbisAudioFailsWithUnsupportedStreamCopy() {
        val input = copyFixture(FixtureAssets.FIXTURE_B)
        val entriesBefore = snapshotFiles(workDir)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC)),
            cutMode = CutMode.FAST,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)

        val failed = states.last() as EditState.Failed
        assertTrue(failed.error is UnsupportedStreamCopyException)
        val entriesAfter = snapshotFiles(workDir)
        assertEquals(entriesBefore, entriesAfter)
    }

    // --- C7: invalid range is rejected synchronously without touching state ----------------------

    @Test
    fun start_C7_error_zeroLengthRangeThrowsSynchronouslyWithoutChangingJobsCurrent() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val currentBefore = EditJobs.current.value
        val entriesBefore = snapshotFiles(workDir)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(3 * SEC, 3 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        org.junit.Assert.assertThrows(InvalidRangeException::class.java) { editor.start(spec) }

        assertEquals(currentBefore, EditJobs.current.value)
        val entriesAfter = snapshotFiles(workDir)
        assertEquals(entriesBefore, entriesAfter)
    }

    // --- C40: ExpectedTimeModel.record is invoked exactly once, with exact arguments, on completion;
    //          failed/cancelled jobs must not call it at all. ---------------------------------------

    @Test
    fun start_C40_edge_completedJobRecordsExactArguments() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val store = CountingCoefficientStore()
        val defaults = mapOf(CutMode.FAST to 50.0, CutMode.PRECISE to 500.0)
        val editor = VideoEditor(context, ExpectedTimeModel(store = store, defaults = defaults))
        val spec = EditSpec(
            inputPath = input.absolutePath,
            // A single, already-keyframe-aligned range: PRECISE never snaps, so
            // outputDurationUs is exactly (5s - 3s) = 2_000_000.
            keepRanges = listOf(TimeRange(3 * SEC, 5 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)
        val completed = states.last() as EditState.Completed

        assertEquals(1, store.puts.size)
        val put = store.puts.single()
        assertEquals(CutMode.PRECISE, put.mode)
        // FAST's coefficient must be untouched: record was called with mode=PRECISE only.
        assertNull(store.get(CutMode.FAST))

        // record(mode=PRECISE, outputDurationUs=2_000_000, width=640, height=360 [fixture A],
        // actualMs=completed.elapsedMs) blends into the store via:
        //   raw = actualMs / outputSeconds / (width*height / (1920*1080))
        //   stored = alpha*raw + (1-alpha)*default, alpha=0.3 (ExpectedTimeModel default)
        val outputSeconds = 2.0
        val resolutionFactor = (640.0 * 360.0) / (1920.0 * 1080.0)
        val raw = completed.elapsedMs / outputSeconds / resolutionFactor
        val expected = 0.3 * raw + 0.7 * defaults.getValue(CutMode.PRECISE)
        assertEquals(expected, put.msPerOutputSecond, expected * 1e-6)
    }

    @Test
    fun cancel_C40_edge_cancelledJobDoesNotCallRecord() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val store = CountingCoefficientStore()
        val editor = newEditor(store)
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        awaitRunning(job)
        job.cancel()
        val states = awaitStates(job)

        assertTrue(states.last() is EditState.Cancelled)
        assertTrue(store.puts.isEmpty())
    }

    @Test
    fun start_C40_edge_failedJobDoesNotCallRecord() {
        val missing = File(workDir, "does-not-exist.mp4")
        val store = CountingCoefficientStore()
        val editor = newEditor(store)
        val spec = EditSpec(
            inputPath = missing.absolutePath,
            keepRanges = listOf(TimeRange(0, SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)

        assertTrue(states.last() is EditState.Failed)
        assertTrue(store.puts.isEmpty())
    }

    // --- I3: hasEffects=true forces PRECISE even when FAST was requested (per C11's decision table,
    //         but observed here at the VideoEditor level, not just EditPlanner.effectiveCutMode). ----

    @Test
    fun start_I3_normal_fastRequestWithEffectsIsProcessedAsPrecise() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        val editor = newEditor()
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(1_500_000, 3 * SEC)),
            cutMode = CutMode.FAST,
            hasEffects = true,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)

        val completed = states.last() as EditState.Completed
        val output = File(completed.outputPath)
        val durationUs = containerDurationUs(output.absolutePath)
        // If hasEffects correctly forces PRECISE, the range is used as-is (no keyframe snap):
        // (3s - 1.5s) = 1.5s. A FAST-mode bug that ignores hasEffects would instead snap the
        // start to the 1s keyframe, producing a 2s output.
        assertTrue(
            "expected ~1.5s (PRECISE, no snap), got ${durationUs}us",
            kotlin.math.abs(durationUs - 1_500_000L) <= FRAME_TOLERANCE_US,
        )
    }

    // --- I7: Running.slowState must reflect the SlowDetector's actual verdict, not a constant. ------

    @Test
    fun start_I7_normal_tinyExpectedDurationEventuallyReportsSlowerThanExpected() {
        val input = copyFixture(FixtureAssets.FIXTURE_A)
        // A near-zero ms/output-second default makes any real encode time vastly exceed
        // 3x the expected duration, so SlowDetector must report SLOWER_THAN_EXPECTED almost
        // immediately once at least one progress callback arrives.
        val timeModel = ExpectedTimeModel(
            store = CountingCoefficientStore(),
            defaults = mapOf(CutMode.FAST to 0.001, CutMode.PRECISE to 0.001),
        )
        val editor = VideoEditor(context, timeModel)
        val spec = EditSpec(
            inputPath = input.absolutePath,
            keepRanges = listOf(TimeRange(0, 2 * SEC), TimeRange(4 * SEC, 10 * SEC)),
            cutMode = CutMode.PRECISE,
        )

        val job = editor.start(spec)
        val states = awaitStates(job)

        val runningStates = states.filterIsInstance<EditState.Running>()
        assertTrue("expected at least one Running state", runningStates.isNotEmpty())
        assertTrue(
            "expected at least one Running.slowState == SLOWER_THAN_EXPECTED",
            runningStates.any { it.slowState == SlowState.SLOWER_THAN_EXPECTED },
        )
        assertTrue(states.last() is EditState.Completed)
    }
}
