package com.blackbox.domain.usecase.settings

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Updates collector settings (enable/disable, interval changes).
 *
 * @property settingsRepository Repository for collector settings.
 * @property logger Logger for operation tracking.
 */
class UpdateCollectorSettingUseCase(
    private val settingsRepository: SettingsRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Toggles a collector's enabled state.
     *
     * @param collectorType The collector to toggle.
     * @param enabled Whether to enable or disable the collector.
     * @return [Result.success] on success or [Result.failure] on error.
     */
    suspend operator fun invoke(collectorType: CollectorType, enabled: Boolean): Result<Unit> {
        return runCatching {
            logger.d(TAG, "Setting ${collectorType.name} enabled=$enabled")
            settingsRepository.setCollectorEnabled(collectorType, enabled)
        }
    }

    /**
     * Updates the collection interval for a collector.
     *
     * @param collectorType The collector to update.
     * @param intervalMs The new collection interval in milliseconds.
     * @return [Result.success] on success or [Result.failure] on error.
     */
    suspend fun updateInterval(collectorType: CollectorType, intervalMs: Long): Result<Unit> {
        return runCatching {
            require(intervalMs >= 0) { "Interval must be non-negative" }
            logger.d(TAG, "Setting ${collectorType.name} interval=${intervalMs}ms")
            settingsRepository.setCollectionInterval(collectorType, intervalMs)
        }
    }

    /**
     * Updates a full collector setting.
     *
     * @param setting The complete setting to persist.
     * @return [Result.success] on success or [Result.failure] on error.
     */
    suspend fun updateFull(setting: CollectorSetting): Result<Unit> {
        return runCatching {
            logger.d(TAG, "Updating full setting for ${setting.collectorType.name}")
            settingsRepository.updateSetting(setting)
        }
    }

    companion object {
        private const val TAG = "UpdateCollectorSettingUseCase"
    }
}
