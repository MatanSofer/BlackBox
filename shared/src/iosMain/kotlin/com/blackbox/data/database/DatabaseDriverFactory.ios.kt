package com.blackbox.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS implementation of [DatabaseDriverFactory].
 *
 * Uses [NativeSqliteDriver] for SQLite access on Apple platforms.
 */
actual class DatabaseDriverFactory {

    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = BlackBoxDatabase.Schema,
            name = DATABASE_NAME,
        )
    }

    companion object {
        private const val DATABASE_NAME = "blackbox.db"
    }
}
