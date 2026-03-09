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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * @property icon   The Material icon displayed for this tab.
 * @property label  The display label shown below the icon.
 */
data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

/**
 * BlackBox Obsidian bottom navigation bar.
 *
 * Selected tab shows the icon and label in [BlackBoxColors.Indigo] with an animated
 * pill indicator that slides underneath the icon. Unselected tabs use [BlackBoxColors.TextTertiary].
 * A top border divides the bar from the screen content.
 *
 * @param currentRoute  The route string of the currently active screen.
 * @param onTabSelected Callback invoked with the selected [Screen] route.
 * @param modifier      Optional [Modifier] for the navigation bar.
 * @param items         The list of navigation tab items to display.
 */
@Composable
fun BlackBoxBottomBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    items: List<BottomNavItem>,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.Surface)
            .drawBehind {
                // Top separator line
                drawLine(
                    color = BlackBoxColors.Border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.BottomBarHeight)
                .navigationBarsPadding()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.screen.route
                val iconColor = if (selected) BlackBoxColors.Indigo else BlackBoxColors.TextTertiary
                val labelColor = if (selected) BlackBoxColors.Indigo else BlackBoxColors.TextTertiary

                // Animate the pill indicator width
                val pillWidth by animateDpAsState(
                    targetValue = if (selected) 40.dp else 0.dp,
                    animationSpec = tween(durationMillis = 220),
                    label = "pill_${item.screen.route}",
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.BottomBarHeight)
                        .clickable(
                            indication = null,
                            interactionSource = null,
                            onClick = { onTabSelected(item.screen.route) },
                        ),
                ) {
                    // Pill indicator above icon
                    Box(
                        modifier = Modifier
                            .width(pillWidth)
                            .height(3.dp)
                            .background(
                                BlackBoxColors.Indigo,
                                RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp),
                            ),
                    )

                    Box(modifier = Modifier.size(2.dp)) // small gap

                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = iconColor,
                        modifier = Modifier
                            .size(Dimens.IconMd)
                            .padding(top = 4.dp),
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
