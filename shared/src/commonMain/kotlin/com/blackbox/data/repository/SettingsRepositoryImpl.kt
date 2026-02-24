package com.blackbox.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.blackbox.data.database.BlackBoxDatabase
import com.blackbox.data.mapper.SettingsMapper
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
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
            .map { list -> list.map(SettingsMapper::toDomain) }
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

    companion object {
        private const val TAG = "SettingsRepository"
        private const val ONBOARDING_KEY = "_ONBOARDING_COMPLETE"
    }
}
