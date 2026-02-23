package com.blackbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.blackbox.ui.insights.InsightsScreen
import com.blackbox.ui.map.MapScreen
import com.blackbox.ui.search.SearchScreen
import com.blackbox.ui.settings.SettingsScreen
import com.blackbox.ui.timeline.TimelineScreen

/**
 * Main navigation host for the BlackBox app.
 *
 * Defines routes for all five primary tabs, each wired to its
 * corresponding MVI screen composable.
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
            SearchScreen()
        }
        composable(Screen.Timeline.route) {
            TimelineScreen()
        }
        composable(Screen.Map.route) {
            MapScreen()
        }
        composable(Screen.Insights.route) {
            InsightsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
