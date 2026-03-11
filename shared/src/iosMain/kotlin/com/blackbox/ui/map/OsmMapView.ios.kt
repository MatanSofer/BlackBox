package com.blackbox.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.blackbox.domain.model.map.LocationStay

@Composable
actual fun OsmMapView(
    stays: List<LocationStay>,
    selectedStay: LocationStay?,
    onStayTapped: (LocationStay?) -> Unit,
    playbackStay: LocationStay?,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Map not available on iOS yet")
    }
}
