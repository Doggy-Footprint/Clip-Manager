package com.doggy.clip_manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.doggy.clip_manager.core.designsystem.theme.ClipTheme
import com.doggy.clip_manager.ui.ClipApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ClipTheme {
                ClipApp()
            }
        }
    }
}
