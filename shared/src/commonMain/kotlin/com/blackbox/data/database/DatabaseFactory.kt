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
        val existingTypes = existing.map { it.collector_type }.toSet()

        val now = Clock.System.now().toEpochMilliseconds()
        val queries = database.blackBoxDatabaseQueries

        fun insertIfMissing(type: String, enabled: Long, intervalMs: Long) {
            if (type !in existingTypes) {
                queries.insertSetting(type, enabled, intervalMs, null, now)
            }
        }

        insertIfMissing("LOCATION", 1, 300_000)
        insertIfMissing("ACTIVITY", 1, 0)
        insertIfMissing("WIFI", 1, 900_000)
        insertIfMissing("APP_USAGE", 1, 300_000)
        insertIfMissing("SCREEN_STATE", 1, 0)
        insertIfMissing("AUDIO_LEVEL", 0, 900_000)
        insertIfMissing("BATTERY", 1, 1_800_000)
        insertIfMissing("CONNECTIVITY", 1, 0)
        insertIfMissing("BAROMETER", 0, 600_000)
        insertIfMissing("LIGHT", 0, 600_000)
        // CALL_LOG: DISABLED by default (sensitive — requires READ_CALL_LOG permission)
        insertIfMissing("CALL_LOG", 0, 300_000)
        // MEDIA_PLAYBACK: enabled by default (no permission required)
        insertIfMissing("MEDIA_PLAYBACK", 1, 30_000)
    }
}
