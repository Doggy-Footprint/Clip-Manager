package com.doggy.clip_manager.feature.player.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.doggy.clip_manager.feature.player.PlayerScreenRoute
import kotlinx.serialization.Serializable

@Serializable
data class PlayerRoute(val path: String)

fun NavController.navigateToPlayer(path: String) = navigate(PlayerRoute(path))

fun NavGraphBuilder.playerScreen() {
    composable<PlayerRoute> {
        PlayerScreenRoute()
    }
}
