package com.blackbox.platform

/**
 * Platform-agnostic encryption manager.
 *
 * Provides the database passphrase for SQLCipher encryption.
 * On Android, backed by the Android Keystore; on iOS, by the Keychain.
 */
expect class EncryptionManager() {

    /**
     * Returns the database encryption passphrase.
     *
     * Creates and securely stores a new passphrase if one doesn't exist.
     * The passphrase is stored in platform-specific secure storage.
     *
     * @return The passphrase bytes for SQLCipher.
     */
    fun getDatabasePassphrase(): ByteArray

    /**
     * Checks if encryption is available on this device.
     *
     * @return True if secure key storage is available.
     */
    fun isAvailable(): Boolean
}
