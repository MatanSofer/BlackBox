package com.blackbox.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import com.blackbox.ui.theme.neonGlowBackground

/**
 * Pure UI content for the Map screen — cyberpunk known locations list.
 *
 * Displays a neon-styled "KNOWN LOCATIONS" header and a list of
 * known places with electric cyan neon borders. The actual map
 * (OSMDroid) will be integrated in a future step.
 *
 * @param state Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun MapContent(
    state: MapContract.State,
    onAction: (MapContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.PaddingScreen),
    ) {
        Text(
            text = "KNOWN LOCATIONS",
            style = MaterialTheme.typography.labelLarge,
            color = BlackBoxColors.NeonGreen,
            modifier = Modifier.padding(bottom = Dimens.SpacingMd),
        )

        when {
            state.isLoading -> {
                LoadingIndicator()
            }

            state.error != null -> {
                ErrorView(
                    message = state.error,
                    onRetry = { onAction(MapContract.Action.Refresh) },
                )
            }

            state.knownPlaces.isEmpty() -> {
                EmptyStateView(
                    title = "NO PLACES YET",
                    message = "Places will appear automatically as BlackBox detects your frequented locations.",
                )
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                ) {
                    items(state.knownPlaces, key = { it.id }) { place ->
                        PlaceCard(
                            place = place,
                            onClick = { onAction(MapContract.Action.PlaceClicked(place)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cyberpunk card displaying a single known place.
 *
 * Uses electric cyan neon border and accent color for the location icon.
 *
 * @param place The known place data.
 * @param onClick Callback when the card is tapped.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun PlaceCard(
    place: KnownPlace,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .neonGlowBackground(BlackBoxColors.ElectricCyanFaint)
            .neonBorder(color = BlackBoxColors.ElectricCyan, cornerRadius = 4.dp)
            .padding(Dimens.PaddingCard)
            .then(Modifier.clickable(onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconMd),
            tint = BlackBoxColors.ElectricCyan,
        )

        Spacer(modifier = Modifier.width(Dimens.SpacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                color = BlackBoxColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingXxs))

            Row {
                // Category badge chip
                Text(
                    text = place.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.ElectricCyan,
                    modifier = Modifier
                        .neonBorder(
                            color = BlackBoxColors.ElectricCyan,
                            cornerRadius = 2.dp,
                        )
                        .padding(
                            horizontal = Dimens.SpacingXs,
                            vertical = 1.dp,
                        ),
                )
                Spacer(modifier = Modifier.width(Dimens.SpacingSm))
                Text(
                    text = "${place.visitCount} visits",
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun MapContentEmptyPreview() {
    BlackBoxTheme {
        MapContent(
            state = MapContract.State(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapContentWithPlacesPreview() {
    BlackBoxTheme {
        MapContent(
            state = MapContract.State(
                knownPlaces = listOf(
                    KnownPlace(
                        id = 1,
                        name = "Home",
                        latitude = 32.0853,
                        longitude = 34.7818,
                        radiusMeters = 100.0,
                        category = PlaceCategory.HOME,
                        visitCount = 150,
                        isAutoDetected = true,
                        firstVisit = 1709000000000L,
                        lastVisit = 1740300000000L,
                    ),
                    KnownPlace(
                        id = 2,
                        name = "Office",
                        latitude = 32.0700,
                        longitude = 34.7900,
                        radiusMeters = 100.0,
                        category = PlaceCategory.WORK,
                        visitCount = 85,
                        isAutoDetected = true,
                        firstVisit = 1709100000000L,
                        lastVisit = 1740280000000L,
                    ),
                    KnownPlace(
                        id = 3,
                        name = "Gym",
                        latitude = 32.0800,
                        longitude = 34.7850,
                        radiusMeters = 50.0,
                        category = PlaceCategory.GYM,
                        visitCount = 30,
                        isAutoDetected = false,
                        firstVisit = 1709200000000L,
                        lastVisit = 1740250000000L,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
