package com.blackbox.domain.util

import com.blackbox.domain.model.record.CollectorType

/**
 * Structured domain error hierarchy for the BlackBox application.
 *
 * All domain-layer failures are represented as one of these sealed
 * types, allowing ViewModels and UI to handle errors in a type-safe
 * and localizable way.
 */
sealed class BlackBoxError : Exception() {

    /** Database operation failed (read, write, migration). */
    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : BlackBoxError()

    /** Natural language query could not be parsed. */
    data class QueryParsingError(
        val query: String,
        val reason: String,
    ) : BlackBoxError() {
        override val message: String get() = "Failed to parse query \"$query\": $reason"
    }

    /** A data collector encountered an error. */
    data class CollectorError(
        val collector: CollectorType,
        override val message: String,
    ) : BlackBoxError()

    /** A required runtime permission was denied. */
    data class PermissionDenied(
        val permission: String,
    ) : BlackBoxError() {
        override val message: String get() = "Permission denied: $permission"
    }

    /** Not enough data to produce a meaningful result. */
    data object InsufficientData : BlackBoxError() {
        override val message: String get() = "Insufficient data to complete this operation"
    }

    /** Query executed successfully but returned no matching records. */
    data object NoResultsFound : BlackBoxError() {
        override val message: String get() = "No results found"
    }

    /** Network or connectivity error (future use). */
    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : BlackBoxError()

    /** Encryption or decryption operation failed. */
    data class EncryptionError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : BlackBoxError()
}
