package com.blackbox.android.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.BatteryData
import com.blackbox.domain.model.record.BatteryHealth
import com.blackbox.domain.model.record.BatteryStatus
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.PlugType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Collects battery level and charging state.
 *
 * Event-driven collector (baseIntervalMs = 0) that listens for
 * ACTION_BATTERY_CHANGED broadcasts. Also captures battery health,
 * temperature, voltage, and power source type.
 *
 * @property context Android context for registering BroadcastReceiver.
 * @property logger Logger for lifecycle and error events.
 */
class BatteryCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = 0, logger) {

    override val collectorType: CollectorType = CollectorType.BATTERY

    private var batteryReceiver: BroadcastReceiver? = null
    private var callbackScope: CoroutineScope? = null
    private var sessionId: String = ""
    private var lastLevelPercent: Int = -1

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting battery collector")
        sessionId = UUID.randomUUID().toString()
        lastLevelPercent = -1

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        callbackScope = scope

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                scope.launch {
                    handleBatteryChanged(intent)
                }
            }
        }
        batteryReceiver = receiver

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        logger.i(TAG, "Battery receiver registered")
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping battery collector")
        batteryReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                logger.w(TAG, "Receiver already unregistered")
            }
        }
        callbackScope?.cancel()
        callbackScope = null
        batteryReceiver = null
    }

    override suspend fun collectData(): List<CollectedRecord> {
        // Event-driven — data arrives via broadcast receiver
        return emptyList()
    }

    /** Handles a battery changed broadcast. */
    private suspend fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val levelPercent = if (level >= 0 && scale > 0) (level * 100) / scale else return

        // Only record on significant changes (level change or charging state change)
        val statusRaw = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (levelPercent == lastLevelPercent && statusRaw == lastStatusRaw) return
        lastLevelPercent = levelPercent
        lastStatusRaw = statusRaw

        val now = System.currentTimeMillis()

        val status = mapBatteryStatus(statusRaw)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val plugType = mapPlugType(plugged)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            .takeIf { it > 0 }
            ?.let { it / 10f }
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            .takeIf { it > 0 }
        val healthRaw = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
        val health = mapBatteryHealth(healthRaw)

        val batteryData = BatteryData(
            levelPercent = levelPercent,
            status = status,
            plugType = plugType,
            temperatureCelsius = temperature,
            voltageMv = voltage,
            health = health,
        )

        val record = CollectedRecord(
            timestamp = now,
            collectorType = CollectorType.BATTERY,
            data = RecordData.Battery(batteryData),
            accuracyScore = 1.0f,
            sessionId = sessionId,
            createdAt = now,
        )

        logger.d(TAG, "Battery collected: $levelPercent% ($status, $plugType)")
        emitRecords(listOf(record))
    }

    private var lastStatusRaw: Int = -1

    private fun mapBatteryStatus(status: Int): BatteryStatus {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
            else -> BatteryStatus.DISCHARGING
        }
    }

    private fun mapPlugType(plugged: Int): PlugType {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            else -> PlugType.NONE
        }
    }

    private fun mapBatteryHealth(health: Int): BatteryHealth {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            else -> BatteryHealth.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "BatteryCollector"
    }
}
