package com.doggy.clip_manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.doggy.clip_manager.ui.AppNavigation
import com.doggy.clip_manager.ui.rememberStoragePermissionGranted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                ClipManagerRoot(onExit = { finish() })
            }
        }
    }
}

@Composable
private fun ClipManagerRoot(onExit: () -> Unit) {
    val granted = rememberStoragePermissionGranted()
    if (granted) {
        AppNavigation(onExit = onExit)
    } else {
        Text("Waiting for storage permission")
    }
}
