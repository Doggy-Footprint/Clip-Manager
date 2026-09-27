package com.doggy.clip_manager.feature.editor

/** Preset colors offered for text overlays (A5): 8 opaque ARGB values, white and black included. */
internal val TEXT_PALETTE: List<Long> = listOf(
    0xffffffff,
    0xff000000,
    0xffff3b30,
    0xffff9500,
    0xffffd60a,
    0xff34c759,
    0xff0a84ff,
    0xffbf5af2,
)

/** Background preset derived from a text color (A5): same RGB, fixed alpha 0x80 regardless of input alpha. */
internal fun backgroundOf(colorArgb: Long): Long = (colorArgb and 0xffffffL) or 0x80000000L
