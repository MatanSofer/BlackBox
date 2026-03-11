package com.blackbox.domain.model.sleep

/**
 * A detected sleep session inferred from screen-state and activity records.
 *
 * Sleep is detected by finding long continuous screen-off periods during
 * night hours (previous 8pm → morning 10am). Brief wake-ups under
 * 30 minutes are merged so they don't split a session.
 *
 * @property date "yyyy-MM-dd" of the morning the session ended (wake-up day).
 * @property sleepStart Epoch ms when the screen went dark and sleep began.
 * @property wakeTime Epoch ms when the screen turned on again.
 * @property durationMs Total sleep duration in milliseconds.
 * @property durationMinutes Convenience: [durationMs] converted to whole minutes.
 * @property quality Inferred quality tier based on duration.
 */
data class SleepSession(
    val date: String,
    val sleepStart: Long,
    val wakeTime: Long,
    val durationMs: Long,
    val durationMinutes: Int,
    val quality: SleepQuality,
)

/**
 * Sleep quality tiers inferred from session duration.
 */
enum class SleepQuality {
    /** Less than 6 hours. */
    POOR,
    /** 6 to 7 hours. */
    FAIR,
    /** 7 to 9 hours — optimal range. */
    GOOD,
    /** More than 9 hours. */
    LONG,
}
