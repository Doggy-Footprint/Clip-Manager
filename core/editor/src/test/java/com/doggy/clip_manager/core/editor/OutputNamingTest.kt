package com.doggy.clip_manager.core.editor

import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputNamingTest {

    // 2026-09-17T13:05:09 in Asia/Seoul, hand-picked per contract case C16.
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val nowMillis: Long =
        ZonedDateTime.of(2026, 9, 17, 13, 5, 9, 0, zone).toInstant().toEpochMilli()

    @Test
    fun outputFileFor_C16_normal_noCollisionUsesTimestampedName() {
        val result = OutputNaming.outputFileFor(
            input = File("/d/clip.mkv"),
            nowMillis = nowMillis,
            zone = zone,
            exists = { false },
        )

        assertEquals(File("/d/clip_edit_20260917_130509.mp4"), result)
    }

    @Test
    fun outputFileFor_C17_edge_baseAndFirstSuffixTakenAppendsNextIndex() {
        val taken = setOf(
            File("/d/clip_edit_20260917_130509.mp4"),
            File("/d/clip_edit_20260917_130509_1.mp4"),
        )

        val result = OutputNaming.outputFileFor(
            input = File("/d/clip.mkv"),
            nowMillis = nowMillis,
            zone = zone,
            exists = { taken.contains(it) },
        )

        assertEquals(File("/d/clip_edit_20260917_130509_2.mp4"), result)
    }

    @Test
    fun outputFileFor_C18_edge_onlyLastExtensionIsStripped() {
        val result = OutputNaming.outputFileFor(
            input = File("/d/my.video.v2.mp4"),
            nowMillis = nowMillis,
            zone = zone,
            exists = { false },
        )

        assertEquals(File("/d/my.video.v2_edit_20260917_130509.mp4"), result)
    }
}
