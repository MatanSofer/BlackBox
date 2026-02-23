package com.blackbox.android.di

import com.blackbox.data.database.DatabaseDriverFactory
import com.blackbox.data.database.DatabaseFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module providing database-related singletons.
 *
 * Provides [DatabaseDriverFactory] (with Android context),
 * [DatabaseFactory], and the [BlackBoxDatabase] singleton.
 * Also seeds default collector settings on first launch.
 */
val databaseModule = module {
    single { DatabaseDriverFactory(androidContext()) }
    single {
        val factory = DatabaseFactory(get())
        val database = factory.createDatabase()
        factory.initializeDefaults(database)
        database
    }
}
