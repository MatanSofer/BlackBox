package com.blackbox.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.blackbox.domain.model.map.LocationStay

/**
 * Platform-specific composable that renders an OSMDroid map with stay markers.
 *
 * Android: embeds an [org.osmdroid.views.MapView] via [AndroidView], with one
 * circular dot per [LocationStay]. Dot size scales with stay duration; the
 * selected stay is highlighted in neon magenta.
 *
 * iOS: stub placeholder (not yet implemented).
 *
 * @param stays Ordered list of location stays to render as dots.
 * @param selectedStay The currently selected stay, or null if none.
 * @param onStayTapped Called when the user taps a dot (the tapped stay) or
 *   the map background (null, to deselect).
 * @param modifier Modifier applied to the map container.
 */
@Composable
expect fun OsmMapView(
    stays: List<LocationStay>,
    selectedStay: LocationStay?,
    onStayTapped: (LocationStay?) -> Unit,
    modifier: Modifier = Modifier,
)
