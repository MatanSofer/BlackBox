package com.blackbox.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blackbox.domain.model.map.DayLocationSummary
import com.blackbox.domain.model.map.LocationStay
import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.common.ObsidianDatePickerDialog
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.obsidianCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure UI content for the Map screen.
 *
 * Supports two modes:
 * - [MapContract.MapMode.DAY]: full-screen OSMDroid map with date navigation,
 *   stay dots, and route-playback controls.
 * - [MapContract.MapMode.PLACES]: scrollable list of all known places with a
 *   mini-map icon per entry.
 *
 * @param state    Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun MapContent(
    state: MapContract.State,
    onAction: (MapContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Date picker dialog — shown as an overlay when triggered
    if (state.showDatePicker) {
        ObsidianDatePickerDialog(
            selectedDate = state.selectedDate,
            availableDates = state.availableDates,
            onDateSelected = { date -> onAction(MapContract.Action.DateSelected(date)) },
            onDismiss = { onAction(MapContract.Action.DismissDatePicker) },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBoxColors.Background),
    ) {
        when (state.mapMode) {
            // ── DAY mode: full-screen map with overlaid controls ───────────────
            MapContract.MapMode.DAY -> {
                // Layer 1: map or state placeholder
                when {
                    state.isLoading -> LoadingIndicator()
                    state.error != null -> ErrorView(
                        message = state.error,
                        onRetry = { onAction(MapContract.Action.Refresh) },
                    )
                    state.summary == null || state.summary.stays.isEmpty() -> EmptyStateView(
                        title = "No Location Data",
                        message = "No location data for this day.\nMake sure Location collection is enabled.",
                    )
                    else -> {
                        val playbackStay = if (state.isPlayingRoute)
                            state.summary.stays.getOrNull(state.playbackIndex) else null
                        OsmMapView(
                            stays = state.summary.stays,
                            selectedStay = state.selectedStay,
                            onStayTapped = { stay -> onAction(MapContract.Action.StayTapped(stay)) },
                            playbackStay = playbackStay,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Layer 2: controls overlay
                Column(modifier = Modifier.fillMaxSize()) {
                    MapTopBar(
                        mapMode = state.mapMode,
                        onSwitchMode = { onAction(MapContract.Action.SwitchMode(it)) },
                    )
                    MapDateBar(
                        selectedDate = state.selectedDate,
                        canPlay = (state.summary?.stays?.size ?: 0) >= 2,
                        isPlaying = state.isPlayingRoute,
                        onPrevious = { onAction(MapContract.Action.PreviousDay) },
                        onNext = { onAction(MapContract.Action.NextDay) },
                        onOpenPicker = { onAction(MapContract.Action.ShowDatePicker) },
                        onPlayPause = {
                            if (state.isPlayingRoute) onAction(MapContract.Action.StopRoutePlayback)
                            else onAction(MapContract.Action.StartRoutePlayback)
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    MapBottomPanel(
                        summary = state.summary,
                        selectedStay = state.selectedStay,
                        onDismiss = { onAction(MapContract.Action.StayTapped(null)) },
                    )
                }
            }

            // ── PLACES mode: scrollable known-places list ──────────────────────
            MapContract.MapMode.PLACES -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        MapTopBar(
                            mapMode = state.mapMode,
                            onSwitchMode = { onAction(MapContract.Action.SwitchMode(it)) },
                        )
                        when {
                            state.isLoadingPlaces -> LoadingIndicator()
                            state.places.isEmpty() -> EmptyStateView(
                                title = "No Places Yet",
                                message = "Tap + to add a place manually,\nor keep BlackBox running to auto-detect.",
                            )
                            else -> PlacesList(
                                places = state.places,
                                onDelete = { onAction(MapContract.Action.DeletePlace(it)) },
                                onEdit = { onAction(MapContract.Action.OpenEditPlaceDialog(it)) },
                            )
                        }
                    }

                    // FAB — bottom-end corner
                    FloatingActionButton(
                        onClick = { onAction(MapContract.Action.OpenAddPlaceDialog) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Dimens.SpacingLg),
                        containerColor = BlackBoxColors.Indigo,
                        contentColor = BlackBoxColors.TextPrimary,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add place",
                        )
                    }
                }

                // Add Place dialog — shown as overlay
                state.addPlaceDialog?.let { dialog ->
                    AddPlaceDialog(
                        dialog = dialog,
                        onNameChanged = { onAction(MapContract.Action.AddPlaceNameChanged(it)) },
                        onCategoryChanged = { onAction(MapContract.Action.AddPlaceCategoryChanged(it)) },
                        onLatChanged = { onAction(MapContract.Action.AddPlaceLatChanged(it)) },
                        onLngChanged = { onAction(MapContract.Action.AddPlaceLngChanged(it)) },
                        onRadiusChanged = { onAction(MapContract.Action.AddPlaceRadiusChanged(it)) },
                        onConfirm = { onAction(MapContract.Action.ConfirmAddPlace) },
                        onDismiss = { onAction(MapContract.Action.DismissAddPlaceDialog) },
                    )
                }

                // Edit Place dialog — shown as overlay
                state.editPlaceDialog?.let { dialog ->
                    EditPlaceDialog(
                        dialog = dialog,
                        onNameChanged = { onAction(MapContract.Action.EditPlaceNameChanged(it)) },
                        onCategoryChanged = { onAction(MapContract.Action.EditPlaceCategoryChanged(it)) },
                        onRadiusChanged = { onAction(MapContract.Action.EditPlaceRadiusChanged(it)) },
                        onConfirm = { onAction(MapContract.Action.ConfirmEditPlace) },
                        onDismiss = { onAction(MapContract.Action.DismissEditPlaceDialog) },
                    )
                }
            }
        }
    }
}

// ── Mode toggle bar ────────────────────────────────────────────────────────────

@Composable
private fun MapTopBar(
    mapMode: MapContract.MapMode,
    onSwitchMode: (MapContract.MapMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.Surface)
            .drawBehind {
                drawLine(
                    color = BlackBoxColors.Border,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = Dimens.PaddingScreen, vertical = Dimens.SpacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MapModeChip(
            label = "Day",
            selected = mapMode == MapContract.MapMode.DAY,
            onClick = { onSwitchMode(MapContract.MapMode.DAY) },
        )
        MapModeChip(
            label = "Places",
            selected = mapMode == MapContract.MapMode.PLACES,
            onClick = { onSwitchMode(MapContract.MapMode.PLACES) },
        )
    }
}

@Composable
private fun MapModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) BlackBoxColors.IndigoDim else BlackBoxColors.SurfaceVariant
    val textColor = if (selected) BlackBoxColors.IndigoLight else BlackBoxColors.TextSecondary

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
        color = textColor,
        modifier = modifier
            .background(bg, RoundedCornerShape(Dimens.RadiusFull))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
    )
}

// ── Date navigator bar ────────────────────────────────────────────────────────

@Composable
private fun MapDateBar(
    selectedDate: String,
    canPlay: Boolean,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.Surface)
            .drawBehind {
                drawLine(
                    color = BlackBoxColors.Border,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = Dimens.PaddingScreen, vertical = Dimens.SpacingXs),
    ) {
        // Left arrow — always a single IconButton
        IconButton(onClick = onPrevious, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day",
                tint = BlackBoxColors.TextSecondary,
            )
        }

        // Date pill — always centred regardless of how many buttons are on either side
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .background(BlackBoxColors.SurfaceVariant, RoundedCornerShape(Dimens.RadiusFull))
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconSm),
                tint = BlackBoxColors.Indigo,
            )
            Spacer(Modifier.width(Dimens.SpacingXs))
            Text(
                text = formatDateLabel(selectedDate),
                style = MaterialTheme.typography.labelLarge,
                color = BlackBoxColors.TextPrimary,
            )
        }

        // Right side — play/pause + next arrow
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (canPlay) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop playback" else "Play route",
                        tint = if (isPlaying) BlackBoxColors.Rose else BlackBoxColors.Teal,
                        modifier = Modifier.size(Dimens.IconMd),
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next day",
                    tint = BlackBoxColors.TextSecondary,
                )
            }
        }
    }
}

// ── Bottom panel ──────────────────────────────────────────────────────────────

@Composable
private fun MapBottomPanel(
    summary: DayLocationSummary?,
    selectedStay: LocationStay?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.Surface)
            .drawBehind {
                drawLine(
                    color = BlackBoxColors.Border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        AnimatedVisibility(
            visible = selectedStay != null && summary != null,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            if (selectedStay != null && summary != null) {
                StayDetailCard(
                    stay = selectedStay,
                    stopIndex = summary.stays.indexOf(selectedStay) + 1,
                    totalStops = summary.stays.size,
                    onDismiss = onDismiss,
                )
            }
        }

        SummaryStrip(summary = summary)
    }
}

/**
 * Always-visible one-line strip: stops count, total distance, time range.
 */
@Composable
private fun SummaryStrip(
    summary: DayLocationSummary?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryChip(
            label = if (summary != null) "${summary.stays.size}" else "—",
            sublabel = "Stops",
            color = BlackBoxColors.Indigo,
        )
        SummaryChip(
            label = if (summary != null) formatDistance(summary.totalDistanceMeters) else "—",
            sublabel = "Distance",
            color = BlackBoxColors.Teal,
        )
        SummaryChip(
            label = if (summary?.firstFixTime != null && summary.lastFixTime != null)
                "${fmtTime(summary.firstFixTime)} – ${fmtTime(summary.lastFixTime)}"
            else "—",
            sublabel = "Time range",
            color = BlackBoxColors.TextSecondary,
        )
    }
}

@Composable
private fun SummaryChip(
    label: String,
    sublabel: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextTertiary,
        )
    }
}

/**
 * Expanded card for a selected stay dot.
 */
@Composable
private fun StayDetailCard(
    stay: LocationStay,
    stopIndex: Int,
    totalStops: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PaddingScreen, vertical = Dimens.SpacingSm)
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = BlackBoxColors.AccentLocation,
                modifier = Modifier.size(Dimens.IconMd),
            )
            Spacer(Modifier.width(Dimens.SpacingXs))
            Text(
                text = stay.knownPlace?.name
                    ?: "(%.5f,  %.5f)".format(stay.latitude, stay.longitude),
                style = MaterialTheme.typography.titleSmall,
                color = BlackBoxColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$stopIndex / $totalStops",
                style = MaterialTheme.typography.labelSmall,
                color = BlackBoxColors.IndigoLight,
                modifier = Modifier
                    .background(BlackBoxColors.IndigoDim, RoundedCornerShape(Dimens.RadiusFull))
                    .padding(horizontal = Dimens.SpacingSm, vertical = 2.dp),
            )
            Spacer(Modifier.width(Dimens.SpacingXs))
            IconButton(onClick = onDismiss, modifier = Modifier.size(Dimens.IconMd)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = BlackBoxColors.TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(Dimens.SpacingXs))

        DetailRow(
            left = "Arrived", leftValue = fmtTime(stay.arrivalTime),
            right = "Departed", rightValue = fmtTime(stay.departureTime),
        )
        DetailRow(
            left = "Duration", leftValue = formatDuration(stay.durationMs),
            right = "GPS fixes", rightValue = "${stay.pointCount}",
        )
        stay.averageAccuracyMeters?.let { acc ->
            DetailRow(
                left = "Avg accuracy", leftValue = "±${acc.toInt()}m",
                right = "Category", rightValue = stay.knownPlace?.category?.name ?: "Unknown",
            )
        }
        DetailRow(
            left = "Lat", leftValue = "%.6f".format(stay.latitude),
            right = "Lng", rightValue = "%.6f".format(stay.longitude),
        )
    }
}

@Composable
private fun DetailRow(
    left: String,
    leftValue: String,
    right: String,
    rightValue: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingXxs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DetailCell(label = left, value = leftValue, modifier = Modifier.weight(1f))
        DetailCell(label = right, value = rightValue, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = BlackBoxColors.TextTertiary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = BlackBoxColors.Teal)
    }
}

// ── Add Place dialog ──────────────────────────────────────────────────────────

private val ADD_PLACE_CATEGORIES = listOf(
    PlaceCategory.HOME,
    PlaceCategory.WORK,
    PlaceCategory.GYM,
    PlaceCategory.RESTAURANT,
    PlaceCategory.SHOPPING,
    PlaceCategory.OTHER,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPlaceDialog(
    dialog: MapContract.AddPlaceDialogState,
    onNameChanged: (String) -> Unit,
    onCategoryChanged: (PlaceCategory) -> Unit,
    onLatChanged: (String) -> Unit,
    onLngChanged: (String) -> Unit,
    onRadiusChanged: (Float) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BlackBoxColors.Surface,
        title = {
            Text(
                text = "Add Place",
                style = MaterialTheme.typography.titleMedium,
                color = BlackBoxColors.TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMd),
            ) {
                // Name
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChanged,
                    label = { Text("Name", color = BlackBoxColors.TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Category chips
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = BlackBoxColors.TextSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                ) {
                    ADD_PLACE_CATEGORIES.forEach { cat ->
                        val selected = cat == dialog.category
                        Text(
                            text = cat.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) BlackBoxColors.Indigo else BlackBoxColors.TextSecondary,
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (selected) BlackBoxColors.Indigo else BlackBoxColors.Border,
                                    shape = RoundedCornerShape(Dimens.RadiusFull),
                                )
                                .clickable { onCategoryChanged(cat) }
                                .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
                        )
                    }
                }

                // Coordinates
                Text(
                    text = "Coordinates",
                    style = MaterialTheme.typography.labelMedium,
                    color = BlackBoxColors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)) {
                    OutlinedTextField(
                        value = dialog.latText,
                        onValueChange = onLatChanged,
                        label = { Text("Lat", color = BlackBoxColors.TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = dialog.latText.toDoubleOrNull() == null,
                    )
                    OutlinedTextField(
                        value = dialog.lngText,
                        onValueChange = onLngChanged,
                        label = { Text("Lng", color = BlackBoxColors.TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = dialog.lngText.toDoubleOrNull() == null,
                    )
                }

                // Radius
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Radius",
                        style = MaterialTheme.typography.labelMedium,
                        color = BlackBoxColors.TextSecondary,
                    )
                    Text(
                        text = "${dialog.radiusMeters.toInt()} m",
                        style = MaterialTheme.typography.labelMedium,
                        color = BlackBoxColors.IndigoLight,
                    )
                }
                Slider(
                    value = dialog.radiusMeters,
                    onValueChange = onRadiusChanged,
                    valueRange = 50f..500f,
                    steps = 17, // 50, 75, 100 … 500 in 25m steps
                    colors = SliderDefaults.colors(
                        thumbColor = BlackBoxColors.Indigo,
                        activeTrackColor = BlackBoxColors.Indigo,
                        inactiveTrackColor = BlackBoxColors.Border,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = dialog.isValid,
            ) {
                Text(
                    text = "Save",
                    color = if (dialog.isValid) BlackBoxColors.Indigo else BlackBoxColors.TextTertiary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BlackBoxColors.TextSecondary)
            }
        },
    )
}

// ── Edit Place dialog ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPlaceDialog(
    dialog: MapContract.EditPlaceDialogState,
    onNameChanged: (String) -> Unit,
    onCategoryChanged: (PlaceCategory) -> Unit,
    onRadiusChanged: (Float) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BlackBoxColors.Surface,
        title = {
            Text(
                text = "Edit Place",
                style = MaterialTheme.typography.titleMedium,
                color = BlackBoxColors.TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMd),
            ) {
                // Name
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChanged,
                    label = { Text("Name", color = BlackBoxColors.TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Category chips
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = BlackBoxColors.TextSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                ) {
                    ADD_PLACE_CATEGORIES.forEach { cat ->
                        val selected = cat == dialog.category
                        Text(
                            text = cat.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) BlackBoxColors.Indigo else BlackBoxColors.TextSecondary,
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (selected) BlackBoxColors.Indigo else BlackBoxColors.Border,
                                    shape = RoundedCornerShape(Dimens.RadiusFull),
                                )
                                .clickable { onCategoryChanged(cat) }
                                .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
                        )
                    }
                }

                // Radius
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Radius",
                        style = MaterialTheme.typography.labelMedium,
                        color = BlackBoxColors.TextSecondary,
                    )
                    Text(
                        text = "${dialog.radiusMeters.toInt()} m",
                        style = MaterialTheme.typography.labelMedium,
                        color = BlackBoxColors.IndigoLight,
                    )
                }
                Slider(
                    value = dialog.radiusMeters,
                    onValueChange = onRadiusChanged,
                    valueRange = 50f..500f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = BlackBoxColors.Indigo,
                        activeTrackColor = BlackBoxColors.Indigo,
                        inactiveTrackColor = BlackBoxColors.Border,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = dialog.isValid,
            ) {
                Text(
                    text = "Save",
                    color = if (dialog.isValid) BlackBoxColors.Indigo else BlackBoxColors.TextTertiary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BlackBoxColors.TextSecondary)
            }
        },
    )
}

// ── Known Places list ─────────────────────────────────────────────────────────

/**
 * Scrollable list of all known places shown in PLACES mode.
 *
 * Each row displays the place name, category, visit count, and last-visit date.
 */
@Composable
private fun PlacesList(
    places: List<KnownPlace>,
    onDelete: (Long) -> Unit,
    onEdit: (KnownPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.PaddingScreen, vertical = Dimens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
    ) {
        item {
            Text(
                text = "${places.size} known places",
                style = MaterialTheme.typography.labelMedium,
                color = BlackBoxColors.TextTertiary,
                modifier = Modifier.padding(bottom = Dimens.SpacingXs),
            )
        }
        items(places, key = { it.id }) { place ->
            PlaceRow(place = place, onDelete = { onDelete(place.id) }, onEdit = { onEdit(place) })
        }
        item { Spacer(Modifier.height(Dimens.SpacingXl)) }
    }
}

@Composable
private fun PlaceRow(
    place: KnownPlace,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(horizontal = Dimens.PaddingCard, vertical = Dimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMd),
    ) {
        // Category-coloured pin icon
        val pinColor = categoryColor(place.category)
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(pinColor.copy(alpha = 0.12f), RoundedCornerShape(Dimens.RadiusSm)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = pinColor,
                modifier = Modifier.size(Dimens.IconMd),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = BlackBoxColors.TextPrimary,
            )
            Text(
                text = "${place.category.name.lowercase().replaceFirstChar { it.uppercase() }}  ·  ${place.visitCount} visits",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextTertiary,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            place.lastVisit?.let { ts ->
                Text(
                    text = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts)),
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextTertiary,
                )
            }
            Text(
                text = if (place.isAutoDetected) "auto" else "manual",
                style = MaterialTheme.typography.labelSmall,
                color = if (place.isAutoDetected) BlackBoxColors.Teal else BlackBoxColors.IndigoLight,
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit place",
                tint = BlackBoxColors.IndigoLight,
                modifier = Modifier.size(Dimens.IconMd),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove place",
                tint = BlackBoxColors.TextTertiary,
                modifier = Modifier.size(Dimens.IconMd),
            )
        }
    }
}

private fun categoryColor(category: PlaceCategory) = when (category) {
    PlaceCategory.HOME -> BlackBoxColors.AccentActivity
    PlaceCategory.WORK -> BlackBoxColors.AccentWifi
    PlaceCategory.GYM -> BlackBoxColors.AccentAudio
    PlaceCategory.RESTAURANT -> BlackBoxColors.AccentBattery
    PlaceCategory.SHOPPING -> BlackBoxColors.AccentConnectivity
    else -> BlackBoxColors.TextSecondary
}

// ── Formatting ────────────────────────────────────────────────────────────────

private fun formatDateLabel(date: String): String = try {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val output = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    output.format(input.parse(date) ?: Date())
} catch (_: Exception) { date }

private fun fmtTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> "${meters.toInt()}m"
    else -> "${"%.1f".format(meters / 1_000)}km"
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun MapContentEmptyPreview() {
    BlackBoxTheme {
        MapContent(
            state = MapContract.State(
                selectedDate = "2026-03-04",
                summary = DayLocationSummary("2026-03-04", emptyList(), 0.0, null, null),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapContentLoadingPreview() {
    BlackBoxTheme {
        MapContent(
            state = MapContract.State(selectedDate = "2026-03-04", isLoading = true),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapContentPlacesPreview() {
    BlackBoxTheme {
        MapContent(
            state = MapContract.State(
                mapMode = MapContract.MapMode.PLACES,
                places = listOf(
                    KnownPlace(id = 1, name = "Home", latitude = 32.08, longitude = 34.78, category = PlaceCategory.HOME, visitCount = 120, isAutoDetected = true, lastVisit = System.currentTimeMillis() - 86_400_000),
                    KnownPlace(id = 2, name = "Office", latitude = 32.07, longitude = 34.77, category = PlaceCategory.WORK, visitCount = 85, isAutoDetected = false, lastVisit = System.currentTimeMillis() - 3_600_000),
                ),
            ),
            onAction = {},
        )
    }
}
