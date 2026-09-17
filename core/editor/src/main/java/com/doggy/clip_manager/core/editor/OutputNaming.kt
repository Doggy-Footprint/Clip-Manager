package com.doggy.clip_manager.core.editor

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object OutputNaming {

    private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun outputFileFor(
        input: File,
        nowMillis: Long,
        zone: ZoneId,
        exists: (File) -> Boolean,
    ): File {
        val name = input.name
        val lastDot = name.lastIndexOf('.')
        val base = if (lastDot > 0) name.substring(0, lastDot) else name
        val timestamp = TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        val parent = input.parentFile

        var suffix = ""
        var counter = 1
        while (true) {
            val candidate = File(parent, "${base}_edit_$timestamp$suffix.mp4")
            if (!exists(candidate)) return candidate
            suffix = "_$counter"
            counter++
        }
    }
}
