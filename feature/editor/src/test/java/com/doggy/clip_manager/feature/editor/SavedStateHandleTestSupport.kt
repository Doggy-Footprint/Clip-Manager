package com.doggy.clip_manager.feature.editor

import androidx.lifecycle.SavedStateHandle

/**
 * Spec ad27e5f2d8c95388 V2 forbids reusing the same `SavedStateHandle` instance across the
 * original and restored `EditorViewModel`: it must be a genuinely new handle built only from
 * the original handle's `keys()`/`get` values, to prove the round trip goes through the saved
 * representation rather than a shared live reference.
 */
internal fun copySavedStateHandle(original: SavedStateHandle): SavedStateHandle {
    val copiedValues = original.keys().associateWith { key -> original.get<Any?>(key) }
    return SavedStateHandle(copiedValues)
}
