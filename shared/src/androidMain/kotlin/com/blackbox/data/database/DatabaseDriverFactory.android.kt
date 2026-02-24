package com.blackbox.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Android implementation of [DatabaseDriverFactory].
 *
 * Uses [AndroidSqliteDriver] backed by SQLCipher for encrypted
 * database storage. The passphrase is provided by [EncryptionManager]
 * via the Android Keystore.
 *
 * @property context Application context for database file access.
 * @property passphrase Database encryption passphrase bytes.
 */
actual class DatabaseDriverFactory(
    private val context: Context,
    private val passphrase: ByteArray = DEFAULT_PASSPHRASE,
) {

    actual fun createDriver(): SqlDriver {
        System.loadLibrary("sqlcipher")

        val factory = SupportOpenHelperFactory(passphrase)

        return AndroidSqliteDriver(
            schema = BlackBoxDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
            factory = factory,
        )
    }

    companion object {
        private const val DATABASE_NAME = "blackbox.db"

        /** Default passphrase used when encryption manager is not available. */
        private val DEFAULT_PASSPHRASE = "blackbox_default_key".toByteArray()
    }
}
