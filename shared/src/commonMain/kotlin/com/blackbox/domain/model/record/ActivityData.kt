package com.blackbox.domain.model.record

/**
 * Activity recognition data from the motion collector.
 *
 * Captures the user's physical activity state, step count,
 * and movement intensity. The [allActivities] list provides
 * confidence scores for all detected activity types.
 *
 * @property detectedActivity The most likely current activity.
 * @property confidence Confidence for the detected activity (0-100).
 * @property allActivities All detected activities with their confidence scores.
 * @property stepCountCumulative Cumulative step count from the sensor.
 * @property stepCountDelta Steps taken since the last collection cycle.
 * @property movementIntensity Normalized intensity score (0.0 to 1.0).
 */
data class ActivityData(
    val detectedActivity: ActivityType,
    val confidence: Int,
    val allActivities: List<DetectedActivity> = emptyList(),
    val stepCountCumulative: Long = 0,
    val stepCountDelta: Int = 0,
    val movementIntensity: Float = 0f,
)

/**
 * A single activity detection with its confidence score.
 *
 * @property type The detected activity type.
 * @property confidence Confidence percentage (0-100).
 */
data class DetectedActivity(
    val type: ActivityType,
    val confidence: Int,
)

/**
 * Physical activity types recognized by the Activity Recognition API.
 */
enum class ActivityType {
    /** User is stationary. */
    STILL,
    /** User is walking. */
    WALKING,
    /** User is running. */
    RUNNING,
    /** User is in a motor vehicle. */
    IN_VEHICLE,
    /** User is on a bicycle. */
    ON_BICYCLE,
    /** Device is tilting (picked up or put down). */
    TILTING,
    /** Activity could not be determined. */
    UNKNOWN,
}
