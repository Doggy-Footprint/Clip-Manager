package com.doggy.clip_manager.core.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Stable
class StoragePermissionState internal constructor(granted: Boolean, private val launch: () -> Unit) {
    var granted by mutableStateOf(granted)
        internal set

    fun request() = launch()
}

/**
 * Launches the system permission flow once on first composition when access is missing:
 * MANAGE_EXTERNAL_STORAGE on API 30+ (no runtime dialog exists for it, only
 * a settings screen), READ_EXTERNAL_STORAGE runtime prompt on API <= 29.
 */
@Composable
fun rememberStoragePermissionState(): StoragePermissionState {
    val context = LocalContext.current
    lateinit var state: StoragePermissionState

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        state.granted = isStoragePermissionGranted(context)
    }
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> state.granted = isGranted }

    state = remember {
        StoragePermissionState(isStoragePermissionGranted(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                settingsLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } else {
                runtimePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!state.granted) state.request()
    }

    return state
}

private fun isStoragePermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}
