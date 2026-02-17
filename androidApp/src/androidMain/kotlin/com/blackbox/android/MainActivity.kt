package com.blackbox.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blackbox.android.ui.navigation.BlackBoxBottomBar
import com.blackbox.android.ui.navigation.BlackBoxNavHost
import com.blackbox.android.ui.navigation.Screen
import com.blackbox.android.ui.navigation.defaultBottomNavItems
import com.blackbox.android.ui.theme.BlackBoxTheme

/**
 * Single activity for the BlackBox app.
 *
 * Sets up edge-to-edge display, applies [BlackBoxTheme], and hosts
 * the [Scaffold] with bottom navigation and [BlackBoxNavHost].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BlackBoxTheme {
                BlackBoxApp()
            }
        }
    }
}

/**
 * Root composable for the BlackBox app.
 *
 * Manages the [NavController], bottom navigation state, and
 * wires the [BlackBoxNavHost] with the [BlackBoxBottomBar].
 */
@Composable
private fun BlackBoxApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = defaultBottomNavItems(
        searchLabel = stringResource(R.string.tab_search),
        timelineLabel = stringResource(R.string.tab_timeline),
        mapLabel = stringResource(R.string.tab_map),
        insightsLabel = stringResource(R.string.tab_insights),
        settingsLabel = stringResource(R.string.tab_settings),
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
        BlackBoxNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
