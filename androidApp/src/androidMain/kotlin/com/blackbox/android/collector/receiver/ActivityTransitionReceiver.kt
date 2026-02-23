package com.blackbox.android.collector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult

/**
 * BroadcastReceiver for activity transition events from Google Play Services.
 *
 * Receives [ActivityTransitionResult] intents when the user's physical
 * activity state changes (e.g., STILL → WALKING). Delegates processing
 * to a registered [OnTransitionListener].
 *
 * Registered dynamically by [ActivityCollector] — not declared in the manifest.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    /** Callback for delivering parsed activity transition events. */
    var onTransitionListener: OnTransitionListener? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            onTransitionListener?.onTransition(
                activityType = event.activityType,
                transitionType = event.transitionType,
                elapsedRealtimeNanos = event.elapsedRealTimeNanos,
            )
        }
    }

    /**
     * Listener for activity transition events.
     */
    fun interface OnTransitionListener {
        /**
         * Called when an activity transition occurs.
         *
         * @param activityType Google's DetectedActivity type constant.
         * @param transitionType ACTIVITY_TRANSITION_ENTER or ACTIVITY_TRANSITION_EXIT.
         * @param elapsedRealtimeNanos Elapsed realtime when the transition occurred.
         */
        fun onTransition(activityType: Int, transitionType: Int, elapsedRealtimeNanos: Long)
    }
}
