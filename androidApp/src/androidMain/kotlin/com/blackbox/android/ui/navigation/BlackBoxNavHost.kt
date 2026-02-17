package com.blackbox.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/**
 * Main navigation host for the BlackBox app.
 *
 * Defines routes for all five primary tabs. Each tab currently renders
 * a placeholder screen that will be replaced with full implementations
 * in later development steps.
 *
 * @param navController The [NavHostController] managing navigation state.
 * @param modifier Optional [Modifier] for the NavHost container.
 */
@Composable
fun BlackBoxNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Search.route,
        modifier = modifier,
    ) {
        composable(Screen.Search.route) {
            PlaceholderScreen(title = "Search")
        }
        composable(Screen.Timeline.route) {
            PlaceholderScreen(title = "Timeline")
        }
        composable(Screen.Map.route) {
            PlaceholderScreen(title = "Map")
        }
        composable(Screen.Insights.route) {
            PlaceholderScreen(title = "Insights")
        }
        composable(Screen.Settings.route) {
            PlaceholderScreen(title = "Settings")
        }
    }
}

/**
 * Temporary placeholder screen displayed for each tab.
 *
 * Shows the screen name centered on a full-size surface.
 * Will be replaced with actual screen implementations in subsequent steps.
 *
 * @param title The name of the screen to display.
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
