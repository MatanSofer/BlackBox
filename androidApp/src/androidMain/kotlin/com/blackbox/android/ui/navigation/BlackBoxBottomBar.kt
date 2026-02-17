package com.blackbox.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.blackbox.android.ui.theme.Dimens

/**
 * Represents a tab item in the bottom navigation bar.
 *
 * @property screen The navigation destination for this tab.
 * @property icon The Material icon displayed for this tab.
 * @property label The display label shown below the icon.
 */
data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

/**
 * BlackBox bottom navigation bar with 5 primary tabs.
 *
 * Highlights the currently selected tab and invokes [onTabSelected]
 * when the user taps a different tab.
 *
 * @param currentRoute The route string of the currently active screen.
 * @param onTabSelected Callback invoked with the selected [Screen] route.
 * @param modifier Optional [Modifier] for the navigation bar.
 * @param items The list of navigation tab items to display.
 */
@Composable
fun BlackBoxBottomBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    items: List<BottomNavItem>,
) {
    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = { onTabSelected(item.screen.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(text = item.label) },
            )
        }
    }
}

/**
 * Creates the default list of bottom navigation items.
 *
 * Uses string labels directly here; these will be replaced with
 * `stringResource()` calls when wired into the actual screen composable.
 */
fun defaultBottomNavItems(
    searchLabel: String,
    timelineLabel: String,
    mapLabel: String,
    insightsLabel: String,
    settingsLabel: String,
): List<BottomNavItem> = listOf(
    BottomNavItem(Screen.Search, Icons.Default.Search, searchLabel),
    BottomNavItem(Screen.Timeline, Icons.Default.DateRange, timelineLabel),
    BottomNavItem(Screen.Map, Icons.Default.LocationOn, mapLabel),
    BottomNavItem(Screen.Insights, Icons.Default.Star, insightsLabel),
    BottomNavItem(Screen.Settings, Icons.Default.Settings, settingsLabel),
)
