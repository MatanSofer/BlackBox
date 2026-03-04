package com.blackbox.android.collector

import android.Manifest
import android.content.Context
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

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting call log collector")
        sessionId = UUID.randomUUID().toString()
        lastQueryTime = System.currentTimeMillis() - POLL_INTERVAL_MS
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
        logger.d(TAG, "Collected ${records.size} call log entries since ${queryStart}")
        return records
    }

    private fun queryCallsSince(since: Long, now: Long): List<CollectedRecord> {
        val projection = arrayOf(
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.NUMBER,
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

            while (c.moveToNext()) {
                val callType = mapCallType(c.getInt(typeIdx))
                val durationSec = c.getInt(durationIdx)
                val callDate = c.getLong(dateIdx)
                val rawNumber = c.getString(numberIdx) ?: ""

                val data = CallLogData(
                    callType = callType,
                    durationSeconds = durationSec,
                    numberHash = hashNumber(rawNumber),
                    callTimestamp = callDate,
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

        /** Never look back more than 24 hours to recover from service restarts. */
        private const val MAX_LOOKBACK_MS = 24L * 60 * 60 * 1_000
    }
}
