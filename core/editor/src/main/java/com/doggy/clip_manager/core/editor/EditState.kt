package com.doggy.clip_manager.core.editor

sealed interface EditState {
    data object Idle : EditState
    data class Running(val progress: Float, val slowState: SlowState) : EditState
    data class Completed(val outputPath: String, val elapsedMs: Long) : EditState
    data class Failed(val error: EditException) : EditState
    data object Cancelled : EditState
}
