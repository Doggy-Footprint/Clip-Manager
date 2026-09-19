package com.doggy.clip_manager.feature.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.doggy.clip_manager.core.data.repository.ImageRepository
import com.doggy.clip_manager.core.model.ImageAsset
import com.doggy.clip_manager.core.ui.rememberStoragePermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageGridViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
) : ViewModel() {
    private val mutableImages = MutableStateFlow<List<ImageAsset>>(emptyList())
    val images: StateFlow<List<ImageAsset>> = mutableImages.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            mutableImages.value = runCatching { imageRepository.listImages() }.getOrDefault(emptyList())
        }
    }
}

/** Explorer-side source list for the editor: picking an image hands it back for an image overlay. */
@Composable
fun ImageGridRoute(
    onImageSelected: (ImageAsset) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageGridViewModel = hiltViewModel(),
) {
    val permission = rememberStoragePermissionState()
    val images by viewModel.images.collectAsStateWithLifecycle()
    LaunchedEffect(permission.granted) {
        if (permission.granted) viewModel.reload()
    }
    ImageGridScreen(images = images, onImageSelected = onImageSelected, modifier = modifier)
}
