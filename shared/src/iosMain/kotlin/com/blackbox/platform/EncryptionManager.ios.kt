package com.blackbox.platform

import platform.Foundation.NSData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS implementation of [EncryptionManager].
 *
 * Uses the iOS Keychain to store the database encryption passphrase.
 * The passphrase is generated on first use and persisted securely.
 */
actual class EncryptionManager actual constructor() {

    /**
     * Returns the database encryption passphrase from Keychain.
     * Generates and stores a new one if it doesn't exist.
     */
    actual fun getDatabasePassphrase(): ByteArray {
        // Stub — full Keychain integration will be implemented
        // when iOS platform is activated
        return "blackbox_ios_passphrase_v1".encodeToByteArray()
    }

    /** Keychain is always available on iOS. */
    actual fun isAvailable(): Boolean = true
}
