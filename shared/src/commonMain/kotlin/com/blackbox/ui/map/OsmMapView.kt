package com.blackbox.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.blackbox.domain.model.map.LocationStay

/**
 * Platform-specific composable that renders an OSMDroid map with stay markers.
 *
 * Android: embeds an [org.osmdroid.views.MapView] via [AndroidView], with one
 * circular dot per [LocationStay]. Dot size scales with stay duration; the
 * selected stay is highlighted in rose.
 *
 * iOS: stub placeholder (not yet implemented).
 *
 * @param stays Ordered list of location stays to render as dots.
 * @param selectedStay The currently selected stay, or null if none.
 * @param onStayTapped Called when the user taps a dot (the tapped stay) or
 *   the map background (null, to deselect).
 * @param playbackStay When route playback is active, the stay the camera should
 *   animate to. Distinct from [selectedStay] to allow smooth camera animation
 *   without triggering a full bounds-fit.
 * @param modifier Modifier applied to the map container.
 */
@Composable
expect fun OsmMapView(
    stays: List<LocationStay>,
    selectedStay: LocationStay?,
    onStayTapped: (LocationStay?) -> Unit,
    playbackStay: LocationStay? = null,
    modifier: Modifier = Modifier,
)
