package com.blackbox.ui.map

import com.blackbox.domain.model.map.DayLocationSummary
import com.blackbox.domain.model.map.LocationStay
import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory

/**
 * MVI contract for the Map screen.
 * Defines all possible states, user actions, and one-time events.
 */
object MapContract {

    /** The two display modes for the Map screen. */
    enum class MapMode { DAY, PLACES }

    /**
     * Transient state for the "Add Place" dialog.
     *
     * @property name User-entered place name.
     * @property category Selected place category.
     * @property latText Latitude as an editable string.
     * @property lngText Longitude as an editable string.
     */
    data class AddPlaceDialogState(
        val name: String = "",
        val category: PlaceCategory = PlaceCategory.OTHER,
        val latText: String = "0.0",
        val lngText: String = "0.0",
    ) {
        /** True when all fields are valid and the place can be saved. */
        val isValid: Boolean
            get() = name.isNotBlank() &&
                latText.toDoubleOrNull() != null &&
                lngText.toDoubleOrNull() != null
    }

    /**
     * Single immutable UI state for the Map screen.
     *
     * @property selectedDate Currently displayed date, formatted as "yyyy-MM-dd".
     * @property isLoading Whether location data is being loaded.
     * @property summary Day location summary for [selectedDate], or null while loading.
     * @property selectedStay The stay dot currently tapped by the user, or null.
     * @property error Error message to display, or null.
     * @property availableDates Dates that have location records; used to grey out empty days in the picker.
     * @property showDatePicker Whether the date picker dialog is visible.
     * @property mapMode Whether the screen shows the day route or the known-places list.
     * @property places All known places for the Places tab.
     * @property isLoadingPlaces Whether the places list is loading.
     * @property isPlayingRoute Whether the route-playback animation is active.
     * @property playbackIndex Index into [DayLocationSummary.stays] that is currently highlighted.
     */
    data class State(
        val selectedDate: String = "",
        val isLoading: Boolean = false,
        val summary: DayLocationSummary? = null,
        val selectedStay: LocationStay? = null,
        val error: String? = null,
        val availableDates: Set<String> = emptySet(),
        val showDatePicker: Boolean = false,
        val mapMode: MapMode = MapMode.DAY,
        val places: List<KnownPlace> = emptyList(),
        val isLoadingPlaces: Boolean = false,
        val isPlayingRoute: Boolean = false,
        val playbackIndex: Int = 0,
        /** Non-null while the Add Place dialog is open. */
        val addPlaceDialog: AddPlaceDialogState? = null,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     */
    sealed interface Action {
        /** Navigate to the previous day. */
        data object PreviousDay : Action
        /** Navigate to the next day. */
        data object NextDay : Action
        /** Jump directly to a specific date. */
        data class DateSelected(val date: String) : Action
        /** User tapped a stay dot (non-null) or the map background (null = deselect). */
        data class StayTapped(val stay: LocationStay?) : Action
        /** Reload location data for the current date. */
        data object Refresh : Action
        /** User tapped the date pill to open the picker. */
        data object ShowDatePicker : Action
        /** User dismissed the date picker without selecting. */
        data object DismissDatePicker : Action
        /** User switched between DAY and PLACES map mode. */
        data class SwitchMode(val mode: MapMode) : Action
        /** Start animating the route playback through all stays. */
        data object StartRoutePlayback : Action
        /** Stop / cancel the route playback. */
        data object StopRoutePlayback : Action
        /** Open the Add Place dialog, pre-filling coordinates from the current day. */
        data object OpenAddPlaceDialog : Action
        /** Dismiss the Add Place dialog without saving. */
        data object DismissAddPlaceDialog : Action
        /** Update the name field in the Add Place dialog. */
        data class AddPlaceNameChanged(val name: String) : Action
        /** Update the category in the Add Place dialog. */
        data class AddPlaceCategoryChanged(val category: PlaceCategory) : Action
        /** Update the latitude text in the Add Place dialog. */
        data class AddPlaceLatChanged(val text: String) : Action
        /** Update the longitude text in the Add Place dialog. */
        data class AddPlaceLngChanged(val text: String) : Action
        /** Confirm and save the new place. */
        data object ConfirmAddPlace : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     */
    sealed interface Event {
        data class ShowSnackbar(val message: String) : Event
    }
}
