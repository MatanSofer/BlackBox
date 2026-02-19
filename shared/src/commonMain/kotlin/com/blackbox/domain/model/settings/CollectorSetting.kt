package com.blackbox.domain.model.settings

import com.blackbox.domain.model.record.CollectorType

/**
 * User configuration for a single data collector.
 *
 * Each collector has an independent enable/disable toggle and
 * a configurable collection interval. Settings are persisted in
 * the CollectorSetting database table.
 *
 * @property collectorType Which collector this setting applies to.
 * @property isEnabled Whether this collector is currently active.
 * @property collectionIntervalMs Polling interval in milliseconds (0 = event-driven).
 * @property customConfigJson JSON object with collector-specific configuration.
 * @property updatedAt When this setting was last changed (epoch ms).
 */
data class CollectorSetting(
    val collectorType: CollectorType,
    val isEnabled: Boolean = true,
    val collectionIntervalMs: Long,
    val customConfigJson: String? = null,
    val updatedAt: Long = 0,
)
