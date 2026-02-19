package com.blackbox.domain.model.place

/**
 * A meaningful location that the user visits regularly.
 *
 * Known places are either auto-detected by clustering frequently
 * visited GPS coordinates or manually created by the user.
 * They provide context for timeline entries and query responses.
 *
 * @property id Database primary key (0 for unsaved places).
 * @property name Display name (e.g., "Home", "Office", "Gym").
 * @property latitude Center latitude of this place.
 * @property longitude Center longitude of this place.
 * @property radiusMeters Geofence radius defining "at this place" (default 100m).
 * @property wifiFingerprint BSSIDs associated with this place for indoor matching.
 * @property category Place type for grouping and display.
 * @property visitCount Total number of detected visits.
 * @property totalTimeMinutes Cumulative time spent at this place in minutes.
 * @property firstVisit Timestamp of the first detected visit (epoch ms).
 * @property lastVisit Timestamp of the most recent visit (epoch ms).
 * @property isAutoDetected True if detected by clustering, false if user-created.
 * @property isHidden True if the user chose to hide this place from the UI.
 * @property createdAt When this place was first created (epoch ms).
 * @property updatedAt When this place was last modified (epoch ms).
 */
data class KnownPlace(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 100.0,
    val wifiFingerprint: List<String> = emptyList(),
    val category: PlaceCategory = PlaceCategory.OTHER,
    val visitCount: Int = 0,
    val totalTimeMinutes: Int = 0,
    val firstVisit: Long? = null,
    val lastVisit: Long? = null,
    val isAutoDetected: Boolean = true,
    val isHidden: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
