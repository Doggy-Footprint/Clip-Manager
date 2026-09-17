package com.doggy.clip_manager.core.editor

import android.content.Context

interface CoefficientStore {
    fun get(mode: CutMode): Double?
    fun put(mode: CutMode, msPerOutputSecond: Double)
}

class SharedPreferencesCoefficientStore(context: Context) : CoefficientStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(mode: CutMode): Double? {
        if (!prefs.contains(mode.name)) return null
        return Double.fromBits(prefs.getLong(mode.name, 0L))
    }

    override fun put(mode: CutMode, msPerOutputSecond: Double) {
        prefs.edit().putLong(mode.name, msPerOutputSecond.toRawBits()).apply()
    }

    private companion object {
        const val PREFS_NAME = "editor_coefficients"
    }
}
