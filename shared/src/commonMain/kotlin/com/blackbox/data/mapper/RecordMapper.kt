package com.blackbox.data.mapper

import com.blackbox.domain.model.record.ActivityData
import com.blackbox.domain.model.record.AppUsageData
import com.blackbox.domain.model.record.AudioLevelData
import com.blackbox.domain.model.record.BarometerData
import com.blackbox.domain.model.record.BatteryData
import com.blackbox.domain.model.record.CallLogData
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.ConnectivityData
import com.blackbox.domain.model.record.LightData
import com.blackbox.domain.model.record.LocationData
import com.blackbox.domain.model.record.MediaPlaybackData
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.model.record.ScreenStateData
import com.blackbox.domain.model.record.WifiData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.blackbox.data.database.BlackBoxRecord as DbRecord

/**
 * Maps between SQLDelight [DbRecord] and domain [CollectedRecord].
 *
 * Handles JSON serialization/deserialization of the polymorphic [RecordData]
 * sealed interface to/from the `data_json` TEXT column.
 */
object RecordMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Converts a SQLDelight [DbRecord] row to a domain [CollectedRecord].
     */
    fun toDomain(dbRecord: DbRecord): CollectedRecord {
        val collectorType = CollectorType.valueOf(dbRecord.collector_type)
        val recordData = deserializeData(collectorType, dbRecord.data_json)
        return CollectedRecord(
            id = dbRecord.id,
            timestamp = dbRecord.timestamp,
            collectorType = collectorType,
            data = recordData,
            accuracyScore = dbRecord.accuracy_score.toFloat(),
            sessionId = dbRecord.session_id,
            createdAt = dbRecord.created_at,
        )
    }

    /**
     * Serializes [RecordData] to a JSON string for database storage.
     */
    fun serializeData(data: RecordData): String = when (data) {
        is RecordData.Location -> json.encodeToString(data.locationData)
        is RecordData.Activity -> json.encodeToString(data.activityData)
        is RecordData.Wifi -> json.encodeToString(data.wifiData)
        is RecordData.AppUsage -> json.encodeToString(data.appUsageData)
        is RecordData.ScreenState -> json.encodeToString(data.screenStateData)
        is RecordData.AudioLevel -> json.encodeToString(data.audioLevelData)
        is RecordData.Battery -> json.encodeToString(data.batteryData)
        is RecordData.Connectivity -> json.encodeToString(data.connectivityData)
        is RecordData.Barometer -> json.encodeToString(data.barometerData)
        is RecordData.Light -> json.encodeToString(data.lightData)
        is RecordData.CallLog -> json.encodeToString(data.callLogData)
        is RecordData.MediaPlayback -> json.encodeToString(data.mediaPlaybackData)
    }

    private fun deserializeData(type: CollectorType, dataJson: String): RecordData = when (type) {
        CollectorType.LOCATION -> RecordData.Location(json.decodeFromString<LocationData>(dataJson))
        CollectorType.ACTIVITY -> RecordData.Activity(json.decodeFromString<ActivityData>(dataJson))
        CollectorType.WIFI -> RecordData.Wifi(json.decodeFromString<WifiData>(dataJson))
        CollectorType.APP_USAGE -> RecordData.AppUsage(json.decodeFromString<AppUsageData>(dataJson))
        CollectorType.SCREEN_STATE -> RecordData.ScreenState(json.decodeFromString<ScreenStateData>(dataJson))
        CollectorType.AUDIO_LEVEL -> RecordData.AudioLevel(json.decodeFromString<AudioLevelData>(dataJson))
        CollectorType.BATTERY -> RecordData.Battery(json.decodeFromString<BatteryData>(dataJson))
        CollectorType.CONNECTIVITY -> RecordData.Connectivity(json.decodeFromString<ConnectivityData>(dataJson))
        CollectorType.BAROMETER -> RecordData.Barometer(json.decodeFromString<BarometerData>(dataJson))
        CollectorType.LIGHT -> RecordData.Light(json.decodeFromString<LightData>(dataJson))
        CollectorType.CALL_LOG -> RecordData.CallLog(json.decodeFromString<CallLogData>(dataJson))
        CollectorType.MEDIA_PLAYBACK -> RecordData.MediaPlayback(json.decodeFromString<MediaPlaybackData>(dataJson))
    }
}
