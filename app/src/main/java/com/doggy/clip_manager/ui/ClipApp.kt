package com.doggy.clip_manager.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import com.doggy.clip_manager.navigation.ClipNavHost
import com.doggy.clip_manager.navigation.TopLevelDestination

@Composable
fun ClipApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val topLevel = TopLevelDestination.entries.firstOrNull { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
    }

    NavigationSuiteScaffold(
        layoutType = if (currentDestination != null && topLevel == null) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        },
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { dest ->
                val selected = dest == topLevel
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = null)
                    },
                    label = { Text(stringResource(dest.labelRes)) },
                )
            }
        },
    ) {
        ClipNavHost(navController = navController)
    }
}
