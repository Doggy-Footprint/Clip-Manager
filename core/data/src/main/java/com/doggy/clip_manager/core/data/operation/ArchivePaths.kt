package com.doggy.clip_manager.core.data.operation

/**
 * Archive entry paths come from an untrusted file, so absolute paths, `..` segments, and
 * symlinks resolving outside the archive root must be rejected before any bytes touch disk
 * (R7 boundary, `UNSAFE_ENTRY`).
 */
internal object ArchivePaths {
    fun normalize(path: String): String = path.trim('/', '\\').replace('\\', '/')

    fun isSafe(path: String): Boolean {
        val normalized = normalize(path)
        if (normalized.isEmpty()) return true
        if (path.startsWith("/") || path.startsWith("\\")) return false
        if (normalized.length >= 2 && normalized[1] == ':') return false
        return normalized.split('/').none { it == ".." }
    }

    /** Resolves [linkTarget] relative to the directory of [entryPath]; null if it escapes the root. */
    fun resolveSymlink(entryPath: String, linkTarget: String): String? {
        if (linkTarget.startsWith("/") || linkTarget.startsWith("\\")) return null
        val baseDir = normalize(entryPath).substringBeforeLast('/', "")
        val combined = if (baseDir.isEmpty()) linkTarget else "$baseDir/$linkTarget"
        val stack = ArrayDeque<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeLast()
                else -> stack.addLast(segment)
            }
        }
        return stack.joinToString("/")
    }

    fun isUnder(path: String, dir: String): Boolean {
        val normalizedDir = normalize(dir)
        val normalizedPath = normalize(path)
        if (normalizedDir.isEmpty()) return true
        return normalizedPath == normalizedDir || normalizedPath.startsWith("$normalizedDir/")
    }

    fun childName(path: String, dir: String): String {
        val normalizedDir = normalize(dir)
        val normalizedPath = normalize(path)
        val remainder = if (normalizedDir.isEmpty()) normalizedPath else normalizedPath.removePrefix("$normalizedDir/")
        return remainder.substringBefore('/')
    }
}
