package com.blackbox.data.mapper

import com.blackbox.domain.model.place.PlaceCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.blackbox.data.database.FindNearestPlace
import com.blackbox.data.database.KnownPlace as DbPlace
import com.blackbox.domain.model.place.KnownPlace as DomainPlace

/**
 * Maps between SQLDelight [DbPlace] and domain [DomainPlace].
 *
 * Handles JSON array serialization for the `wifi_fingerprint` column.
 */
object PlaceMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Converts a SQLDelight [DbPlace] row to a domain [DomainPlace].
     */
    fun toDomain(db: DbPlace): DomainPlace = DomainPlace(
        id = db.id,
        name = db.name,
        latitude = db.latitude,
        longitude = db.longitude,
        radiusMeters = db.radius_meters,
        wifiFingerprint = deserializeWifiFingerprint(db.wifi_fingerprint),
        category = PlaceCategory.entries.firstOrNull { it.name == db.category } ?: PlaceCategory.OTHER,
        visitCount = db.visit_count.toInt(),
        totalTimeMinutes = db.total_time_minutes.toInt(),
        firstVisit = db.first_visit,
        lastVisit = db.last_visit,
        isAutoDetected = db.is_auto_detected != 0L,
        isHidden = db.is_hidden != 0L,
        createdAt = db.created_at,
        updatedAt = db.updated_at,
    )

    /**
     * Converts a [FindNearestPlace] result to a domain [DomainPlace].
     */
    fun toDomain(db: FindNearestPlace): DomainPlace = DomainPlace(
        id = db.id,
        name = db.name,
        latitude = db.latitude,
        longitude = db.longitude,
        radiusMeters = db.radius_meters,
        wifiFingerprint = deserializeWifiFingerprint(db.wifi_fingerprint),
        category = PlaceCategory.entries.firstOrNull { it.name == db.category } ?: PlaceCategory.OTHER,
        visitCount = db.visit_count.toInt(),
        totalTimeMinutes = db.total_time_minutes.toInt(),
        firstVisit = db.first_visit,
        lastVisit = db.last_visit,
        isAutoDetected = db.is_auto_detected != 0L,
        isHidden = db.is_hidden != 0L,
        createdAt = db.created_at,
        updatedAt = db.updated_at,
    )

    /**
     * Serializes a list of WiFi BSSIDs to a JSON array string.
     */
    fun serializeWifiFingerprint(fingerprints: List<String>): String? =
        if (fingerprints.isEmpty()) null else json.encodeToString(fingerprints)

    private fun deserializeWifiFingerprint(jsonStr: String?): List<String> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
