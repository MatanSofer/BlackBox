package com.blackbox.domain.model.record

import kotlinx.serialization.Serializable

/**
 * WiFi environment data captured by the WiFi collector.
 *
 * Contains info about the currently connected network and
 * a snapshot of nearby networks for environment fingerprinting.
 * WiFi data provides stronger indoor positioning than GPS alone.
 *
 * @property connectedSsid SSID of the connected network (null if disconnected).
 * @property connectedBssid BSSID (MAC address) of the connected AP.
 * @property signalStrengthDbm Signal strength in dBm (negative value).
 * @property frequencyMhz Frequency of the connected network in MHz.
 * @property nearbyNetworks List of visible networks for fingerprinting.
 * @property networkCount Total number of visible networks.
 */
@Serializable
data class WifiData(
    val connectedSsid: String? = null,
    val connectedBssid: String? = null,
    val signalStrengthDbm: Int? = null,
    val frequencyMhz: Int? = null,
    val nearbyNetworks: List<NearbyNetwork> = emptyList(),
    val networkCount: Int = 0,
)

/**
 * A nearby WiFi network detected during a scan.
 *
 * @property ssid Network name.
 * @property bssid Access point MAC address.
 * @property rssi Received signal strength indicator in dBm.
 */
@Serializable
data class NearbyNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
)
