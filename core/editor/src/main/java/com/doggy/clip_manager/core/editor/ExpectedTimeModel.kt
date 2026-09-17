package com.doggy.clip_manager.core.editor

import kotlin.math.roundToLong

class ExpectedTimeModel(
    private val store: CoefficientStore,
    private val defaults: Map<CutMode, Double>,
    private val alpha: Double = 0.3,
) {
    fun expectedMs(mode: CutMode, outputDurationUs: Long, width: Int, height: Int): Long {
        val coefficient = store.get(mode) ?: defaults.getValue(mode)
        return (coefficient * outputSeconds(outputDurationUs) * areaRatio(width, height)).roundToLong()
    }

    fun record(mode: CutMode, outputDurationUs: Long, width: Int, height: Int, actualMs: Long) {
        if (outputDurationUs <= 0 || width <= 0 || height <= 0) return
        val actualCoefficient = actualMs / (outputSeconds(outputDurationUs) * areaRatio(width, height))
        val previous = store.get(mode) ?: defaults.getValue(mode)
        store.put(mode, alpha * actualCoefficient + (1 - alpha) * previous)
    }

    private fun outputSeconds(outputDurationUs: Long): Double = outputDurationUs / 1_000_000.0

    private fun areaRatio(width: Int, height: Int): Double =
        (width.toDouble() * height.toDouble()) / (1920.0 * 1080.0)
}
