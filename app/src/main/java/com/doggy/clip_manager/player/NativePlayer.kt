package com.doggy.clip_manager.player

import android.view.Surface

class NativePlayer {
    private var handle: Long = 0

    fun open(path: String): Boolean {
        release()
        handle = nativeOpen(path)
        return handle != 0L
    }

    fun setSurface(surface: Surface?) {
        if (handle != 0L) nativeSetSurface(handle, surface)
    }

    fun play() {
        if (handle != 0L) nativePlay(handle)
    }

    fun pause() {
        if (handle != 0L) nativePause(handle)
    }

    fun isPlaying(): Boolean = if (handle != 0L) nativeIsPlaying(handle) else false

    fun seekTo(positionMs: Long, mode: SeekMode) {
        if (handle != 0L) nativeSeekTo(handle, positionMs, mode.nativeValue)
    }

    fun positionMs(): Long = if (handle != 0L) nativePositionMs(handle) else 0L

    fun durationMs(): Long = if (handle != 0L) nativeDurationMs(handle) else 0L

    fun release() {
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0
        }
    }

    private external fun nativeOpen(path: String): Long
    private external fun nativeSetSurface(handle: Long, surface: Surface?)
    private external fun nativePlay(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeIsPlaying(handle: Long): Boolean
    private external fun nativeSeekTo(handle: Long, positionMs: Long, mode: Int)
    private external fun nativePositionMs(handle: Long): Long
    private external fun nativeDurationMs(handle: Long): Long
    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("clipplayer")
        }
    }
}
