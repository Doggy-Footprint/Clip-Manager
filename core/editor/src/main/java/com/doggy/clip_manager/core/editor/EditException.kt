package com.doggy.clip_manager.core.editor

sealed class EditException(message: String?, cause: Throwable?) : Exception(message, cause)

class InvalidRangeException(message: String? = null) : EditException(message, null)

class InvalidEffectException(message: String? = null) : EditException(message, null)

class EmptyEditException(message: String? = null) : EditException(message, null)

class InputNotReadableException(message: String? = null, cause: Throwable? = null) :
    EditException(message, cause)

class UnsupportedStreamCopyException(message: String? = null) : EditException(message, null)

class EncodingFailedException(cause: Throwable) : EditException(cause.message, cause)
