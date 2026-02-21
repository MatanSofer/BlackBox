package com.blackbox.data.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific factory for creating SQLDelight [SqlDriver] instances.
 *
 * Each platform provides its own actual implementation:
 * - Android: [AndroidSqliteDriver] (plain SQLite, upgradable to SQLCipher)
 * - iOS: [NativeSqliteDriver]
 */
expect class DatabaseDriverFactory {

    /**
     * Creates a new [SqlDriver] for the BlackBox database.
     */
    fun createDriver(): SqlDriver
}
