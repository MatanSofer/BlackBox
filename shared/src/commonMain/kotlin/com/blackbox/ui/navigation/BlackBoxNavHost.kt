package com.blackbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.blackbox.ui.insights.InsightsScreen
import com.blackbox.ui.map.MapScreen
import com.blackbox.ui.onboarding.OnboardingScreen
import com.blackbox.ui.search.SearchScreen
import com.blackbox.ui.settings.SettingsScreen
import com.blackbox.ui.timeline.TimelineScreen

/**
 * Main navigation host for the BlackBox app.
 *
 * Defines routes for all five primary tabs and the onboarding screen,
 * each wired to its corresponding MVI screen composable.
 *
 * @param navController The [NavHostController] managing navigation state.
 * @param startDestination The initial screen route (onboarding or search).
 * @param onStartService Callback invoked when the background collection
 *   service should be started (triggered on onboarding completion).
 * @param onOpenUsageAccessSettings Callback to open the system Usage Access
 *   settings screen so the user can grant the special permission.
 * @param onRequestLocationPermission Callback to trigger the system location permission dialog.
 * @param onRequestActivityPermission Callback to trigger the activity recognition permission dialog.
 * @param onRequestNotificationPermission Callback to trigger the notification permission dialog.
 * @param modifier Optional [Modifier] for the NavHost container.
 */
@Composable
fun BlackBoxNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Search.route,
    onStartService: () -> Unit = {},
    onOpenUsageAccessSettings: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    onRequestActivityPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onRequestLocationPermission = onRequestLocationPermission,
                onRequestActivityPermission = onRequestActivityPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOnboardingComplete = {
                    onStartService()
                    navController.navigate(Screen.Search.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
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
