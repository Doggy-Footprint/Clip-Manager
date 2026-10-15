package com.doggy.clip_manager.feature.mainlayer

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * O1 (R2,R3,R6,C7,C11): 3-value BVA (on / -1 / +1) of every `cbzTapZone` boundary in portrait
 * 1080x1920 and landscape 1920x1080. Expected values are hand-derived from the spec formulas:
 * square side = min(w,h)/3 centered on the screen (bounds inclusive); strip =
 * y in [h - inset - strip, h - inset) and wins over Center/Left/Right; otherwise x < w/2 is Left.
 *
 * Portrait: square x in [360,720], y in [780,1140]; strip y in [1809,1872).
 * Landscape: square x in [780,1140], y in [360,720]; strip y in [969,1032).
 */
@RunWith(Parameterized::class)
internal class CbzTapZoneTest(
    private val label: String,
    private val w: Float,
    private val h: Float,
    private val x: Float,
    private val y: Float,
    private val expected: CbzTapZone,
) {
    @Test
    fun zone() {
        assertEquals(label, expected, cbzTapZone(x, y, w, h, STRIP, INSET))
    }

    companion object {
        private const val STRIP = 63f
        private const val INSET = 48f

        private fun c(label: String, w: Float, h: Float, x: Float, y: Float, e: CbzTapZone) =
            arrayOf<Any>(label, w, h, x, y, e)

        private val C = CbzTapZone.Center
        private val L = CbzTapZone.Left
        private val R = CbzTapZone.Right
        private val S = CbzTapZone.PreviewStrip

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            // portrait 1080x1920
            c("P squareLeft -1", 1080f, 1920f, 359f, 960f, L),
            c("P squareLeft on", 1080f, 1920f, 360f, 960f, C),
            c("P squareLeft +1", 1080f, 1920f, 361f, 960f, C),
            c("P squareRight -1", 1080f, 1920f, 719f, 960f, C),
            c("P squareRight on", 1080f, 1920f, 720f, 960f, C),
            c("P squareRight +1", 1080f, 1920f, 721f, 960f, R),
            c("P squareTop -1", 1080f, 1920f, 539f, 779f, L),
            c("P squareTop on", 1080f, 1920f, 539f, 780f, C),
            c("P squareTop +1", 1080f, 1920f, 539f, 781f, C),
            c("P squareBottom -1", 1080f, 1920f, 539f, 1139f, C),
            c("P squareBottom on", 1080f, 1920f, 539f, 1140f, C),
            c("P squareBottom +1", 1080f, 1920f, 539f, 1141f, L),
            c("P stripTop -1", 1080f, 1920f, 100f, 1808f, L),
            c("P stripTop on", 1080f, 1920f, 100f, 1809f, S),
            c("P stripTop +1", 1080f, 1920f, 100f, 1810f, S),
            c("P inset -1", 1080f, 1920f, 100f, 1871f, S),
            c("P inset on", 1080f, 1920f, 100f, 1872f, L),
            c("P inset +1", 1080f, 1920f, 100f, 1873f, L),
            c("P halfX -1", 1080f, 1920f, 539f, 100f, L),
            c("P halfX on", 1080f, 1920f, 540f, 100f, R),
            c("P halfX +1", 1080f, 1920f, 541f, 100f, R),
            // landscape 1920x1080
            c("L squareLeft -1", 1920f, 1080f, 779f, 540f, L),
            c("L squareLeft on", 1920f, 1080f, 780f, 540f, C),
            c("L squareLeft +1", 1920f, 1080f, 781f, 540f, C),
            c("L squareRight -1", 1920f, 1080f, 1139f, 540f, C),
            c("L squareRight on", 1920f, 1080f, 1140f, 540f, C),
            c("L squareRight +1", 1920f, 1080f, 1141f, 540f, R),
            c("L squareTop -1", 1920f, 1080f, 959f, 359f, L),
            c("L squareTop on", 1920f, 1080f, 959f, 360f, C),
            c("L squareTop +1", 1920f, 1080f, 959f, 361f, C),
            c("L squareBottom -1", 1920f, 1080f, 959f, 719f, C),
            c("L squareBottom on", 1920f, 1080f, 959f, 720f, C),
            c("L squareBottom +1", 1920f, 1080f, 959f, 721f, L),
            c("L stripTop -1", 1920f, 1080f, 100f, 968f, L),
            c("L stripTop on", 1920f, 1080f, 100f, 969f, S),
            c("L stripTop +1", 1920f, 1080f, 100f, 970f, S),
            c("L inset -1", 1920f, 1080f, 100f, 1031f, S),
            c("L inset on", 1920f, 1080f, 100f, 1032f, L),
            c("L inset +1", 1920f, 1080f, 100f, 1033f, L),
            c("L halfX -1", 1920f, 1080f, 959f, 100f, L),
            c("L halfX on", 1920f, 1080f, 960f, 100f, R),
            c("L halfX +1", 1920f, 1080f, 961f, 100f, R),
        )
    }
}
