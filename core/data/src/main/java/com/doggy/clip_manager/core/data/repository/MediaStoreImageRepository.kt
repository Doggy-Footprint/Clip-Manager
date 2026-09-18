package com.doggy.clip_manager.core.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.doggy.clip_manager.core.model.ImageAsset
import com.doggy.clip_manager.core.model.ImageSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface ImageRepository {
    suspend fun listImages(): List<ImageAsset>
}

internal class MediaStoreImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImageRepository {
    private val contentResolver: ContentResolver get() = context.contentResolver

    override suspend fun listImages(): List<ImageAsset> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    add(ImageAsset(ImageSource(uri.toString()), cursor.getString(nameIndex).orEmpty()))
                }
            }
        }.orEmpty()
    }
}
