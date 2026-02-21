package com.blackbox.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android implementation of [DatabaseDriverFactory].
 *
 * Uses [AndroidSqliteDriver] with plain SQLite. Will be upgraded
 * to SQLCipher for encryption in a later step.
 *
 * @property context Application context for database file access.
 */
actual class DatabaseDriverFactory(private val context: Context) {

    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = BlackBoxDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
        )
    }

    companion object {
        private const val DATABASE_NAME = "blackbox.db"
    }
}
