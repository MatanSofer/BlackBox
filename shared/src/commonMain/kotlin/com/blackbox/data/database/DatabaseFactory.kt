package com.blackbox.data.database

import kotlinx.datetime.Clock

/**
 * Factory for creating and initializing the [BlackBoxDatabase].
 *
 * Creates the database instance from a platform-specific driver
 * and provides initialization of default data (collector settings).
 *
 * @property driverFactory Platform-specific [DatabaseDriverFactory].
 */
class DatabaseFactory(private val driverFactory: DatabaseDriverFactory) {

    /**
     * Creates a [BlackBoxDatabase] instance.
     */
    fun createDatabase(): BlackBoxDatabase {
        val driver = driverFactory.createDriver()
        return BlackBoxDatabase(driver)
    }

    /**
     * Inserts default [CollectorSetting] rows if the table is empty.
     *
     * Called on first launch to populate initial collector configuration.
     * Each collector gets its default enabled state and polling interval.
     */
    fun initializeDefaults(database: BlackBoxDatabase) {
        val existing = database.blackBoxDatabaseQueries.getAllSettings().executeAsList()
        if (existing.isNotEmpty()) return

        val now = Clock.System.now().toEpochMilliseconds()
        val queries = database.blackBoxDatabaseQueries

        // LOCATION: 5 min, enabled
        queries.insertSetting("LOCATION", 1, 300_000, null, now)
        // ACTIVITY: event-driven, enabled
        queries.insertSetting("ACTIVITY", 1, 0, null, now)
        // WIFI: 15 min, enabled
        queries.insertSetting("WIFI", 1, 900_000, null, now)
        // APP_USAGE: 5 min, enabled
        queries.insertSetting("APP_USAGE", 1, 300_000, null, now)
        // SCREEN_STATE: event-driven, enabled
        queries.insertSetting("SCREEN_STATE", 1, 0, null, now)
        // AUDIO_LEVEL: 15 min, DISABLED by default
        queries.insertSetting("AUDIO_LEVEL", 0, 900_000, null, now)
        // BATTERY: 30 min, enabled
        queries.insertSetting("BATTERY", 1, 1_800_000, null, now)
        // CONNECTIVITY: event-driven, enabled
        queries.insertSetting("CONNECTIVITY", 1, 0, null, now)
        // BAROMETER: 10 min, DISABLED by default
        queries.insertSetting("BAROMETER", 0, 600_000, null, now)
        // LIGHT: 10 min, DISABLED by default
        queries.insertSetting("LIGHT", 0, 600_000, null, now)
    }
}
