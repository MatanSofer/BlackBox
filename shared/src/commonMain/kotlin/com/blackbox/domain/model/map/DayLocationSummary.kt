package com.blackbox.domain.model.map

/**
 * Aggregated location data for a single day.
 *
 * Built by [com.blackbox.domain.usecase.map.GetDayLocationSummaryUseCase] from raw
 * location fixes, grouped into [LocationStay] clusters.
 *
 * @property date The day this summary covers, formatted as "yyyy-MM-dd".
 * @property stays Ordered list of location clusters (arrival time ascending).
 * @property totalDistanceMeters Sum of straight-line distances between consecutive stay centroids.
 * @property firstFixTime Epoch ms of the very first GPS fix of the day, or null if no data.
 * @property lastFixTime Epoch ms of the very last GPS fix of the day, or null if no data.
 */
data class DayLocationSummary(
    val date: String,
    val stays: List<LocationStay>,
    val totalDistanceMeters: Double,
    val firstFixTime: Long?,
    val lastFixTime: Long?,
)
