package com.blackbox.domain.model.timeline

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType

/**
 * A named group of records from a single data collector, used to display
 * the timeline in a grouped-by-collector card layout.
 *
 * The three "base" collector types (LOCATION, ACTIVITY, and the null
 * Derived Events group) always appear. The remaining eight collector types
 * appear only when the user enables raw data view in Settings.
 *
 * @property collectorType The collector that produced this group's data.
 *   `null` represents the synthetic Derived Events group.
 * @property displayName Short uppercase label shown in the card header (e.g. "LOCATION").
 * @property recordCount Number of records in this group.
 * @property lastRecordTime Epoch ms of the most recent record, used for sorting.
 * @property entries Processed [TimelineEntry] list (used for LOCATION and ACTIVITY groups).
 * @property rawRecords Raw [CollectedRecord] list (used for the eight non-base collectors).
 * @property isExpanded Whether the card body is currently visible.
 */
data class CollectorGroup(
    val collectorType: CollectorType?,
    val displayName: String,
    val recordCount: Int,
    val lastRecordTime: Long?,
    val entries: List<TimelineEntry> = emptyList(),
    val rawRecords: List<CollectedRecord> = emptyList(),
    val isExpanded: Boolean = false,
)
