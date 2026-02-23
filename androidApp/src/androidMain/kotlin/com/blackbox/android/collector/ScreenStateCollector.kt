package com.blackbox.android.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.BrightnessMode
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.model.record.ScreenState
import com.blackbox.domain.model.record.ScreenStateData
import com.blackbox.domain.usecase.record.SaveRecordUseCase
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Collects screen on/off/unlock events.
 *
 * Event-driven collector (baseIntervalMs = 0) that listens for system
 * broadcasts: ACTION_SCREEN_ON, ACTION_SCREEN_OFF, ACTION_USER_PRESENT.
 * Zero battery impact since it only reacts to system-delivered events.
 *
 * @property context Android context for registering BroadcastReceiver.
 * @property saveRecordUseCase Use case for persisting records.
 * @property logger Logger for lifecycle and error events.
 */
class ScreenStateCollector(
    private val context: Context,
    private val saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = 0, logger) {

    override val collectorType: CollectorType = CollectorType.SCREEN_STATE

    private var screenReceiver: BroadcastReceiver? = null
    private var callbackScope: CoroutineScope? = null
    private var sessionId: String = ""

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting screen state collector")
        sessionId = UUID.randomUUID().toString()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        callbackScope = scope

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val screenState = when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> ScreenState.ON
                    Intent.ACTION_SCREEN_OFF -> ScreenState.OFF
                    Intent.ACTION_USER_PRESENT -> ScreenState.UNLOCKED
                    else -> return
                }
                scope.launch {
                    handleScreenEvent(screenState)
                }
            }
        }
        screenReceiver = receiver

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        logger.i(TAG, "Screen state receiver registered")
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping screen state collector")
        screenReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                logger.w(TAG, "Receiver already unregistered")
            }
        }
        callbackScope?.cancel()
        callbackScope = null
        screenReceiver = null
    }

    override suspend fun collectData(): List<CollectedRecord> {
        // Event-driven — data arrives via broadcast receiver
        return emptyList()
    }

    /** Handles a screen state change event. */
    private suspend fun handleScreenEvent(screenState: ScreenState) {
        val now = System.currentTimeMillis()
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Settings.SettingNotFoundException) {
            null
        }

        val brightnessMode = try {
            val mode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
            )
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                BrightnessMode.AUTO
            } else {
                BrightnessMode.MANUAL
            }
        } catch (_: Settings.SettingNotFoundException) {
            null
        }

        val screenStateData = ScreenStateData(
            state = screenState,
            brightness = brightness,
            brightnessMode = brightnessMode,
            orientation = null,
            isInteractive = pm?.isInteractive == true,
        )

        val record = CollectedRecord(
            timestamp = now,
            collectorType = CollectorType.SCREEN_STATE,
            data = RecordData.ScreenState(screenStateData),
            accuracyScore = 1.0f,
            sessionId = sessionId,
            createdAt = now,
        )

        saveRecordUseCase(record)
            .onSuccess {
                logger.d(TAG, "Screen state saved: $screenState")
                emitRecords(listOf(record))
            }
            .onFailure { e ->
                logger.e(TAG, "Failed to save screen state record", e)
            }
    }

    companion object {
        private const val TAG = "ScreenStateCollector"
    }
}
