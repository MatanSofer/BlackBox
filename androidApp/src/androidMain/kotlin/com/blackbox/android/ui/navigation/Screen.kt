package com.blackbox.android.ui.navigation

/**
 * Sealed class defining all navigation destinations in the app.
 *
 * Each screen has a unique [route] string used by the Navigation component.
 * Bottom navigation tabs are the five primary destinations.
 *
 * @property route The navigation route string for this destination.
 */
sealed class Screen(val route: String) {

    /** Search / home screen — natural language query interface. */
    data object Search : Screen("search")

    /** Timeline screen — chronological day view of all events. */
    data object Timeline : Screen("timeline")

    /** Map screen — location history on an offline map. */
    data object Map : Screen("map")

    /** Insights screen — patterns and statistical insights. */
    data object Insights : Screen("insights")

    /** Settings screen — collector toggles, data management, privacy. */
    data object Settings : Screen("settings")

    /** Onboarding screen — first-launch experience. */
    data object Onboarding : Screen("onboarding")
}
