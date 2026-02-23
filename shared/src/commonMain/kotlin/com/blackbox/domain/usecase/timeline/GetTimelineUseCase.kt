package com.blackbox.domain.usecase.timeline

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.model.timeline.TimelineEntry
import com.blackbox.domain.model.timeline.TimelineEntryType
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.repository.TimelineRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Builds a chronological timeline of entries for a given time range.
 *
 * Combines location stays, activity periods, and derived events
 * into an ordered list of [TimelineEntry] instances suitable for
 * display in the Timeline screen.
 *
 * @property recordRepository Repository for master records.
 * @property locationRepository Repository for location data.
 * @property timelineRepository Repository for derived events.
 * @property placeRepository Repository for known places.
 * @property logger Logger for operation tracking.
 */
class GetTimelineUseCase(
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
    private val timelineRepository: TimelineRepository,
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Builds a timeline for the given time range.
     *
     * @param startTime Start of the range in epoch ms.
     * @param endTime End of the range in epoch ms.
     * @return [Result] containing the ordered list of timeline entries.
     */
    suspend operator fun invoke(startTime: Long, endTime: Long): Result<List<TimelineEntry>> {
        return runCatching {
            logger.d(TAG, "Building timeline: $startTime..$endTime")

            val entries = mutableListOf<TimelineEntry>()

            // Add location-based entries
            val locations = locationRepository.getLocationsInRange(startTime, endTime)
            entries.addAll(buildLocationEntries(locations))

            // Add activity entries
            val activityRecords = recordRepository.getRecordsByTypeInRange(
                CollectorType.ACTIVITY, startTime, endTime,
            )
            for (record in activityRecords) {
                val activityData = (record.data as? RecordData.Activity)?.activityData ?: continue
                entries.add(
                    TimelineEntry(
                        startTimestamp = record.timestamp,
                        type = TimelineEntryType.ACTIVITY,
                        title = activityData.detectedActivity.name,
                        subtitle = "Confidence: ${activityData.confidence}%",
                        iconType = "activity_${activityData.detectedActivity.name.lowercase()}",
                    )
                )
            }

            // Add derived events
            val events = timelineRepository.getEventsInRange(startTime, endTime)
            for (event in events) {
                entries.add(
                    TimelineEntry(
                        startTimestamp = event.timestamp,
                        type = TimelineEntryType.EVENT,
                        title = event.description,
                        iconType = event.eventType.name.lowercase(),
                    )
                )
            }

            val sorted = entries.sortedBy { it.startTimestamp }
            logger.d(TAG, "Timeline built: ${sorted.size} entries")
            sorted
        }
    }

    /**
     * Groups consecutive location points into location stay entries.
     */
    private suspend fun buildLocationEntries(locations: List<LocationEntry>): List<TimelineEntry> {
        if (locations.isEmpty()) return emptyList()

        val entries = mutableListOf<TimelineEntry>()
        var groupStart = locations.first()
        var groupEnd = groupStart

        for (i in 1 until locations.size) {
            val current = locations[i]
            val distance = haversineDistance(
                groupStart.latitude, groupStart.longitude,
                current.latitude, current.longitude,
            )

            if (distance < STAY_RADIUS_METERS) {
                groupEnd = current
            } else {
                entries.add(buildStayEntry(groupStart, groupEnd))
                groupStart = current
                groupEnd = current
            }
        }

        entries.add(buildStayEntry(groupStart, groupEnd))
        return entries
    }

    private suspend fun buildStayEntry(start: LocationEntry, end: LocationEntry): TimelineEntry {
        val place = placeRepository.findNearestPlace(start.latitude, start.longitude)
        val title = place?.name ?: "(%.4f, %.4f)".format(start.latitude, start.longitude)
        val durationMs = end.timestamp - start.timestamp
        val durationMin = durationMs / 60_000

        return TimelineEntry(
            startTimestamp = start.timestamp,
            endTimestamp = end.timestamp,
            type = TimelineEntryType.LOCATION_STAY,
            title = title,
            subtitle = if (durationMin > 0) "${durationMin}m" else null,
            iconType = "location",
            place = place,
        )
    }

    /** Approximates distance between two coordinates in meters. */
    private fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double,
    ): Double {
        val r = 6_371_000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    companion object {
        private const val TAG = "GetTimelineUseCase"

        /** Radius within which consecutive points are grouped as a "stay". */
        private const val STAY_RADIUS_METERS = 100.0
    }
}
