package com.blackbox.android.collector

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.telephony.TelephonyManager
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.BluetoothDevice
import com.blackbox.domain.model.record.BluetoothDeviceType
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.ConnectivityData
import com.blackbox.domain.model.record.NetworkType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.util.BlackBoxLogger
import java.util.UUID

/**
 * Collects network and Bluetooth connectivity state.
 *
 * Polling-based collector that captures WiFi/cellular/Bluetooth
 * connectivity status. Useful for corroborating location data
 * and detecting environment changes.
 *
 * @property context Android context for accessing system services.
 * @property logger Logger for lifecycle and error events.
 */
class ConnectivityCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = DEFAULT_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.CONNECTIVITY

    private var sessionId: String = ""

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting connectivity collector")
        sessionId = UUID.randomUUID().toString()
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping connectivity collector")
    }

    override suspend fun collectData(): List<CollectedRecord> {
        val now = System.currentTimeMillis()

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val capabilities = network?.let { cm.getNetworkCapabilities(it) }

        val networkType = when {
            capabilities == null -> NetworkType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }

        val wifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isVpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val airplaneMode = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) != 0

        val cellularType = getCellularType()
        val carrierName = getCarrierName()

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter
        val btEnabled = btAdapter?.isEnabled == true
        val btDevices = getConnectedBluetoothDevices(btAdapter)

        val connectivityData = ConnectivityData(
            networkType = networkType,
            wifiConnected = wifiConnected,
            cellularType = cellularType,
            carrierName = carrierName,
            airplaneMode = airplaneMode,
            bluetoothEnabled = btEnabled,
            connectedBtDevices = btDevices,
            isVpnActive = isVpnActive,
        )

        val record = CollectedRecord(
            timestamp = now,
            collectorType = CollectorType.CONNECTIVITY,
            data = RecordData.Connectivity(connectivityData),
            accuracyScore = 1.0f,
            sessionId = sessionId,
            createdAt = now,
        )

        logger.d(TAG, "Connectivity collected: $networkType, bt=$btEnabled, airplane=$airplaneMode")
        return listOf(record)
    }

    /**
     * Returns the cellular network type as a string (e.g., "LTE", "5G").
     *
     * [TelephonyManager.dataNetworkType] requires READ_PHONE_STATE on API 29+,
     * which we intentionally do not request. Returns null gracefully on SecurityException.
     */
    private fun getCellularType(): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        return try {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                TelephonyManager.NETWORK_TYPE_UNKNOWN -> null
                else -> "OTHER"
            }
        } catch (_: SecurityException) {
            null // READ_PHONE_STATE not granted — cellular type not available
        }
    }

    /** Returns the mobile carrier name. */
    private fun getCarrierName(): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return tm?.networkOperatorName?.takeIf { it.isNotBlank() }
    }

    /** Returns a list of connected Bluetooth devices (requires BLUETOOTH_CONNECT on API 31+). */
    @Suppress("MissingPermission")
    private fun getConnectedBluetoothDevices(adapter: BluetoothAdapter?): List<BluetoothDevice> {
        if (adapter == null || !adapter.isEnabled) return emptyList()
        return try {
            adapter.bondedDevices
                ?.map { device ->
                    BluetoothDevice(
                        name = device.name ?: "Unknown",
                        type = mapBluetoothDeviceType(device.bluetoothClass?.majorDeviceClass),
                    )
                }
                ?: emptyList()
        } catch (_: SecurityException) {
            logger.w(TAG, "BLUETOOTH_CONNECT permission not granted")
            emptyList()
        }
    }

    /** Maps Android Bluetooth device class to domain type. */
    private fun mapBluetoothDeviceType(majorClass: Int?): BluetoothDeviceType {
        return when (majorClass) {
            0x0400 -> BluetoothDeviceType.AUDIO // AUDIO_VIDEO
            0x0200 -> BluetoothDeviceType.PHONE
            0x0100 -> BluetoothDeviceType.COMPUTER
            0x0700 -> BluetoothDeviceType.WATCH // WEARABLE
            else -> BluetoothDeviceType.OTHER
        }
    }

    companion object {
        private const val TAG = "ConnectivityCollector"

        /** Default polling interval (5 minutes). */
        private const val DEFAULT_INTERVAL_MS = 5L * 60 * 1000
    }
}
