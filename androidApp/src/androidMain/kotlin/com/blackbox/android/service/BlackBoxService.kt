package com.blackbox.android.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blackbox.android.MainActivity
import com.blackbox.android.R
import com.blackbox.android.collector.base.CollectorOrchestrator
import com.blackbox.domain.platform.StepCounterProvider
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service that keeps data collection running in the background.
 *
 * Creates a persistent notification to satisfy Android's foreground service
 * requirements. Manages the [CollectorOrchestrator] lifecycle: starts it
 * when the service starts and stops it when the service is destroyed.
 *
 * Uses [START_STICKY] to ensure the service restarts if killed by the system.
 */
class BlackBoxService : Service() {

    private val orchestrator: CollectorOrchestrator by inject()
    private val stepCounterProvider: StepCounterProvider by inject()
    private val logger: BlackBoxLogger by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        logger.i(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.i(TAG, "Service started (startId=$startId)")

        val notification = buildNotification()
        startForegroundCompat(notification)

        serviceScope.launch {
            // Snapshot step counter baseline as early as possible each day.
            // TYPE_STEP_COUNTER is cumulative since reboot; reading it here (at service
            // start / boot) ensures the baseline is anchored near midnight rather than
            // whenever the user first opens the Insights screen.
            try {
                stepCounterProvider.getTodaySteps()
                logger.d(TAG, "Step counter baseline anchored at service start")
            } catch (e: Exception) {
                logger.w(TAG, "Step counter baseline snapshot failed", e)
            }
        }

        serviceScope.launch {
            try {
                orchestrator.startAll()
            } catch (e: Exception) {
                logger.e(TAG, "Failed to start orchestrator", e)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        logger.i(TAG, "Service destroyed")
        serviceScope.launch {
            orchestrator.stopAll()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Calls [startForeground] with only the service types whose required
     * runtime permissions are currently granted.
     *
     * On Android 10+ (API 29), the 2-argument [startForeground] defaults to
     * ALL types declared in the manifest (including `location`). To avoid a
     * [SecurityException] before location permission is granted, we
     * explicitly pass only the types that are currently permitted.
     *
     * If the location type is granted but the app is not yet in the eligible
     * foreground state (e.g., START_STICKY restart with no visible activity),
     * we fall back gracefully to [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC].
     */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val locationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

            val serviceType = if (locationGranted) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }

            logger.d(TAG, "startForeground type=0x${serviceType.toString(16)} locationGranted=$locationGranted")

            try {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: SecurityException) {
                // Location type requires eligible foreground state (visible activity or system exemption).
                // Fall back to DATA_SYNC only so the service keeps running; the location collector
                // will be inactive until the service is restarted from a foreground context.
                logger.w(TAG, "startForeground with location type failed, retrying as dataSync only", e)
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Creates the notification channel for Android O+.
     * Channel is low importance to minimize user disruption.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the persistent foreground notification.
     */
    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "BlackBoxService"
        /** Notification channel ID for the foreground service. */
        const val CHANNEL_ID = "blackbox_collection"
        /** Notification ID for the persistent foreground notification. */
        const val NOTIFICATION_ID = 1001

        /**
         * Creates an intent to start the [BlackBoxService].
         */
        fun newIntent(context: Context): Intent {
            return Intent(context, BlackBoxService::class.java)
        }
    }
}
