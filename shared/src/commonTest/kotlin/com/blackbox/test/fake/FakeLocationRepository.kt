package com.blackbox.test.fake

import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeLocationRepository : LocationRepository {

    val savedLocations = mutableListOf<LocationEntry>()
    var locationsToReturn: List<LocationEntry> = emptyList()
    var shouldThrow: Throwable? = null

    override suspend fun getLocationsInRange(startTime: Long, endTime: Long): List<LocationEntry> {
        shouldThrow?.let { throw it }
        return locationsToReturn.filter { it.timestamp in startTime..endTime }
    }

    override suspend fun getLastKnownLocation(): LocationEntry? {
        return locationsToReturn.maxByOrNull { it.timestamp }
    }

    override suspend fun getLocationAt(timestamp: Long, toleranceMs: Long): LocationEntry? {
        return locationsToReturn.minByOrNull {
            kotlin.math.abs(it.timestamp - timestamp)
        }?.takeIf { kotlin.math.abs(it.timestamp - timestamp) <= toleranceMs }
    }

    override suspend fun getLocationsInBoundingBox(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double,
        startTime: Long, endTime: Long,
    ): List<LocationEntry> {
        return locationsToReturn.filter {
            it.latitude in minLat..maxLat &&
                it.longitude in minLng..maxLng &&
                it.timestamp in startTime..endTime
        }
    }

    override fun observeLocations(startTime: Long, endTime: Long): Flow<List<LocationEntry>> {
        return flowOf(locationsToReturn.filter { it.timestamp in startTime..endTime })
    }

    override suspend fun saveLocation(location: LocationEntry) {
        shouldThrow?.let { throw it }
        savedLocations.add(location)
    }

    override suspend fun deleteLocationsInRange(startTime: Long, endTime: Long) {
        savedLocations.removeAll { it.timestamp in startTime..endTime }
    }
}
