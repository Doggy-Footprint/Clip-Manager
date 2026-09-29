package com.doggy.clip_manager.feature.mainlayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ViewerScreen(
    source: FileOperationSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    var content by remember(source) { mutableStateOf<ViewerContent?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(source) { content = viewModel.resolve(source) }

    Box(modifier.fillMaxSize()) {
        when (val current = content) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is ViewerContent.Error -> Text(current.message, modifier = Modifier.align(Alignment.Center))
            is ViewerContent.Video -> PlayerPane(path = current.path, isFullscreen = false, onToggleFullscreen = {})
            is ViewerContent.Audio -> AudioPlayerPane(path = current.path, modifier = Modifier.fillMaxSize())
            is ViewerContent.ImageUri -> ImageViewerPane(uri = current.uri)
            is ViewerContent.Pdf -> PdfViewerContent(path = current.path)
            is ViewerContent.Text -> Text(
                text = current.content,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
            is ViewerContent.Cbz -> CbzViewerContent(
                archive = current.archive,
                entries = current.entries,
                password = current.password,
                viewModel = viewModel,
            )
            is ViewerContent.CbzPasswordRequired -> {
                var input by remember(current.archive) { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = onClose,
                    title = { Text("암호") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                            )
                            if (current.wrong) Text("암호가 올바르지 않습니다")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { scope.launch { content = viewModel.resolve(source, input.toCharArray()) } }) {
                            Text("확인")
                        }
                    },
                    dismissButton = { TextButton(onClick = onClose) { Text("취소") } },
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(ClipIcons.ArrowBack, contentDescription = null)
        }
    }
}

@Composable
private fun PdfViewerContent(path: String) {
    var pageIndex by remember(path) { mutableIntStateOf(0) }
    var pageCount by remember(path) { mutableIntStateOf(0) }
    var bitmap by remember(path, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path, pageIndex) {
        val (bmp, count) = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val count = renderer.pageCount
                    val safeIndex = pageIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
                    val rendered = if (count > 0) {
                        renderer.openPage(safeIndex).use { page ->
                            Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).also {
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    } else null
                    rendered to count
                }
            }
        }
        bitmap = bmp
        pageCount = count
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { if (pageIndex > 0) pageIndex-- }, enabled = pageIndex > 0) {
                Icon(ClipIcons.SkipPrevious, contentDescription = null)
            }
            Text("${pageIndex + 1}/$pageCount", modifier = Modifier.align(Alignment.CenterVertically))
            IconButton(onClick = { if (pageIndex < pageCount - 1) pageIndex++ }, enabled = pageIndex < pageCount - 1) {
                Icon(ClipIcons.SkipNext, contentDescription = null)
            }
        }
    }
}

@Composable
private fun CbzViewerContent(archive: File, entries: List<String>, password: CharArray?, viewModel: ViewerViewModel) {
    var pageIndex by remember(archive) { mutableIntStateOf(0) }
    var bitmap by remember(archive, pageIndex) { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(archive, pageIndex, entries) {
        bitmap = if (entries.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = viewModel.readEntryBytes(archive, entries[pageIndex.coerceIn(0, entries.size - 1)], password)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (entries.isEmpty()) {
            Text("이미지가 없습니다")
            return@Box
        }
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { if (pageIndex > 0) pageIndex-- }, enabled = pageIndex > 0) {
                Icon(ClipIcons.SkipPrevious, contentDescription = null)
            }
            Text("${pageIndex + 1}/${entries.size}", modifier = Modifier.align(Alignment.CenterVertically))
            IconButton(onClick = { if (pageIndex < entries.size - 1) pageIndex++ }, enabled = pageIndex < entries.size - 1) {
                Icon(ClipIcons.SkipNext, contentDescription = null)
            }
        }
    }
}
