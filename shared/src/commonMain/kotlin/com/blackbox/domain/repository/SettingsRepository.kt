package com.blackbox.domain.repository

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.domain.model.settings.RetentionPeriod
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

    /** Returns whether the raw data view (all 10 collectors) is enabled in the Timeline. */
    suspend fun isRawDataViewEnabled(): Boolean

    /**
     * Observes the raw data view setting as a reactive [Flow].
     *
     * Emits the current value immediately and again whenever the setting is changed,
     * allowing the Timeline screen to react to Settings changes without an app restart.
     */
    fun observeRawDataViewEnabled(): Flow<Boolean>

    /** Sets whether the raw data view is enabled. */
    suspend fun setRawDataViewEnabled(enabled: Boolean)

    /** Returns the current data retention period. Defaults to [RetentionPeriod.ONE_YEAR]. */
    suspend fun getRetentionPeriod(): RetentionPeriod

    /** Persists the user's chosen data retention period. */
    suspend fun setRetentionPeriod(period: RetentionPeriod)

    /** Returns whether the biometric/device-credential lock is enabled. */
    suspend fun isBiometricLockEnabled(): Boolean

    /** Persists whether the biometric lock is enabled. */
    suspend fun setBiometricLockEnabled(enabled: Boolean)
}
