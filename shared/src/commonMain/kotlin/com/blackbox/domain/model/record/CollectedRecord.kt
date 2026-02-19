package com.blackbox.domain.model.record

/**
 * A single data point captured by a collector.
 *
 * This is the primary record type stored in the BlackBoxRecord table.
 * Each record wraps a typed [RecordData] payload produced by the
 * corresponding collector. The [sessionId] groups records from the
 * same collection cycle.
 *
 * @property id Database primary key (0 for unsaved records).
 * @property timestamp Unix epoch milliseconds when this data was captured.
 * @property collectorType Which collector produced this record.
 * @property data Typed collector-specific payload.
 * @property accuracyScore Confidence in this data point (0.0 to 1.0).
 * @property sessionId UUID grouping records from the same collection cycle.
 * @property createdAt Unix epoch milliseconds when this record was persisted.
 */
data class CollectedRecord(
    val id: Long = 0,
    val timestamp: Long,
    val collectorType: CollectorType,
    val data: RecordData,
    val accuracyScore: Float = 1.0f,
    val sessionId: String,
    val createdAt: Long,
)

/**
 * Sealed interface for typed collector-specific data payloads.
 *
 * Each variant wraps the corresponding collector data class,
 * allowing type-safe access to record data without casting.
 */
sealed interface RecordData {
    /** Location coordinates from the GPS/fused provider. */
    data class Location(val locationData: LocationData) : RecordData
    /** Motion state and step count from activity recognition. */
    data class Activity(val activityData: ActivityData) : RecordData
    /** WiFi network environment scan results. */
    data class Wifi(val wifiData: WifiData) : RecordData
    /** Foreground app usage event. */
    data class AppUsage(val appUsageData: AppUsageData) : RecordData
    /** Screen on/off/unlock event. */
    data class ScreenState(val screenStateData: ScreenStateData) : RecordData
    /** Ambient noise level measurement. */
    data class AudioLevel(val audioLevelData: AudioLevelData) : RecordData
    /** Battery level and charging state. */
    data class Battery(val batteryData: BatteryData) : RecordData
    /** Network and Bluetooth connectivity state. */
    data class Connectivity(val connectivityData: ConnectivityData) : RecordData
    /** Atmospheric pressure reading. */
    data class Barometer(val barometerData: BarometerData) : RecordData
    /** Ambient light level reading. */
    data class Light(val lightData: LightData) : RecordData
}
