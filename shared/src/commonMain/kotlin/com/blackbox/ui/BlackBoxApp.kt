package com.blackbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.tab_insights
import blackbox.shared.generated.resources.tab_map
import blackbox.shared.generated.resources.tab_search
import blackbox.shared.generated.resources.tab_settings
import blackbox.shared.generated.resources.tab_timeline
import com.blackbox.ui.navigation.BlackBoxBottomBar
import com.blackbox.ui.navigation.BlackBoxNavHost
import com.blackbox.ui.navigation.Screen
import com.blackbox.ui.navigation.defaultBottomNavItems
import org.jetbrains.compose.resources.stringResource

/**
 * Root composable for the BlackBox app.
 *
 * Manages the [NavController][androidx.navigation.NavController],
 * bottom navigation state, and wires the [BlackBoxNavHost] with
 * the [BlackBoxBottomBar]. Lives in the shared module so it can
 * be reused across platforms (Android, iOS).
 *
 * Shows a themed splash background while the onboarding state
 * is being loaded (null), preventing a flash of the wrong screen.
 *
 * @param isOnboardingComplete Whether the user has completed onboarding,
 *   or null if still loading.
 * @param onStartService Platform callback invoked when the background
 *   collection service should be started (after onboarding completes).
 * @param onOpenUsageAccessSettings Platform callback to open the system
 *   Usage Access settings screen during onboarding.
 * @param onRequestLocationPermission Platform callback to show the location permission dialog.
 * @param onRequestActivityPermission Platform callback to show the activity recognition dialog.
 * @param onRequestNotificationPermission Platform callback to show the notification permission dialog.
 * @param onOpenAppSettings Platform callback invoked from Settings when the user tries to enable
 *   a collector whose permission is not yet granted. Opens the system app settings page so
 *   the user can grant the permission — avoids Android's two-strikes permanent-denial rule.
 */
@Composable
fun BlackBoxApp(
    isOnboardingComplete: Boolean? = true,
    onStartService: () -> Unit = {},
    onOpenUsageAccessSettings: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    onRequestActivityPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
) {
    if (isOnboardingComplete == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            // Empty themed background while loading — avoids white flash
        }
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = defaultBottomNavItems(
        searchLabel = stringResource(Res.string.tab_search),
        timelineLabel = stringResource(Res.string.tab_timeline),
        mapLabel = stringResource(Res.string.tab_map),
        insightsLabel = stringResource(Res.string.tab_insights),
        settingsLabel = stringResource(Res.string.tab_settings),
    )

    // Only show bottom bar on primary tab screens
    val primaryRoutes = setOf(
        Screen.Search.route,
        Screen.Timeline.route,
        Screen.Map.route,
        Screen.Insights.route,
        Screen.Settings.route,
    )
    val showBottomBar = currentRoute in primaryRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                BlackBoxBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    items = bottomNavItems,
                )
            }
        },
    ) { innerPadding ->
        val startDest = if (isOnboardingComplete) Screen.Search.route else Screen.Onboarding.route
        BlackBoxNavHost(
            navController = navController,
            startDestination = startDest,
            onStartService = onStartService,
            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            onRequestLocationPermission = onRequestLocationPermission,
            onRequestActivityPermission = onRequestActivityPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenAppSettings = onOpenAppSettings,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
