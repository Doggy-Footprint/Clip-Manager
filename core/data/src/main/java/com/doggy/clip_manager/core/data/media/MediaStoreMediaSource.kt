package com.doggy.clip_manager.core.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.doggy.clip_manager.core.model.MediaEntry
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.core.model.MediaQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class MediaStoreMediaSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaSource {
    override suspend fun query(query: MediaQuery): List<MediaEntry> = withContext(Dispatchers.IO) {
        query.kinds.flatMap { kind -> queryKind(kind) }
    }

    private fun queryKind(kind: MediaKind): List<MediaEntry> {
        val collection = collectionOf(kind)
        // BUCKET_DISPLAY_NAME and DURATION only exist on MediaColumns from API 29; querying a
        // column the provider does not know throws instead of returning null.
        val extendedColumns = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            if (extendedColumns) {
                add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                if (kind != MediaKind.IMAGE) add(MediaStore.MediaColumns.DURATION)
            }
        }.toTypedArray()
        return context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val bucketIndex = if (extendedColumns) cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME) else -1
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val durationIndex = if (extendedColumns) cursor.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MediaEntry(
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)).toString(),
                            filePath = dataIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString),
                            displayName = cursor.getString(nameIndex).orEmpty(),
                            kind = kind,
                            bucketName = bucketIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString),
                            dateModifiedSeconds = cursor.getLong(modifiedIndex),
                            durationMs = durationIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun collectionOf(kind: MediaKind): Uri = when (kind) {
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}
