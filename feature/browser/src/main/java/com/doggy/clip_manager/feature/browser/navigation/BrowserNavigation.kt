package com.doggy.clip_manager.feature.browser.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.doggy.clip_manager.feature.browser.BrowserScreenRoute
import kotlinx.serialization.Serializable

@Serializable
data object BrowserRoute

fun NavGraphBuilder.browserScreen(onOpenVideo: (String) -> Unit) {
    composable<BrowserRoute> {
        BrowserScreenRoute(onOpenVideo = onOpenVideo)
    }
}
