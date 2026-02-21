package com.blackbox.data.mapper

import com.blackbox.domain.repository.LocationEntry
import com.blackbox.data.database.LocationRecord as DbLocation

/**
 * Maps between SQLDelight [DbLocation] and domain [LocationEntry].
 */
object LocationMapper {

    /**
     * Converts a SQLDelight [DbLocation] row to a domain [LocationEntry].
     */
    fun toDomain(db: DbLocation): LocationEntry = LocationEntry(
        id = db.id,
        recordId = db.record_id,
        latitude = db.latitude,
        longitude = db.longitude,
        altitude = db.altitude,
        accuracyMeters = db.accuracy_meters?.toFloat(),
        speed = db.speed?.toFloat(),
        bearing = db.bearing?.toFloat(),
        source = db.source,
        timestamp = db.timestamp,
    )
}
