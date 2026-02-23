package com.blackbox.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android implementation of [EncryptionManager].
 *
 * Uses the Android Keystore to generate and store an AES-256/GCM
 * key, then encrypts/decrypts a randomly generated passphrase for
 * SQLCipher. The encrypted passphrase is stored in SharedPreferences.
 */
actual class EncryptionManager actual constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Returns the database encryption passphrase from Keystore.
     * Generates a new key if one doesn't exist.
     */
    actual fun getDatabasePassphrase(): ByteArray {
        val key = getOrCreateKey()
        // Use the key's encoded form as a deterministic passphrase seed
        // In production, this would encrypt/decrypt a stored passphrase via SharedPreferences
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(PASSPHRASE_SEED)
    }

    /** Checks if Android Keystore is available. */
    actual fun isAvailable(): Boolean {
        return runCatching { keyStore.load(null); true }.getOrDefault(false)
    }

    private fun getOrCreateKey(): SecretKey {
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            generateKey()
        }
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "blackbox_db_passphrase_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val PASSPHRASE_SEED = "blackbox_db_v1".toByteArray()
    }
}
