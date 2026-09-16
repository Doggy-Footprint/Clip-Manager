package com.doggy.clip_manager.ui

import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private const val ROUTE_BROWSER = "browser"
private const val ROUTE_PLAYER = "player/{path}"

@Composable
fun AppNavigation(onExit: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_BROWSER) {
        composable(ROUTE_BROWSER) {
            BrowserScreen(
                onOpenVideo = { path ->
                    navController.navigate("player/${Uri.encode(path)}")
                },
                onExit = onExit
            )
        }
        composable(
            ROUTE_PLAYER,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path")
            if (encodedPath != null) {
                PlayerScreen(path = Uri.decode(encodedPath))
            } else {
                Surface { Text("Invalid path") }
            }
        }
    }
}
