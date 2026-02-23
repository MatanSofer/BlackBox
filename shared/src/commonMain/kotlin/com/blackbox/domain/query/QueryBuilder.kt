package com.blackbox.domain.query

import com.blackbox.domain.model.query.EntityType
import com.blackbox.domain.model.query.ParsedQuery
import com.blackbox.domain.model.query.QueryIntent
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.RecordRepository

/**
 * Builds and executes database queries from a [ParsedQuery].
 *
 * Determines which repositories to query based on the classified intent
 * and extracted entities, executes the queries within the parsed time range,
 * and returns the raw results.
 *
 * @property recordRepository Repository for master records.
 * @property locationRepository Repository for denormalized location data.
 */
class QueryBuilder(
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
) {

    /**
     * Executes database queries based on the parsed query.
     *
     * @param parsedQuery The fully parsed query with intent, time range, and entities.
     * @return [QueryData] containing the retrieved records and location entries.
     */
    suspend fun execute(parsedQuery: ParsedQuery): QueryData {
        val start = parsedQuery.timeRange.startEpochMs
        val end = parsedQuery.timeRange.endEpochMs

        return when (parsedQuery.intent) {
            QueryIntent.LOCATION_QUERY -> executeLocationQuery(start, end, parsedQuery)
            QueryIntent.ACTIVITY_QUERY -> executeActivityQuery(start, end, parsedQuery)
            QueryIntent.TEMPORAL_QUERY -> executeTemporalQuery(start, end, parsedQuery)
            QueryIntent.DURATION_QUERY -> executeDurationQuery(start, end, parsedQuery)
            QueryIntent.COUNT_QUERY -> executeCountQuery(start, end, parsedQuery)
            QueryIntent.PROOF_QUERY -> executeProofQuery(start, end)
            QueryIntent.PATTERN_QUERY,
            QueryIntent.SUMMARY_QUERY -> executeSummaryQuery(start, end)
        }
    }

    private suspend fun executeLocationQuery(
        start: Long,
        end: Long,
        parsedQuery: ParsedQuery,
    ): QueryData {
        val locations = locationRepository.getLocationsInRange(start, end)
        val records = recordRepository.getRecordsByTypeInRange(CollectorType.LOCATION, start, end)
        return QueryData(records = records, locations = locations)
    }

    private suspend fun executeActivityQuery(
        start: Long,
        end: Long,
        parsedQuery: ParsedQuery,
    ): QueryData {
        val appEntity = parsedQuery.entities.firstOrNull { it.type == EntityType.APP_NAME }
        val activityEntity = parsedQuery.entities.firstOrNull { it.type == EntityType.ACTIVITY_TYPE }

        val collectorTypes = mutableListOf<CollectorType>()
        if (appEntity != null) collectorTypes.add(CollectorType.APP_USAGE)
        if (activityEntity != null) collectorTypes.add(CollectorType.ACTIVITY)
        if (collectorTypes.isEmpty()) {
            collectorTypes.addAll(listOf(CollectorType.ACTIVITY, CollectorType.APP_USAGE))
        }

        val records = collectorTypes.flatMap { type ->
            recordRepository.getRecordsByTypeInRange(type, start, end)
        }
        return QueryData(records = records)
    }

    private suspend fun executeTemporalQuery(
        start: Long,
        end: Long,
        parsedQuery: ParsedQuery,
    ): QueryData {
        val records = recordRepository.getRecordsInRange(start, end)
        val locations = locationRepository.getLocationsInRange(start, end)
        return QueryData(records = records, locations = locations)
    }

    private suspend fun executeDurationQuery(
        start: Long,
        end: Long,
        parsedQuery: ParsedQuery,
    ): QueryData {
        val placeEntity = parsedQuery.entities.firstOrNull { it.type == EntityType.PLACE_NAME }
        val activityEntity = parsedQuery.entities.firstOrNull { it.type == EntityType.ACTIVITY_TYPE }

        val records = when {
            activityEntity != null ->
                recordRepository.getRecordsByTypeInRange(CollectorType.ACTIVITY, start, end)
            placeEntity != null ->
                recordRepository.getRecordsByTypeInRange(CollectorType.LOCATION, start, end)
            else -> recordRepository.getRecordsInRange(start, end)
        }

        val locations = if (placeEntity != null) {
            locationRepository.getLocationsInRange(start, end)
        } else {
            emptyList()
        }

        return QueryData(records = records, locations = locations)
    }

    private suspend fun executeCountQuery(
        start: Long,
        end: Long,
        parsedQuery: ParsedQuery,
    ): QueryData {
        val records = recordRepository.getRecordsInRange(start, end)
        return QueryData(records = records)
    }

    private suspend fun executeProofQuery(start: Long, end: Long): QueryData {
        val locations = locationRepository.getLocationsInRange(start, end)
        val allRecords = recordRepository.getRecordsInRange(start, end)
        return QueryData(records = allRecords, locations = locations)
    }

    private suspend fun executeSummaryQuery(start: Long, end: Long): QueryData {
        val records = recordRepository.getRecordsInRange(start, end)
        val locations = locationRepository.getLocationsInRange(start, end)
        return QueryData(records = records, locations = locations)
    }
}

/**
 * Raw data retrieved by the [QueryBuilder].
 *
 * @property records Master records matching the query.
 * @property locations Denormalized location entries (may be empty).
 */
data class QueryData(
    val records: List<CollectedRecord> = emptyList(),
    val locations: List<LocationEntry> = emptyList(),
)
