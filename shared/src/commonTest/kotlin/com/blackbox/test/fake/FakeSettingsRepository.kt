package com.blackbox.test.fake

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeSettingsRepository : SettingsRepository {

    val settings = mutableMapOf<CollectorType, CollectorSetting>()
    var shouldThrow: Throwable? = null

    override suspend fun getAllSettings(): List<CollectorSetting> {
        shouldThrow?.let { throw it }
        return settings.values.toList()
    }

    override suspend fun getSettingForCollector(collectorType: CollectorType): CollectorSetting? {
        return settings[collectorType]
    }

    override suspend fun setCollectorEnabled(collectorType: CollectorType, enabled: Boolean) {
        shouldThrow?.let { throw it }
        val existing = settings[collectorType] ?: return
        settings[collectorType] = existing.copy(isEnabled = enabled)
    }

    override suspend fun setCollectionInterval(collectorType: CollectorType, intervalMs: Long) {
        shouldThrow?.let { throw it }
        val existing = settings[collectorType] ?: return
        settings[collectorType] = existing.copy(collectionIntervalMs = intervalMs)
    }

    override suspend fun updateSetting(setting: CollectorSetting) {
        shouldThrow?.let { throw it }
        settings[setting.collectorType] = setting
    }

    override fun observeSettings(): Flow<List<CollectorSetting>> {
        return flowOf(settings.values.toList())
    }
}
