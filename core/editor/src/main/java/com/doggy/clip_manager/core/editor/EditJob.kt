package com.doggy.clip_manager.core.editor

import kotlinx.coroutines.flow.StateFlow

interface EditJob {
    val id: String
    val spec: EditSpec
    val state: StateFlow<EditState>
    fun cancel()
}
