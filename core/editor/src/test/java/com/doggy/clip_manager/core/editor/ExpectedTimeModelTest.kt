package com.doggy.clip_manager.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SEC = 1_000_000L

private class FakeCoefficientStore : CoefficientStore {
    private val values = mutableMapOf<CutMode, Double>()

    override fun get(mode: CutMode): Double? = values[mode]

    override fun put(mode: CutMode, msPerOutputSecond: Double) {
        values[mode] = msPerOutputSecond
    }
}

class ExpectedTimeModelTest {

    @Test
    fun expectedMs_C26_normal_emptyStoreFallsBackToDefaultCoefficient() {
        val model = ExpectedTimeModel(
            store = FakeCoefficientStore(),
            defaults = mapOf(CutMode.PRECISE to 1000.0),
        )

        val result = model.expectedMs(CutMode.PRECISE, outputDurationUs = 10 * SEC, width = 1920, height = 1080)

        assertEquals(10_000L, result)
    }

    @Test
    fun record_C27_normal_blendsWithAlphaAndAffectsFutureResolutionScaledEstimate() {
        val store = FakeCoefficientStore()
        val model = ExpectedTimeModel(store = store, defaults = mapOf(CutMode.PRECISE to 1000.0))

        model.record(CutMode.PRECISE, outputDurationUs = 10 * SEC, width = 1920, height = 1080, actualMs = 20_000)

        // 0.3 * (20_000ms / 10s = 2000 ms/s) + 0.7 * 1000.0 = 1300.0
        assertEquals(1300.0, store.get(CutMode.PRECISE)!!, 1e-9)

        // 1300.0 * 10s * (960*540 / (1920*1080) = 0.25) = 3_250
        val estimate = model.expectedMs(CutMode.PRECISE, outputDurationUs = 10 * SEC, width = 960, height = 540)
        assertEquals(3_250L, estimate)
    }

    @Test
    fun record_C28_edge_zeroOutputDurationLeavesStoreUnchanged() {
        val store = FakeCoefficientStore()
        val model = ExpectedTimeModel(store = store, defaults = mapOf(CutMode.PRECISE to 1000.0))

        model.record(CutMode.PRECISE, outputDurationUs = 0, width = 1920, height = 1080, actualMs = 5_000)

        assertNull(store.get(CutMode.PRECISE))
    }

    @Test
    fun record_C28_edge_zeroAreaLeavesStoreUnchanged() {
        val store = FakeCoefficientStore()
        val model = ExpectedTimeModel(store = store, defaults = mapOf(CutMode.FAST to 500.0))

        model.record(CutMode.FAST, outputDurationUs = 10 * SEC, width = 0, height = 1080, actualMs = 5_000)

        assertNull(store.get(CutMode.FAST))
    }

    @Test
    fun record_C28_edge_zeroOutputDurationDoesNotOverwriteExistingValue() {
        val store = FakeCoefficientStore()
        val model = ExpectedTimeModel(store = store, defaults = mapOf(CutMode.PRECISE to 1000.0))
        model.record(CutMode.PRECISE, outputDurationUs = 10 * SEC, width = 1920, height = 1080, actualMs = 20_000)
        val afterFirstRecord = store.get(CutMode.PRECISE)

        model.record(CutMode.PRECISE, outputDurationUs = 0, width = 1920, height = 1080, actualMs = 999_999)

        assertEquals(afterFirstRecord, store.get(CutMode.PRECISE))
    }
}
