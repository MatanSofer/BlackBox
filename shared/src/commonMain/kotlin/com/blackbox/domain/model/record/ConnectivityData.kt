package com.blackbox.domain.model.record

import kotlinx.serialization.Serializable

/**
 * Network and Bluetooth connectivity data.
 *
 * Captures the device's connectivity state including WiFi,
 * cellular, Bluetooth, and VPN status. Useful for corroborating
 * location data and detecting environment changes.
 *
 * @property networkType Primary network connection type.
 * @property wifiConnected Whether WiFi is currently connected.
 * @property cellularType Type of cellular connection (e.g., "LTE", "5G").
 * @property carrierName Mobile carrier name.
 * @property airplaneMode Whether airplane mode is enabled.
 * @property bluetoothEnabled Whether Bluetooth is turned on.
 * @property connectedBtDevices List of connected Bluetooth devices.
 * @property isVpnActive Whether a VPN connection is active.
 */
@Serializable
data class ConnectivityData(
    val networkType: NetworkType,
    val wifiConnected: Boolean = false,
    val cellularType: String? = null,
    val carrierName: String? = null,
    val airplaneMode: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val connectedBtDevices: List<BluetoothDevice> = emptyList(),
    val isVpnActive: Boolean = false,
)

/**
 * Primary network connection type.
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    NONE,
}

/**
 * A connected Bluetooth device.
 *
 * @property name Device display name.
 * @property type Device category.
 */
@Serializable
data class BluetoothDevice(
    val name: String,
    val type: BluetoothDeviceType,
)

/**
 * Bluetooth device category.
 */
enum class BluetoothDeviceType {
    AUDIO,
    CAR,
    WATCH,
    PHONE,
    COMPUTER,
    OTHER,
}
