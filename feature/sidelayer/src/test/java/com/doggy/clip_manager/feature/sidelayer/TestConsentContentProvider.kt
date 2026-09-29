package com.doggy.clip_manager.feature.sidelayer

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException

/**
 * V3 (R13,C13): a content provider whose `delete` deterministically throws
 * `RecoverableSecurityException` (the API 29 consent path named in the spec Signatures
 * section) so OperationViewModel.consentRequest can be driven to non-null in a plain
 * Robolectric unit test, without needing a real MediaStore row or a device-level
 * MediaStore.createWriteRequest/createDeleteRequest (API 30+) round trip.
 *
 * [alwaysDenied] controls what a retried delete does after the first throw: `false`
 * (default) simulates the user granting consent, so the second call succeeds; `true`
 * keeps throwing, simulating sustained denial.
 */
class TestConsentContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.doggy.clip_manager.test.consent"
        private val storage = mutableMapOf<String, ByteArray>()
        private val deleteAttempts = mutableMapOf<String, Int>()
        var alwaysDenied: Boolean = false

        fun register(id: String, bytes: ByteArray): Uri {
            storage[id] = bytes
            return Uri.parse("content://$AUTHORITY/media/$id")
        }

        fun stillPresent(id: String): Boolean = storage.containsKey(id)

        fun reset() {
            storage.clear()
            deleteAttempts.clear()
            alwaysDenied = false
        }
    }

    override fun onCreate() = true

    private fun idOf(uri: Uri) = uri.lastPathSegment.orEmpty()

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val bytes = storage[idOf(uri)] ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf("${idOf(uri)}.bin", bytes.size))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = idOf(uri)
        val bytes = storage[id] ?: throw FileNotFoundException("no such content: $uri")
        val temp = java.io.File.createTempFile("consent-$id", ".bin")
        temp.deleteOnExit()
        temp.writeBytes(bytes)
        return ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = idOf(uri)
        val attempt = (deleteAttempts[id] ?: 0) + 1
        deleteAttempts[id] = attempt
        if (attempt == 1 || alwaysDenied) {
            val pendingIntent = PendingIntent.getActivity(
                context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE,
            )
            val remoteAction = RemoteAction(
                Icon.createWithResource(context, android.R.drawable.ic_dialog_alert),
                "Allow",
                "Allow deleting this item",
                pendingIntent,
            )
            throw RecoverableSecurityException(
                SecurityException("consent required to delete $id"),
                "consent required",
                remoteAction,
            )
        }
        storage.remove(id)
        return 1
    }

    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
