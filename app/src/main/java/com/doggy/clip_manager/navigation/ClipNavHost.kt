package com.doggy.clip_manager.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.doggy.clip_manager.R
import com.doggy.clip_manager.core.designsystem.component.EmptyState
import com.doggy.clip_manager.core.designsystem.icon.ClipIcons
import com.doggy.clip_manager.feature.browser.navigation.BrowserRoute
import com.doggy.clip_manager.feature.browser.navigation.browserScreen
import com.doggy.clip_manager.feature.player.navigation.navigateToPlayer
import com.doggy.clip_manager.feature.player.navigation.playerScreen

@Composable
fun ClipNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = BrowserRoute, modifier = modifier) {
        browserScreen(onOpenVideo = navController::navigateToPlayer)
        composable<TagsRoute> {
            EmptyState(
                icon = ClipIcons.Tag,
                title = stringResource(R.string.tags_empty_title),
                body = stringResource(R.string.tags_empty_body),
            )
        }
        playerScreen()
    }
}
