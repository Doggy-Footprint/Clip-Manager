package com.doggy.clip_manager.core.data.operation

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Minimal registered content provider used only as a V2/V10-adjacent test double for
 * MediaStore-style path-less URI sources: no on-disk backing beyond a temp copy handed
 * out per openFile call, so it exercises FileOperationRepository's Content-source branch
 * through the real ContentResolver rather than a mocked interface.
 */
class TestMediaContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.doggy.clip_manager.test.media"
        private val storage = mutableMapOf<String, Pair<String, ByteArray>>()
        private val deniedIds = mutableSetOf<String>()

        fun register(id: String, displayName: String, bytes: ByteArray): Uri {
            storage[id] = displayName to bytes
            return Uri.parse("content://$AUTHORITY/media/$id")
        }

        fun registerPermissionDenied(id: String): Uri {
            deniedIds += id
            storage[id] = "denied.bin" to ByteArray(0)
            return Uri.parse("content://$AUTHORITY/media/$id")
        }

        fun missingUri(id: String): Uri = Uri.parse("content://$AUTHORITY/media/$id")

        fun reset() {
            storage.clear()
            deniedIds.clear()
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
        val entry = storage[idOf(uri)] ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf(entry.first, entry.second.size))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = idOf(uri)
        if (id in deniedIds) throw SecurityException("permission denied for $id")
        val entry = storage[id] ?: throw FileNotFoundException("no such content: $uri")
        val temp = File.createTempFile("provider-$id", ".bin")
        temp.deleteOnExit()
        temp.writeBytes(entry.second)
        return ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
