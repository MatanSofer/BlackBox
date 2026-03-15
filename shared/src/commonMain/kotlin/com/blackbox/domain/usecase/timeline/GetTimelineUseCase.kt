package com.blackbox.domain.usecase.timeline

import com.blackbox.domain.model.record.CallType
import com.blackbox.domain.model.record.CollectedRecord
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
            val wifiRecords = recordRepository.getRecordsByTypeInRange(
                CollectorType.WIFI, startTime, endTime,
            )
            entries.addAll(buildLocationEntries(locations, wifiRecords))

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

            // Add call log entries
            val callRecords = recordRepository.getRecordsByTypeInRange(
                CollectorType.CALL_LOG, startTime, endTime,
            )
            for (record in callRecords) {
                val callData = (record.data as? RecordData.CallLog)?.callLogData ?: continue
                val durationStr = formatDuration(callData.durationSeconds)
                val title = when (callData.callType) {
                    CallType.INCOMING -> if (durationStr.isNotEmpty()) "Incoming call · $durationStr" else "Incoming call"
                    CallType.OUTGOING -> if (durationStr.isNotEmpty()) "Outgoing call · $durationStr" else "Outgoing call"
                    CallType.MISSED -> "Missed call"
                    CallType.REJECTED -> "Rejected call"
                    CallType.VOICEMAIL -> "Voicemail"
                    CallType.UNKNOWN -> "Call"
                }
                entries.add(
                    TimelineEntry(
                        startTimestamp = record.timestamp,
                        type = TimelineEntryType.EVENT,
                        title = title,
                        iconType = "call_${callData.callType.name.lowercase()}",
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
     *
     * @param locations Ordered list of location entries.
     * @param wifiRecords WiFi records for the same time range — used for indoor place matching.
     */
    private suspend fun buildLocationEntries(
        locations: List<LocationEntry>,
        wifiRecords: List<CollectedRecord>,
    ): List<TimelineEntry> {
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
                entries.add(buildStayEntry(groupStart, groupEnd, wifiRecords))
                groupStart = current
                groupEnd = current
            }
        }

        entries.add(buildStayEntry(groupStart, groupEnd, wifiRecords))
        return entries
    }

    /**
     * Builds a [TimelineEntry] for a single location stay.
     *
     * Place lookup order:
     * 1. GPS-based nearest place within radius.
     * 2. WiFi BSSID matching — finds the WiFi record closest to the stay start
     *    and checks if its BSSID belongs to any known place's fingerprint.
     */
    private suspend fun buildStayEntry(
        start: LocationEntry,
        end: LocationEntry,
        wifiRecords: List<CollectedRecord>,
    ): TimelineEntry {
        var place = placeRepository.findNearestPlace(start.latitude, start.longitude)

        // WiFi-based fallback: match by router BSSID stored in the place's fingerprint
        if (place == null) {
            val nearestBssid = wifiRecords
                .filter { it.timestamp in start.timestamp..end.timestamp }
                .mapNotNull { (it.data as? RecordData.Wifi)?.wifiData?.connectedBssid }
                .firstOrNull()
            if (nearestBssid != null) {
                place = placeRepository.findPlaceByWifiBssid(nearestBssid)
            }
        }

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

    /** Formats a call duration in seconds to a concise "Xm Ys" string. */
    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return ""
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
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
