package com.blackbox.data.mapper

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.data.database.CollectorSetting as DbSetting
import com.blackbox.domain.model.settings.CollectorSetting as DomainSetting

/**
 * Maps between SQLDelight [DbSetting] and domain [DomainSetting].
 */
object SettingsMapper {

    /**
     * Converts a SQLDelight [DbSetting] row to a domain [DomainSetting].
     */
    fun toDomain(db: DbSetting): DomainSetting = DomainSetting(
        collectorType = CollectorType.valueOf(db.collector_type),
        isEnabled = db.is_enabled != 0L,
        collectionIntervalMs = db.collection_interval_ms,
        customConfigJson = db.custom_config_json,
        updatedAt = db.updated_at,
    )
}
