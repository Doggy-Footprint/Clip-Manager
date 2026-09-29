package com.doggy.clip_manager.core.data.operation

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Thrown when a Content (MediaStore) write/delete needs a system consent dialog before it can
 * proceed (API 29 `RecoverableSecurityException`, API 30+ `MediaStore.createWriteRequest`/
 * `createDeleteRequest`). The UI launches [intentSender] and reports the result back through
 * `OperationViewModel.onConsentResult`.
 */
class ConsentRequiredException(val intentSender: IntentSender) : Exception()

private fun consentSenderFor(resolver: ContentResolver, uri: Uri, cause: SecurityException): IntentSender {
    if (cause is RecoverableSecurityException) return cause.userAction.actionIntent.intentSender
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender
    }
    throw cause
}

/**
 * Deleting a MediaStore row owned by another app throws [SecurityException] instead of failing
 * silently; that is converted into [ConsentRequiredException] so the caller can request consent
 * and retry, rather than being reported as a plain I/O failure.
 */
internal fun ContentResolver.deleteWithConsent(uri: Uri) {
    try {
        delete(uri, null, null)
    } catch (e: SecurityException) {
        throw ConsentRequiredException(consentSenderFor(this, uri, e))
    }
}
