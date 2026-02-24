package com.blackbox.domain.repository

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import kotlinx.coroutines.flow.Flow

/**
 * Repository for user preferences and collector settings.
 */
interface SettingsRepository {

    /** Retrieves all collector settings. */
    suspend fun getAllSettings(): List<CollectorSetting>

    /** Retrieves the setting for a specific collector. */
    suspend fun getSettingForCollector(collectorType: CollectorType): CollectorSetting?

    /** Enables or disables a specific collector. */
    suspend fun setCollectorEnabled(collectorType: CollectorType, enabled: Boolean)

    /** Updates the collection interval for a specific collector. */
    suspend fun setCollectionInterval(collectorType: CollectorType, intervalMs: Long)

    /** Updates the full setting for a collector. */
    suspend fun updateSetting(setting: CollectorSetting)

    /** Observes all collector settings as a reactive flow. */
    fun observeSettings(): Flow<List<CollectorSetting>>

    /** Returns whether onboarding has been completed. */
    suspend fun isOnboardingComplete(): Boolean

    /** Marks onboarding as completed. */
    suspend fun setOnboardingComplete()
}
