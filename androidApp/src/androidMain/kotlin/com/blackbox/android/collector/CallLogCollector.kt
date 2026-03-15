package com.blackbox.android.collector

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.CallLogData
import com.blackbox.domain.model.record.CallType
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

/**
 * Collects call log entries from the system [CallLog.Calls] content provider.
 *
 * Polls every [POLL_INTERVAL_MS] and queries only for calls that occurred
 * after [lastQueryTime], so no entry is processed twice. If the service was
 * killed and restarted, the lookback window is capped at [MAX_LOOKBACK_MS]
 * to prevent re-processing a day's worth of old calls.
 *
 * Phone numbers are never stored in plain text — only a non-reversible
 * truncated SHA-256 hash (first 8 bytes → 16 hex chars) is persisted.
 *
 * Requires [Manifest.permission.READ_CALL_LOG]. If the permission is absent
 * the collector skips silently every cycle without crashing.
 *
 * @property context Android context for the ContentResolver and permission checks.
 * @property logger Logger for lifecycle and error events.
 */
class CallLogCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = POLL_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.CALL_LOG

    private var sessionId: String = ""
    private var lastQueryTime: Long = 0L

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting call log collector")
        sessionId = UUID.randomUUID().toString()
        // Restore lastQueryTime from persistent storage so calls between service
        // restarts are not lost. Fall back to MAX_LOOKBACK_MS on first ever start.
        lastQueryTime = prefs.getLong(KEY_LAST_QUERY_TIME, System.currentTimeMillis() - MAX_LOOKBACK_MS)
        logger.d(TAG, "Resuming from lastQueryTime=$lastQueryTime")
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping call log collector")
    }

    override suspend fun collectData(): List<CollectedRecord> {
        if (!hasPermission()) {
            logger.w(TAG, "READ_CALL_LOG not granted — skipping cycle")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val queryStart = lastQueryTime.coerceAtLeast(now - MAX_LOOKBACK_MS)

        val records = withContext(Dispatchers.IO) {
            queryCallsSince(queryStart, now)
        }

        lastQueryTime = now
        prefs.edit().putLong(KEY_LAST_QUERY_TIME, now).apply()
        logger.d(TAG, "Collected ${records.size} call log entries since ${queryStart}")
        return records
    }

    private fun queryCallsSince(since: Long, now: Long): List<CollectedRecord> {
        val projection = arrayOf(
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,  // cached contact display name, no READ_CONTACTS needed
        )
        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(since.toString())
        val sortOrder = "${CallLog.Calls.DATE} ASC"

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        ) ?: return emptyList()

        val results = mutableListOf<CollectedRecord>()

        cursor.use { c ->
            val typeIdx = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val durationIdx = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val dateIdx = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val numberIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME) // -1 if column absent

            while (c.moveToNext()) {
                val callType = mapCallType(c.getInt(typeIdx))
                val durationSec = c.getInt(durationIdx)
                val callDate = c.getLong(dateIdx)
                val rawNumber = c.getString(numberIdx) ?: ""
                val contactName = if (nameIdx >= 0) c.getString(nameIdx)?.takeIf { it.isNotBlank() } else null

                val data = CallLogData(
                    callType = callType,
                    durationSeconds = durationSec,
                    numberHash = hashNumber(rawNumber),
                    callTimestamp = callDate,
                    contactName = contactName,
                )

                results.add(
                    CollectedRecord(
                        timestamp = callDate,
                        collectorType = CollectorType.CALL_LOG,
                        data = RecordData.CallLog(data),
                        accuracyScore = 1.0f,
                        sessionId = sessionId,
                        createdAt = now,
                    )
                )
            }
        }

        return results
    }

    private fun mapCallType(androidType: Int): CallType = when (androidType) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.UNKNOWN
    }

    /**
     * Produces a 16-character hex string from the first 8 bytes of the
     * SHA-256 digest of the cleaned phone number. Not reversible.
     */
    private fun hashNumber(number: String): String {
        val cleaned = number.filter { it.isDigit() || it == '+' }
        val digest = MessageDigest.getInstance("SHA-256").digest(cleaned.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CallLogCollector"

        /** Poll every 5 minutes. */
        private const val POLL_INTERVAL_MS = 5L * 60 * 1_000

        /**
         * Maximum lookback window. 30 days ensures historical calls already in
         * the system call log are imported on first start, while still bounding
         * the initial query to a reasonable range.
         */
        private const val MAX_LOOKBACK_MS = 30L * 24 * 60 * 60 * 1_000

        private const val PREFS_NAME = "blackbox_calllog_collector"
        private const val KEY_LAST_QUERY_TIME = "last_query_time"
    }
}
