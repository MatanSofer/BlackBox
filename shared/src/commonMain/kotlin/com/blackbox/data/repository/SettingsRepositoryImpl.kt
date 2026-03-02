package com.blackbox.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.blackbox.data.database.BlackBoxDatabase
import com.blackbox.data.mapper.SettingsMapper
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.domain.model.settings.RetentionPeriod
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [SettingsRepository].
 *
 * Manages per-collector user preferences stored in the
 * CollectorSetting table. All database operations run on [Dispatchers.IO].
 */
class SettingsRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val logger: BlackBoxLogger,
) : SettingsRepository {

    override suspend fun getAllSettings(): List<CollectorSetting> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching all collector settings")
            database.blackBoxDatabaseQueries
                .getAllSettings()
                .executeAsList()
                .filter { !it.collector_type.startsWith("_") }
                .map(SettingsMapper::toDomain)
        }
    }

    override suspend fun getSettingForCollector(collectorType: CollectorType): CollectorSetting? {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .getSettingForCollector(collectorType.name)
                .executeAsOneOrNull()
                ?.let(SettingsMapper::toDomain)
        }
    }

    override suspend fun setCollectorEnabled(collectorType: CollectorType, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Setting ${collectorType.name} enabled=$enabled")
            database.blackBoxDatabaseQueries.setCollectorEnabled(
                is_enabled = if (enabled) 1L else 0L,
                updated_at = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                collector_type = collectorType.name,
            )
        }
    }

    override suspend fun setCollectionInterval(collectorType: CollectorType, intervalMs: Long) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Setting ${collectorType.name} interval=${intervalMs}ms")
            database.blackBoxDatabaseQueries.setCollectionInterval(
                collection_interval_ms = intervalMs,
                updated_at = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                collector_type = collectorType.name,
            )
        }
    }

    override suspend fun updateSetting(setting: CollectorSetting) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Updating setting: ${setting.collectorType.name}")
            database.blackBoxDatabaseQueries.insertSetting(
                collector_type = setting.collectorType.name,
                is_enabled = if (setting.isEnabled) 1L else 0L,
                collection_interval_ms = setting.collectionIntervalMs,
                custom_config_json = setting.customConfigJson,
                updated_at = setting.updatedAt,
            )
        }
    }

    override fun observeSettings(): Flow<List<CollectorSetting>> {
        return database.blackBoxDatabaseQueries
            .getAllSettings()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.filter { !it.collector_type.startsWith("_") }
                    .map(SettingsMapper::toDomain)
            }
    }

    override suspend fun isOnboardingComplete(): Boolean {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .getSettingForCollector(ONBOARDING_KEY)
                .executeAsOneOrNull()
                ?.is_enabled == 1L
        }
    }

    override suspend fun setOnboardingComplete() {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Marking onboarding as complete")
            database.blackBoxDatabaseQueries.insertSetting(
                collector_type = ONBOARDING_KEY,
                is_enabled = 1L,
                collection_interval_ms = 0L,
                custom_config_json = null,
                updated_at = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    override fun observeRawDataViewEnabled(): Flow<Boolean> {
        return database.blackBoxDatabaseQueries
            .getSettingForCollector(RAW_DATA_VIEW_KEY)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { row -> row?.is_enabled == 1L }
    }

    override suspend fun isRawDataViewEnabled(): Boolean {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .getSettingForCollector(RAW_DATA_VIEW_KEY)
                .executeAsOneOrNull()
                ?.is_enabled == 1L
        }
    }

    override suspend fun setRawDataViewEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Setting raw data view enabled=$enabled")
            database.blackBoxDatabaseQueries.insertSetting(
                collector_type = RAW_DATA_VIEW_KEY,
                is_enabled = if (enabled) 1L else 0L,
                collection_interval_ms = 0L,
                custom_config_json = null,
                updated_at = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    override suspend fun getRetentionPeriod(): RetentionPeriod {
        return withContext(Dispatchers.IO) {
            val name = database.blackBoxDatabaseQueries
                .getSettingForCollector(RETENTION_PERIOD_KEY)
                .executeAsOneOrNull()
                ?.custom_config_json
            RetentionPeriod.entries.firstOrNull { it.name == name } ?: RetentionPeriod.ONE_YEAR
        }
    }

    override suspend fun setRetentionPeriod(period: RetentionPeriod) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Setting retention period=${period.name}")
            database.blackBoxDatabaseQueries.insertSetting(
                collector_type = RETENTION_PERIOD_KEY,
                is_enabled = 1L,
                collection_interval_ms = 0L,
                custom_config_json = period.name,
                updated_at = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    companion object {
        private const val TAG = "SettingsRepository"
        private const val ONBOARDING_KEY = "_ONBOARDING_COMPLETE"
        private const val RAW_DATA_VIEW_KEY = "_RAW_DATA_VIEW_ENABLED"
        private const val RETENTION_PERIOD_KEY = "_RETENTION_PERIOD"
    }
}
