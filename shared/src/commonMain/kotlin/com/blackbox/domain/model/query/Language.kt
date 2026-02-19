package com.blackbox.domain.model.query

/**
 * Supported query languages.
 *
 * Detected automatically from the query text by checking
 * for Hebrew Unicode characters (\u0590-\u05FF).
 */
enum class Language {
    ENGLISH,
    HEBREW,
}
