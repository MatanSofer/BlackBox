package com.blackbox.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.blackbox.domain.util.BlackBoxLogger
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore wrapper for managing encryption keys.
 *
 * Generates and stores AES-256/GCM keys in the Android Keystore,
 * which provides hardware-backed key storage on supported devices.
 * Used primarily for encrypting the SQLCipher database passphrase.
 *
 * The database passphrase is a random byte array that is encrypted
 * with the Keystore key and stored in SharedPreferences. Android
 * Keystore keys do not expose raw bytes (getEncoded() returns null),
 * so the Keystore is used only for encrypt/decrypt operations.
 *
 * @property context Application context for SharedPreferences access.
 * @property logger Logger for operation tracking.
 */
class KeyManager(
    private val context: Context,
    private val logger: BlackBoxLogger,
) {

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Retrieves the database encryption passphrase, generating one on first use.
     *
     * Android Keystore keys do not expose raw bytes (`getEncoded()` returns null),
     * so this method generates a random passphrase, encrypts it with the Keystore
     * key, and stores the encrypted form in SharedPreferences. On subsequent calls
     * the encrypted passphrase is loaded and decrypted.
     *
     * @return Raw passphrase bytes suitable for SQLCipher.
     */
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedCiphertext = prefs.getString(PREF_KEY_CIPHERTEXT, null)
        val storedIv = prefs.getString(PREF_KEY_IV, null)

        if (storedCiphertext != null && storedIv != null) {
            logger.d(TAG, "Loading existing database passphrase")
            val encrypted = EncryptedData(
                ciphertext = Base64.decode(storedCiphertext, Base64.NO_WRAP),
                iv = Base64.decode(storedIv, Base64.NO_WRAP),
            )
            return decrypt(encrypted)
        }

        logger.d(TAG, "Generating new database passphrase")
        val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES).also {
            SecureRandom().nextBytes(it)
        }

        val encrypted = encrypt(passphrase)
        prefs.edit()
            .putString(PREF_KEY_CIPHERTEXT, Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
            .putString(PREF_KEY_IV, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
            .apply()

        return passphrase
    }

    /**
     * Retrieves the database encryption key, generating one if it doesn't exist.
     *
     * @return The AES [SecretKey] for database encryption.
     */
    fun getOrCreateDatabaseKey(): SecretKey {
        return getKey(DATABASE_KEY_ALIAS) ?: generateKey(DATABASE_KEY_ALIAS)
    }

    /**
     * Encrypts data using the database key.
     *
     * @param plaintext The data to encrypt.
     * @return [EncryptedData] containing the ciphertext and IV.
     */
    fun encrypt(plaintext: ByteArray): EncryptedData {
        val key = getOrCreateDatabaseKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv

        logger.d(TAG, "Data encrypted: ${plaintext.size} bytes → ${ciphertext.size} bytes")
        return EncryptedData(ciphertext = ciphertext, iv = iv)
    }

    /**
     * Decrypts data using the database key.
     *
     * @param encryptedData The encrypted data with IV.
     * @return The decrypted plaintext bytes.
     */
    fun decrypt(encryptedData: EncryptedData): ByteArray {
        val key = getOrCreateDatabaseKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plaintext = cipher.doFinal(encryptedData.ciphertext)
        logger.d(TAG, "Data decrypted: ${encryptedData.ciphertext.size} bytes → ${plaintext.size} bytes")
        return plaintext
    }

    /**
     * Checks if a key exists in the Keystore.
     *
     * @param alias The key alias to check.
     * @return True if the key exists.
     */
    fun hasKey(alias: String = DATABASE_KEY_ALIAS): Boolean {
        return keyStore.containsAlias(alias)
    }

    /**
     * Deletes a key from the Keystore.
     *
     * @param alias The key alias to delete.
     */
    fun deleteKey(alias: String = DATABASE_KEY_ALIAS) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
            logger.d(TAG, "Key deleted: $alias")
        }
    }

    private fun getKey(alias: String): SecretKey? {
        return if (keyStore.containsAlias(alias)) {
            (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } else {
            null
        }
    }

    private fun generateKey(alias: String): SecretKey {
        logger.d(TAG, "Generating new AES key: $alias")

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val TAG = "KeyManager"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val DATABASE_KEY_ALIAS = "blackbox_db_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PASSPHRASE_LENGTH_BYTES = 32
        private const val PREFS_NAME = "blackbox_key_prefs"
        private const val PREF_KEY_CIPHERTEXT = "db_passphrase_enc"
        private const val PREF_KEY_IV = "db_passphrase_iv"
    }
}

/**
 * Container for encrypted data with initialization vector.
 *
 * @property ciphertext The encrypted bytes.
 * @property iv The initialization vector used during encryption.
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        return 31 * ciphertext.contentHashCode() + iv.contentHashCode()
    }
}
