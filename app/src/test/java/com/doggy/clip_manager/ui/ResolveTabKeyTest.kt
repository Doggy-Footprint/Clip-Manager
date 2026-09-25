package com.doggy.clip_manager.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VO4 (FR6, C6, C7): resolveTabKey decision table
 * {Extension present, Extension absent, BuiltIn, Settings}.
 */
class ResolveTabKeyTest {

    @Test
    fun extensionAbsent_fallsBackToBuiltInFiles_c6() {
        val result = resolveTabKey(TabKey.Extension("gone"), setOf("a"))

        assertEquals(TabKey.BuiltIn(MediaTab.FILES), result)
    }

    @Test
    fun extensionPresent_returnsUnchanged_c7() {
        val key = TabKey.Extension("a")

        val result = resolveTabKey(key, setOf("a"))

        assertEquals(key, result)
    }

    @Test
    fun builtIn_returnsUnchanged_c7() {
        val key = TabKey.BuiltIn(MediaTab.VIDEOS)

        val result = resolveTabKey(key, setOf("a"))

        assertEquals(key, result)
    }

    @Test
    fun settings_returnsUnchanged_c7() {
        val key = TabKey.Settings

        val result = resolveTabKey(key, setOf("a"))

        assertEquals(TabKey.Settings, result)
    }
}
