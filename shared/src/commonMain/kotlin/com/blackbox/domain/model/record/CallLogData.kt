package com.blackbox.domain.model.record

import kotlinx.serialization.Serializable

/**
 * Data captured for a single call log entry.
 *
 * The phone number is never stored in plain text — only a
 * non-reversible truncated SHA-256 hash is retained so the user
 * can recognise patterns (e.g. same caller) without exposing PII.
 *
 * @property callType Whether the call was incoming, outgoing, missed, etc.
 * @property durationSeconds Length of the call in seconds. 0 for missed/rejected calls.
 * @property numberHash SHA-256 of the cleaned E.164 number, first 8 bytes as 16 hex chars.
 * @property callTimestamp Actual call start time in epoch milliseconds.
 */
@Serializable
data class CallLogData(
    val callType: CallType,
    val durationSeconds: Int,
    val numberHash: String,
    val callTimestamp: Long,
)

/**
 * Classification of a call log entry by direction and outcome.
 */
enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    VOICEMAIL,
    UNKNOWN,
}
