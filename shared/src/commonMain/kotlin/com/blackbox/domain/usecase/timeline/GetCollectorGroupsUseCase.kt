package com.blackbox.domain.usecase.timeline

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.model.timeline.CollectorGroup
import com.blackbox.domain.model.timeline.TimelineEntry
import com.blackbox.domain.model.timeline.TimelineEntryType
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.repository.TimelineRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Builds a grouped timeline where each data collector produces one card.
 *
 * The three "base" groups (LOCATION, ACTIVITY, Derived Events) are always
 * included. When [showAll] is `true`, one additional card is produced for
 * each of the remaining eight collectors (WIFI, CONNECTIVITY, BATTERY,
 * SCREEN_STATE, APP_USAGE, AUDIO_LEVEL, BAROMETER, LIGHT) showing their
 * raw [CollectedRecord] list for inspection.
 *
 * Empty groups (zero records/entries) are filtered out of the result.
 * Groups are sorted by [CollectorGroup.lastRecordTime] descending so the
 * most recently active collector appears at the top.
 *
 * @property recordRepository Repository for master collector records.
 * @property locationRepository Repository for denormalized location data.
 * @property timelineRepository Repository for derived events.
 * @property placeRepository Repository for known places (used when labelling stays).
 * @property logger Logger for operation tracking.
 */
class GetCollectorGroupsUseCase(
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
    private val timelineRepository: TimelineRepository,
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Builds collector groups for the given time range.
     *
     * @param startTime Start of the range in epoch ms.
     * @param endTime End of the range in epoch ms.
     * @param showAll When `true`, all 10 collector types are included; otherwise only 3.
     * @return [Result] containing the list of non-empty [CollectorGroup] instances.
     */
    suspend operator fun invoke(
        startTime: Long,
        endTime: Long,
        showAll: Boolean,
    ): Result<List<CollectorGroup>> = runCatching {
        logger.d(TAG, "Building collector groups: $startTime..$endTime showAll=$showAll")

        val groups = mutableListOf<CollectorGroup>()

        // ── Location group ───────────────────────────────────────────────────
        val locations = locationRepository.getLocationsInRange(startTime, endTime)
        val locationEntries = buildLocationEntries(locations)
        if (locationEntries.isNotEmpty()) {
            groups.add(
                CollectorGroup(
                    collectorType = CollectorType.LOCATION,
                    displayName = "LOCATION",
                    recordCount = locationEntries.size,
                    lastRecordTime = locationEntries.maxOfOrNull { it.startTimestamp },
                    entries = locationEntries,
                ),
            )
        }

        // ── Activity group ────────────────────────────────────────────────────
        val activityRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.ACTIVITY, startTime, endTime,
        )
        val activityEntries = buildActivityEntries(activityRecords)
        if (activityEntries.isNotEmpty()) {
            groups.add(
                CollectorGroup(
                    collectorType = CollectorType.ACTIVITY,
                    displayName = "ACTIVITY",
                    recordCount = activityEntries.size,
                    lastRecordTime = activityEntries.maxOfOrNull { it.startTimestamp },
                    entries = activityEntries,
                ),
            )
        }

        // ── Derived Events group ──────────────────────────────────────────────
        val events = timelineRepository.getEventsInRange(startTime, endTime)
        val eventEntries = events.map { event ->
            TimelineEntry(
                startTimestamp = event.timestamp,
                type = TimelineEntryType.EVENT,
                title = event.description,
                iconType = event.eventType.name.lowercase(),
            )
        }
        if (eventEntries.isNotEmpty()) {
            groups.add(
                CollectorGroup(
                    collectorType = null,
                    displayName = "DERIVED EVENTS",
                    recordCount = eventEntries.size,
                    lastRecordTime = eventEntries.maxOfOrNull { it.startTimestamp },
                    entries = eventEntries,
                ),
            )
        }

        // ── Raw collector groups (only when showAll = true) ───────────────────
        if (showAll) {
            val rawTypes = listOf(
                CollectorType.WIFI,
                CollectorType.CONNECTIVITY,
                CollectorType.BATTERY,
                CollectorType.SCREEN_STATE,
                CollectorType.APP_USAGE,
                CollectorType.AUDIO_LEVEL,
                CollectorType.BAROMETER,
                CollectorType.LIGHT,
            )
            for (type in rawTypes) {
                val records = recordRepository.getRecordsByTypeInRange(type, startTime, endTime)
                if (records.isNotEmpty()) {
                    groups.add(
                        CollectorGroup(
                            collectorType = type,
                            displayName = type.name.replace('_', ' '),
                            recordCount = records.size,
                            lastRecordTime = records.maxOfOrNull { it.timestamp },
                            rawRecords = records,
                        ),
                    )
                }
            }
        }

        val sorted = groups.sortedByDescending { it.lastRecordTime ?: 0L }
        logger.d(TAG, "Built ${sorted.size} groups")
        sorted
    }

    /**
     * Observes collector groups reactively for the given time range.
     *
     * Combines three live data sources — records, locations, and the showAll
     * setting — into a single flow. Whenever any of them changes (e.g. a new
     * record is saved by the background service, or the user toggles the
     * "show all collectors" switch in Settings), the flow re-emits by calling
     * [invoke] with the latest inputs, so the Timeline screen updates
     * automatically without polling or app restarts.
     *
     * @param startTime Start of the range in epoch ms.
     * @param endTime End of the range in epoch ms.
     * @param showAllFlow Live setting for whether all 10 collectors are shown.
     * @return Flow of Results that re-emits on every relevant DB change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(
        startTime: Long,
        endTime: Long,
        showAllFlow: Flow<Boolean>,
    ): Flow<Result<List<CollectorGroup>>> {
        return combine(
            recordRepository.observeRecordsInRange(startTime, endTime),
            locationRepository.observeLocations(startTime, endTime),
            showAllFlow,
        ) { _, _, showAll -> showAll }
            .flatMapLatest { showAll ->
                flow { emit(invoke(startTime, endTime, showAll)) }
            }
    }

    /**
     * Groups consecutive location points into location-stay [TimelineEntry] instances.
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
        val durationMin = (end.timestamp - start.timestamp) / 60_000
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

    private fun buildActivityEntries(records: List<CollectedRecord>): List<TimelineEntry> {
        return records.mapNotNull { record ->
            val activityData = (record.data as? RecordData.Activity)?.activityData ?: return@mapNotNull null
            TimelineEntry(
                startTimestamp = record.timestamp,
                type = TimelineEntryType.ACTIVITY,
                title = activityData.detectedActivity.name,
                subtitle = "Confidence: ${activityData.confidence}%",
                iconType = "activity_${activityData.detectedActivity.name.lowercase()}",
            )
        }
    }

    /** Approximates distance between two WGS84 coordinates in metres. */
    private fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double,
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    companion object {
        private const val TAG = "GetCollectorGroupsUseCase"
        private const val STAY_RADIUS_METERS = 100.0
    }
}
