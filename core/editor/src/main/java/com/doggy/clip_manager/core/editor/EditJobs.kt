package com.doggy.clip_manager.core.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EditJobs {
    private val _current = MutableStateFlow<EditJob?>(null)
    val current: StateFlow<EditJob?> = _current.asStateFlow()

    internal fun setCurrent(job: EditJob?) {
        _current.value = job
    }
}
