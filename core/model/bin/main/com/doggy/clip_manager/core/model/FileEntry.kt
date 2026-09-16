package com.doggy.clip_manager.core.model

import java.io.File

data class FileEntry(val file: File, val isDirectory: Boolean, val isVideo: Boolean)
