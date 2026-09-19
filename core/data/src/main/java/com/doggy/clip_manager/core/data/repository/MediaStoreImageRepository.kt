package com.doggy.clip_manager.core.data.repository

import com.doggy.clip_manager.core.data.media.MediaRepository
import com.doggy.clip_manager.core.model.ImageAsset
import com.doggy.clip_manager.core.model.ImageSource
import com.doggy.clip_manager.core.model.MediaKind
import com.doggy.clip_manager.core.model.MediaQuery
import javax.inject.Inject

interface ImageRepository {
    suspend fun listImages(): List<ImageAsset>
}

internal class MediaStoreImageRepository @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ImageRepository {
    override suspend fun listImages(): List<ImageAsset> =
        mediaRepository.query(MediaQuery(setOf(MediaKind.IMAGE)))
            .map { ImageAsset(ImageSource(it.uri), it.displayName) }
}
