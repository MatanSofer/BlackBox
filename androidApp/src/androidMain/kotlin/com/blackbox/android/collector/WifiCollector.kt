package com.blackbox.android.collector

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.NearbyNetwork
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.model.record.WifiData
import com.blackbox.domain.util.BlackBoxLogger
import java.util.UUID

/**
 * Collects WiFi environment data for indoor positioning and environment fingerprinting.
 *
 * This is a polling-based collector that periodically reads the current
 * WiFi connection info and scan results. WiFi fingerprints provide
 * stronger indoor positioning than GPS alone.
 *
 * Collects:
 * - Connected network SSID, BSSID, signal strength, frequency
 * - Nearby networks (up to [MAX_NEARBY_NETWORKS]) for fingerprinting
 *
 * @property context Android context for accessing WifiManager.
 * @property logger Logger for lifecycle and error events.
 */
class WifiCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = DEFAULT_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.WIFI

    private var wifiManager: WifiManager? = null
    private var sessionId: String = ""

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting WiFi collector")
        sessionId = UUID.randomUUID().toString()
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            logger.w(TAG, "WifiManager not available")
        }
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping WiFi collector")
        wifiManager = null
    }

    @SuppressLint("MissingPermission")
    override suspend fun collectData(): List<CollectedRecord> {
        val wifi = wifiManager ?: return emptyList()
        val now = System.currentTimeMillis()

        val connectionInfo = wifi.connectionInfo
        val scanResults = wifi.scanResults ?: emptyList()

        val connectedSsid = connectionInfo?.ssid
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.takeIf { it != "<unknown ssid>" }

        val connectedBssid = connectionInfo?.bssid
            ?.takeIf { it != "02:00:00:00:00:00" }

        val signalDbm = connectionInfo?.rssi
            ?.takeIf { it != -127 }

        val frequencyMhz = connectionInfo?.frequency
            ?.takeIf { it > 0 }

        val nearbyNetworks = scanResults
            .sortedByDescending { it.level }
            .take(MAX_NEARBY_NETWORKS)
            .map { result ->
                NearbyNetwork(
                    ssid = result.SSID ?: "",
                    bssid = result.BSSID ?: "",
                    rssi = result.level,
                )
            }

        val wifiData = WifiData(
            connectedSsid = connectedSsid,
            connectedBssid = connectedBssid,
            signalStrengthDbm = signalDbm,
            frequencyMhz = frequencyMhz,
            nearbyNetworks = nearbyNetworks,
            networkCount = scanResults.size,
        )

        val record = CollectedRecord(
            timestamp = now,
            collectorType = CollectorType.WIFI,
            data = RecordData.Wifi(wifiData),
            accuracyScore = computeAccuracyScore(signalDbm),
            sessionId = sessionId,
            createdAt = now,
        )

        logger.d(TAG, "WiFi collected: ssid=$connectedSsid, nearby=${nearbyNetworks.size}/${scanResults.size}")
        return listOf(record)
    }

    /**
     * Computes a 0.0–1.0 accuracy score from WiFi signal strength.
     *
     * >=-50 dBm → 1.0 (excellent), <=-90 dBm → 0.2 (poor).
     */
    private fun computeAccuracyScore(signalDbm: Int?): Float {
        if (signalDbm == null) return 0.3f
        return when {
            signalDbm >= EXCELLENT_SIGNAL_DBM -> 1.0f
            signalDbm <= POOR_SIGNAL_DBM -> 0.2f
            else -> {
                val range = EXCELLENT_SIGNAL_DBM - POOR_SIGNAL_DBM
                val normalized = (signalDbm - POOR_SIGNAL_DBM).toFloat() / range
                0.2f + (normalized * 0.8f)
            }
        }
    }

    companion object {
        private const val TAG = "WifiCollector"

        /** Default polling interval (5 minutes). */
        private const val DEFAULT_INTERVAL_MS = 5L * 60 * 1000

        /** Maximum number of nearby networks to include per scan. */
        private const val MAX_NEARBY_NETWORKS = 15

        /** Signal strength considered excellent (dBm). */
        private const val EXCELLENT_SIGNAL_DBM = -50

        /** Signal strength considered poor (dBm). */
        private const val POOR_SIGNAL_DBM = -90
    }
}
