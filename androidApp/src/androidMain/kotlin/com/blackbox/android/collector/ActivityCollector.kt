package com.blackbox.android.collector

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.android.collector.receiver.ActivityTransitionReceiver
import com.blackbox.domain.model.record.ActivityData
import com.blackbox.domain.model.record.ActivityType
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.util.BlackBoxLogger
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity as GmsDetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.sqrt

/**
 * Collects activity recognition data and step counts.
 *
 * This is an event-driven collector (baseIntervalMs = 0) that uses:
 * - [ActivityRecognitionClient] for detecting activity transitions
 *   (STILL, WALKING, RUNNING, IN_VEHICLE, ON_BICYCLE)
 * - [SensorManager] with TYPE_STEP_COUNTER for cumulative step counts
 *
 * Activity transitions are extremely battery-efficient as Android batches
 * them at the OS level. Step counts are sampled from the hardware pedometer.
 *
 * @property context Android context for accessing activity recognition and sensors.
 * @property logger Logger for lifecycle and error events.
 */
class ActivityCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = 0, logger) {

    override val collectorType: CollectorType = CollectorType.ACTIVITY

    private var activityClient: ActivityRecognitionClient? = null
    private var transitionReceiver: ActivityTransitionReceiver? = null
    private var transitionPendingIntent: PendingIntent? = null
    private var sensorManager: SensorManager? = null
    private var stepListener: SensorEventListener? = null
    private var callbackScope: CoroutineScope? = null
    private var sessionId: String = ""

    /** Last known cumulative step count from the hardware pedometer. */
    private var lastStepCount: Long = -1L

    /** Cumulative step count at the start of this session. */
    private var sessionStartStepCount: Long = -1L

    /** Current detected activity state. */
    private var currentActivity: ActivityType = ActivityType.UNKNOWN
    private var currentConfidence: Int = 0

    /**
     * Wall-clock time when this collector session started.
     * Any transition event whose converted timestamp is before this value is a
     * historical delivery from Android's buffer (e.g., events that occurred
     * before the app was reinstalled) and must be discarded.
     */
    private var sessionStartMs: Long = 0L

    /**
     * Set of `elapsedRealtimeNanos` values already processed this session.
     * The GMS Activity Recognition API can deliver the same event multiple times
     * in one batch; this set prevents saving duplicate records.
     */
    private val processedEventNanos = mutableSetOf<Long>()

    @SuppressLint("MissingPermission")
    override fun onCollectorStarted() {
        logger.i(TAG, "Starting activity recognition")
        sessionId = UUID.randomUUID().toString()
        sessionStartMs = System.currentTimeMillis()
        processedEventNanos.clear()
        lastStepCount = -1L
        sessionStartStepCount = -1L
        currentActivity = ActivityType.UNKNOWN
        currentConfidence = 0

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        callbackScope = scope

        setupActivityTransitions(scope)
        setupStepCounter()

        logger.i(TAG, "Activity collector started with sessionId=$sessionId")
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping activity recognition")

        removeActivityTransitions()
        removeStepCounter()

        callbackScope?.cancel()
        callbackScope = null

        logger.i(TAG, "Activity collector stopped")
    }

    override suspend fun collectData(): List<CollectedRecord> {
        // Event-driven collector — data arrives via transition callbacks
        return emptyList()
    }

    /**
     * Registers activity transition updates with Google Play Services.
     * Transitions are delivered via a [BroadcastReceiver].
     */
    @SuppressLint("MissingPermission")
    private fun setupActivityTransitions(scope: CoroutineScope) {
        val client = ActivityRecognition.getClient(context)
        activityClient = client

        val transitions = buildTransitionList()
        val request = ActivityTransitionRequest(transitions)

        val intent = Intent(ACTION_ACTIVITY_TRANSITION)
            .setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        transitionPendingIntent = pi

        val receiver = ActivityTransitionReceiver()
        receiver.onTransitionListener = ActivityTransitionReceiver.OnTransitionListener {
                activityType, transitionType, elapsedRealtimeNanos ->
            if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                scope.launch {
                    handleActivityTransition(activityType, elapsedRealtimeNanos)
                }
            }
        }
        transitionReceiver = receiver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_ACTIVITY_TRANSITION),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_ACTIVITY_TRANSITION))
        }

        client.requestActivityTransitionUpdates(request, pi)
            .addOnSuccessListener {
                logger.i(TAG, "Activity transition updates registered")
            }
            .addOnFailureListener { e ->
                logger.e(TAG, "Failed to register activity transitions", e)
            }
    }

    /** Builds the list of activity transitions to monitor (ENTER only). */
    private fun buildTransitionList(): List<ActivityTransition> {
        val activityTypes = listOf(
            GmsDetectedActivity.STILL,
            GmsDetectedActivity.WALKING,
            GmsDetectedActivity.RUNNING,
            GmsDetectedActivity.IN_VEHICLE,
            GmsDetectedActivity.ON_BICYCLE,
        )

        return activityTypes.map { type ->
            ActivityTransition.Builder()
                .setActivityType(type)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        }
    }

    /** Registers a step counter sensor listener. */
    private fun setupStepCounter() {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm
        if (sm == null) {
            logger.w(TAG, "SensorManager not available, step counting disabled")
            return
        }

        val stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            logger.w(TAG, "Step counter sensor not available")
            return
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val cumulativeSteps = event.values[0].toLong()
                if (sessionStartStepCount < 0) {
                    sessionStartStepCount = cumulativeSteps
                }
                lastStepCount = cumulativeSteps
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // No action needed
            }
        }
        stepListener = listener

        sm.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        logger.d(TAG, "Step counter registered")
    }

    /** Removes activity transition updates and unregisters the receiver. */
    private fun removeActivityTransitions() {
        transitionPendingIntent?.let { pi ->
            activityClient?.removeActivityTransitionUpdates(pi)
        }
        transitionReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                logger.w(TAG, "Receiver already unregistered")
            }
        }
        activityClient = null
        transitionReceiver = null
        transitionPendingIntent = null
    }

    /** Unregisters the step counter sensor listener. */
    private fun removeStepCounter() {
        stepListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
        sensorManager = null
        stepListener = null
    }

    /**
     * Handles an activity transition event.
     *
     * Maps the Google activity type to the domain [ActivityType],
     * builds an [ActivityData] with current step counts, and saves
     * the record.
     */
    private suspend fun handleActivityTransition(
        gmsActivityType: Int,
        elapsedRealtimeNanos: Long,
    ) {
        val activityType = mapGoogleActivityType(gmsActivityType)
        val confidence = 100 // Transition API fires only with high confidence
        val now = System.currentTimeMillis()

        // Convert elapsed realtime to wall clock
        val elapsedNow = SystemClock.elapsedRealtimeNanos()
        val timestampMs = now - ((elapsedNow - elapsedRealtimeNanos) / 1_000_000)

        // Discard events older than this session's start time — these are historical
        // transitions buffered by Android and delivered on re-registration (e.g. after
        // the app was reinstalled). They do not belong to the current session.
        if (timestampMs < sessionStartMs) {
            logger.d(TAG, "Discarding historical activity event: $activityType at $timestampMs (session started at $sessionStartMs)")
            return
        }

        // Deduplicate: the GMS API sometimes delivers the same event multiple times
        // in one batch (identical elapsedRealtimeNanos). Only process each unique event once.
        if (!processedEventNanos.add(elapsedRealtimeNanos)) {
            logger.d(TAG, "Discarding duplicate activity event: $activityType at $timestampMs")
            return
        }

        currentActivity = activityType
        currentConfidence = confidence

        val stepDelta = computeStepDelta()

        val activityData = ActivityData(
            detectedActivity = activityType,
            confidence = confidence,
            allActivities = listOf(
                com.blackbox.domain.model.record.DetectedActivity(
                    type = activityType,
                    confidence = confidence,
                ),
            ),
            stepCountCumulative = if (lastStepCount >= 0) lastStepCount else 0,
            stepCountDelta = stepDelta,
            movementIntensity = estimateIntensity(activityType),
        )

        val record = CollectedRecord(
            timestamp = timestampMs,
            collectorType = CollectorType.ACTIVITY,
            data = RecordData.Activity(activityData),
            accuracyScore = 1.0f,
            sessionId = sessionId,
            createdAt = now,
        )

        logger.d(TAG, "Activity collected: $activityType (confidence=$confidence, steps=$stepDelta)")
        emitRecords(listOf(record))
    }

    /**
     * Maps Google's activity type integer to the domain [ActivityType].
     */
    private fun mapGoogleActivityType(gmsType: Int): ActivityType {
        return when (gmsType) {
            GmsDetectedActivity.STILL -> ActivityType.STILL
            GmsDetectedActivity.WALKING -> ActivityType.WALKING
            GmsDetectedActivity.RUNNING -> ActivityType.RUNNING
            GmsDetectedActivity.IN_VEHICLE -> ActivityType.IN_VEHICLE
            GmsDetectedActivity.ON_BICYCLE -> ActivityType.ON_BICYCLE
            GmsDetectedActivity.TILTING -> ActivityType.TILTING
            else -> ActivityType.UNKNOWN
        }
    }

    /** Computes step delta since last reported count. */
    private fun computeStepDelta(): Int {
        if (lastStepCount < 0 || sessionStartStepCount < 0) return 0
        return (lastStepCount - sessionStartStepCount).toInt().coerceAtLeast(0)
    }

    /**
     * Estimates movement intensity from the activity type.
     *
     * Since we use transition API (no continuous accelerometer),
     * we approximate intensity from the detected activity.
     */
    private fun estimateIntensity(activityType: ActivityType): Float {
        return when (activityType) {
            ActivityType.STILL -> 0.0f
            ActivityType.TILTING -> 0.1f
            ActivityType.WALKING -> 0.4f
            ActivityType.ON_BICYCLE -> 0.6f
            ActivityType.RUNNING -> 0.8f
            ActivityType.IN_VEHICLE -> 0.3f
            ActivityType.UNKNOWN -> 0.0f
        }
    }

    companion object {
        private const val TAG = "ActivityCollector"
        private const val ACTION_ACTIVITY_TRANSITION =
            "com.blackbox.android.ACTION_ACTIVITY_TRANSITION"
        private const val REQUEST_CODE = 1001
    }
}
