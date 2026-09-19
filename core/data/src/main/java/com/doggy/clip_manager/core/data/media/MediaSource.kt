package com.doggy.clip_manager.core.data.media

import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaQuery

/**
 * A place media entries come from. Additional sources (extra scan roots, archives exposing their
 * contents as folders) join the aggregate by adding another `@Binds @IntoSet` binding.
 */
interface MediaSource {
    suspend fun query(query: MediaQuery): List<MediaEntry>
}
