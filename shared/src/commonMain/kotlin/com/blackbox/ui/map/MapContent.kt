package com.blackbox.ui.map

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens

/**
 * Pure UI content for the Map screen.
 *
 * Displays a list of known places. The actual map view (OSMDroid)
 * will be integrated in a future step as it requires platform-specific code.
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
            text = "Known Places",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

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
                    title = "No Places Yet",
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
 * Card displaying a single known place.
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.PaddingCard),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconMd),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.width(Dimens.SpacingMd))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "${place.category.name} · ${place.visitCount} visits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
