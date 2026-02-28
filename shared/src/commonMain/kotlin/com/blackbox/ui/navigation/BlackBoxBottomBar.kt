package com.blackbox.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.Dimens

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
 * BlackBox cyberpunk bottom navigation bar with neon highlights.
 *
 * Selected tab shows icon + label in [BlackBoxColors.NeonGreen] with
 * an animated neon underline indicator. Background extends into the
 * system navigation bar area via [navigationBarsPadding] so content
 * is never hidden behind gesture/button bars.
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
    Surface(
        color = BlackBoxColors.Surface,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Neon top border line
                drawLine(
                    color = BlackBoxColors.OutlineNeon,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = Dimens.NeonBorderWidth.toPx(),
                )
            },
    ) {
        // heightIn(min) ensures tabs have enough room; navigationBarsPadding adds
        // bottom inset so content isn't hidden behind the system nav bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.BottomBarHeight)
                .navigationBarsPadding()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.screen.route
                val iconColor = if (selected) BlackBoxColors.NeonGreen else BlackBoxColors.TextMuted
                val labelColor = if (selected) BlackBoxColors.NeonGreen else BlackBoxColors.TextMuted

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.BottomBarHeight)
                        .padding(top = Dimens.SpacingXs)
                        .noRippleClickable { onTabSelected(item.screen.route) },
                ) {
                    // Animated neon underline indicator at top of tab
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (selected) 24.dp else 0.dp,
                        animationSpec = tween(durationMillis = 200),
                        label = "neon_indicator_${item.screen.route}",
                    )
                    Box(
                        modifier = Modifier
                            .width(indicatorWidth)
                            .height(Dimens.BottomBarNeonIndicatorHeight)
                            .background(BlackBoxColors.NeonGreen),
                    )

                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = iconColor,
                        modifier = Modifier
                            .size(Dimens.IconMd)
                            .padding(top = Dimens.SpacingXs),
                    )

                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Creates the default list of bottom navigation items.
 *
 * @param searchLabel Label for the Search tab.
 * @param timelineLabel Label for the Timeline tab.
 * @param mapLabel Label for the Map tab.
 * @param insightsLabel Label for the Insights tab.
 * @param settingsLabel Label for the Settings tab.
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

/** Modifier helper: click without ripple effect. */
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        indication = null,
        interactionSource = null,
        onClick = onClick,
    )
