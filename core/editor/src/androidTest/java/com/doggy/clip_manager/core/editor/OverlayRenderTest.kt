package com.doggy.clip_manager.core.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doggy.clip_manager.core.model.ImageSource
import java.io.File
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val SEC = 1_000_000L

// fixture_effects is 640x360 with static quadrants; this point sits inside the bottom-right
// yellow quadrant and inside the centered overlay, so it distinguishes the two by color alone.
private const val PROBE_X = 360
private const val PROBE_Y = 200
private val BASELINE = Color.rgb(0xFF, 0xFF, 0x00)
private val OVERLAY_COLOR = Color.rgb(0xFF, 0x00, 0xFF)

@RunWith(AndroidJUnit4::class)
class OverlayRenderTest {

    private lateinit var context: Context
    private lateinit var workDir: File
    private lateinit var overlayImage: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workDir = File(context.cacheDir, "overlay-render-test-${System.nanoTime()}").apply { mkdirs() }
        overlayImage = writeSolidPng(OVERLAY_COLOR, 200, 200)
    }

    @Test
    fun overlay_B11_normal_appearsOnlyInsideItsOutputRange() {
        val output = render(
            EditEffects(overlays = listOf(imageOverlay("o1", TimeRange(3 * SEC, 6 * SEC)))),
        )

        assertColorClose(BASELINE, framePixelAt(output.absolutePath, 1 * SEC, PROBE_X, PROBE_Y))
        assertColorClose(OVERLAY_COLOR, framePixelAt(output.absolutePath, 4 * SEC + SEC / 2, PROBE_X, PROBE_Y))
        assertColorClose(BASELINE, framePixelAt(output.absolutePath, 8 * SEC, PROBE_X, PROBE_Y))
    }

    /**
     * The overlay range is the output timeline, so a 2x segment must not shift it: source 0..4s
     * becomes output 0..2s, and an overlay on output 1..2s must render in the second output second,
     * not in the first as it would if the range were matched against source timestamps.
     */
    @Test
    fun overlay_B11_edge_outputRangeIsNotShiftedByASpeedSegment() {
        val output = render(
            EditEffects(
                speeds = listOf(SpeedRange(TimeRange(0, 4 * SEC), 2f)),
                overlays = listOf(imageOverlay("o1", TimeRange(1 * SEC, 2 * SEC))),
            ),
        )

        assertColorClose(BASELINE, framePixelAt(output.absolutePath, SEC / 2, PROBE_X, PROBE_Y))
        assertColorClose(OVERLAY_COLOR, framePixelAt(output.absolutePath, SEC + SEC / 2, PROBE_X, PROBE_Y))
        assertColorClose(BASELINE, framePixelAt(output.absolutePath, 3 * SEC, PROBE_X, PROBE_Y))
    }

    @Test
    fun overlay_B6_normal_bothConcatStrategiesPlaceItInTheSameOutputRange() {
        val effects = EditEffects(overlays = listOf(imageOverlay("o1", TimeRange(3 * SEC, 6 * SEC))))
        val single = render(effects, ConcatStrategy.SINGLE_COMPOSITION)
        val concat = render(effects, ConcatStrategy.SEGMENT_CONCAT)

        for (probeUs in listOf(1 * SEC, 4 * SEC + SEC / 2, 8 * SEC)) {
            assertColorClose(
                framePixelAt(single.absolutePath, probeUs, PROBE_X, PROBE_Y),
                framePixelAt(concat.absolutePath, probeUs, PROBE_X, PROBE_Y),
            )
        }
    }

    @Test
    fun overlay_B7_error_unreadableImageFailsTheEdit() {
        val missing = File(workDir, "absent.png")
        val output = startEdit(
            EditEffects(
                overlays = listOf(
                    ImageOverlay("o1", TimeRange(0, 2 * SEC), ImageSource(Uri.fromFile(missing).toString())),
                ),
            ),
            ConcatStrategy.SINGLE_COMPOSITION,
        )

        val failure = output.last()
        if (failure !is EditState.Failed) throw AssertionError("expected a failed edit, got $failure")
    }

    private fun imageOverlay(id: String, range: TimeRange) =
        ImageOverlay(id, range, ImageSource(Uri.fromFile(overlayImage).toString()))

    private fun writeSolidPng(color: Int, width: Int, height: Int): File {
        val file = File(workDir, "overlay-$color.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun render(effects: EditEffects, strategy: ConcatStrategy = ConcatStrategy.SINGLE_COMPOSITION): File {
        val states = startEdit(effects, strategy)
        val terminal = states.last()
        if (terminal !is EditState.Completed) throw AssertionError("edit did not complete: $terminal")
        return File(terminal.outputPath)
    }

    private fun startEdit(effects: EditEffects, strategy: ConcatStrategy): List<EditState> {
        val input = FixtureAssets.copyToCache(
            context,
            FixtureAssets.FIXTURE_EFFECTS,
            "overlay-render-${System.nanoTime()}.mp4",
        )
        val editor = VideoEditor(
            context,
            ExpectedTimeModel(
                store = object : CoefficientStore {
                    private val values = mutableMapOf<CutMode, Double>()
                    override fun get(mode: CutMode): Double? = values[mode]
                    override fun put(mode: CutMode, msPerOutputSecond: Double) {
                        values[mode] = msPerOutputSecond
                    }
                },
                defaults = mapOf(CutMode.FAST to 50.0, CutMode.PRECISE to 500.0),
            ),
        )
        val job = editor.start(
            EditSpec(
                inputPath = input.absolutePath,
                keepRanges = listOf(TimeRange(0, 10 * SEC)),
                cutMode = CutMode.PRECISE,
                effects = effects,
                concatStrategy = strategy,
            ),
        )
        return runBlocking {
            val states = mutableListOf<EditState>()
            withTimeout(180_000) {
                job.state.takeWhile { state ->
                    states.add(state)
                    state !is EditState.Completed && state !is EditState.Failed && state !is EditState.Cancelled
                }.collect {}
            }
            states
        }
    }
}
