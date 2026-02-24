package com.blackbox.android.di

import com.blackbox.android.security.KeyManager
import com.blackbox.data.database.DatabaseDriverFactory
import com.blackbox.data.database.DatabaseFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module providing database-related singletons.
 *
 * Provides [DatabaseDriverFactory] (with Android context and
 * encryption passphrase from [KeyManager]), [DatabaseFactory],
 * and the [BlackBoxDatabase] singleton. Seeds default collector
 * settings on first launch.
 */
val databaseModule = module {
    single {
        val keyManager: KeyManager = get()
        val passphrase = keyManager.getOrCreateDatabasePassphrase()
        DatabaseDriverFactory(androidContext(), passphrase)
    }
    single {
        val factory = DatabaseFactory(get())
        val database = factory.createDatabase()
        factory.initializeDefaults(database)
        database
    }
}
