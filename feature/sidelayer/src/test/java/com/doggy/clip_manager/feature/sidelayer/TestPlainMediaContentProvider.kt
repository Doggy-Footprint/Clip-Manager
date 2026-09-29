package com.doggy.clip_manager.feature.sidelayer

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
 * V1 (C16, coverage item 7, media-grid variant): a content provider whose `delete` always
 * succeeds immediately - unlike `TestConsentContentProvider`, which exists specifically to
 * exercise the consent-required path (V3). This one lets the media-grid delete test observe
 * a plain successful deletion without also having to drive the consent flow.
 */
class TestPlainMediaContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.doggy.clip_manager.test.plainmedia"
        private val storage = mutableMapOf<String, ByteArray>()

        fun register(id: String, bytes: ByteArray): Uri {
            storage[id] = bytes
            return Uri.parse("content://$AUTHORITY/media/$id")
        }

        fun stillPresent(id: String): Boolean = storage.containsKey(id)

        fun reset() = storage.clear()
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
        val temp = File.createTempFile("plainmedia-$id", ".bin")
        temp.deleteOnExit()
        temp.writeBytes(bytes)
        return ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = idOf(uri)
        return if (storage.remove(id) != null) 1 else 0
    }

    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
