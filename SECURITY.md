# BlackBox — Security & Privacy Implementation Guide

---

## 1. Security Architecture Overview

```
┌──────────────────────────────────────────┐
│              APP ACCESS                   │
│  Biometric / PIN authentication          │
│  Auto-lock after 1 min background        │
├──────────────────────────────────────────┤
│            DATABASE LAYER                 │
│  SQLCipher: AES-256-CBC encryption       │
│  Key stored in Android Keystore          │
│  (hardware-backed StrongBox when avail)  │
├──────────────────────────────────────────┤
│           INTEGRITY LAYER                 │
│  Daily hash chain (SHA-256)              │
│  Each day's hash includes previous day   │
│  Tamper detection on verification        │
├──────────────────────────────────────────┤
│            EXPORT LAYER                   │
│  AES-256-GCM encryption                  │
│  PBKDF2 key from user password           │
│  Integrity hash included                 │
└──────────────────────────────────────────┘
```

---

## 2. Database Encryption (SQLCipher)

### 2.1 Setup

```kotlin
// shared/data/database/EncryptedDatabaseDriverFactory.kt (Android actual)
actual class DatabaseDriverFactory(
    private val context: Context,
    private val keyManager: KeyManager,
) {
    actual fun create(): SqlDriver {
        val passphrase = keyManager.getDatabaseKey()

        return AndroidSqliteDriver(
            schema = BlackBoxDatabase.Schema,
            context = context,
            name = "blackbox.db",
            factory = SupportOpenHelperFactory(passphrase.toByteArray())
        )
    }
}
```

### 2.2 Key Management

```kotlin
// androidApp/security/KeyManager.kt
class AndroidKeyManager(private val context: Context) : KeyManager {

    companion object {
        private const val KEY_ALIAS = "blackbox_master_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    /**
     * Generate or retrieve the master encryption key from Android Keystore.
     * This key never leaves the secure hardware.
     */
    fun getDatabaseKey(): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateMasterKey()
        }

        // Use the Keystore key to derive a passphrase for SQLCipher
        return derivePassphrase(keyStore.getKey(KEY_ALIAS, null) as SecretKey)
    }

    private fun generateMasterKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            setUserAuthenticationRequired(false) // Key accessible without biometric
            // Use StrongBox if available (dedicated secure chip)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(true)
            }
        }.build()

        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun derivePassphrase(key: SecretKey): String {
        // Encrypt a known value with the Keystore key
        // The result is deterministic for the same key
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, FIXED_IV))
        val encrypted = cipher.doFinal(DERIVATION_INPUT)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
}
```

---

## 3. App Authentication

### 3.1 Biometric Lock

```kotlin
// androidApp/security/BiometricManager.kt
class AppBiometricManager(private val activity: FragmentActivity) {

    fun authenticate(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val biometricManager = BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // Device doesn't support biometric — allow entry
            // (data is still encrypted at DB level)
            onSuccess()
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BlackBox")
            .setSubtitle("Authenticate to access your data")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                    or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errString.toString())
                }
                override fun onAuthenticationFailed() {
                    // Don't call onFailure — user can retry
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}
```

### 3.2 Auto-Lock

In `MainActivity`, track lifecycle to auto-lock after background timeout:

```kotlin
class MainActivity : ComponentActivity() {
    private var backgroundTimestamp: Long = 0
    private var isAuthenticated = false
    private val lockTimeoutMs = 60_000L // 1 minute

    override fun onPause() {
        super.onPause()
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        val elapsed = System.currentTimeMillis() - backgroundTimestamp
        if (elapsed > lockTimeoutMs || !isAuthenticated) {
            showAuthScreen()
        }
    }
}
```

---

## 4. Hash Chain Integrity

### 4.1 How It Works

Each `DailySummary` contains:
- `day_hash`: SHA-256 hash of all raw records for that day
- `previous_day_hash`: The `day_hash` from the previous day

This creates an unbreakable chain. If any day's data is tampered with, the chain breaks.

```
Day 1: day_hash = SHA256(all_day1_records)
        previous_day_hash = null (first day)

Day 2: day_hash = SHA256(all_day2_records)
        previous_day_hash = Day1.day_hash

Day 3: day_hash = SHA256(all_day3_records)
        previous_day_hash = Day2.day_hash
```

### 4.2 Implementation

```kotlin
class HashChainManager(
    private val recordRepository: RecordRepository,
    private val summaryRepository: SummaryRepository,
) {
    /**
     * Compute the hash for a day's records and link to previous day.
     * Called by DailySummaryWorker at end of each day.
     */
    suspend fun computeDayHash(date: LocalDate): DayHash {
        val dayStart = date.atStartOfDay().toEpochMs()
        val dayEnd = date.plusDays(1).atStartOfDay().toEpochMs() - 1

        // Get all raw records for the day, sorted by timestamp
        val records = recordRepository.getAllRecordsInRange(dayStart, dayEnd)

        // Compute hash of all records
        val digest = MessageDigest.getInstance("SHA-256")
        records.forEach { record ->
            digest.update(record.timestamp.toString().toByteArray())
            digest.update(record.collectorType.toByteArray())
            digest.update(record.dataJson.toByteArray())
        }
        val dayHash = digest.digest().toHexString()

        // Get previous day's hash
        val previousDate = date.minusDays(1)
        val previousSummary = summaryRepository.getSummaryForDate(previousDate)
        val previousDayHash = previousSummary?.dayHash

        return DayHash(
            date = date,
            hash = dayHash,
            previousHash = previousDayHash,
            recordCount = records.size,
        )
    }

    /**
     * Verify the integrity of the hash chain for a date range.
     * Returns verification result with any detected breaks.
     */
    suspend fun verifyChain(startDate: LocalDate, endDate: LocalDate): ChainVerification {
        val summaries = summaryRepository.getSummariesInRange(startDate, endDate)
        val breaks = mutableListOf<ChainBreak>()

        for (i in 1 until summaries.size) {
            val current = summaries[i]
            val previous = summaries[i - 1]

            if (current.previousDayHash != previous.dayHash) {
                breaks.add(ChainBreak(
                    date = current.date,
                    expected = previous.dayHash,
                    found = current.previousDayHash,
                ))
            }
        }

        return ChainVerification(
            isValid = breaks.isEmpty(),
            breaks = breaks,
            daysVerified = summaries.size,
        )
    }
}
```

---

## 5. Data Export Encryption

### 5.1 Export Format

```json
{
  "version": 1,
  "exported_at": "2026-02-14T10:30:00Z",
  "date_range": {"start": "2025-02-14", "end": "2026-02-14"},
  "encryption": {
    "algorithm": "AES-256-GCM",
    "kdf": "PBKDF2-HMAC-SHA256",
    "iterations": 100000,
    "salt": "<base64-encoded-salt>",
    "iv": "<base64-encoded-iv>"
  },
  "data": "<base64-encoded-encrypted-payload>",
  "integrity_hash": "<SHA-256 of decrypted data>"
}
```

### 5.2 Export Implementation

```kotlin
class DataExporter(
    private val recordRepository: RecordRepository,
    private val summaryRepository: SummaryRepository,
) {
    suspend fun export(dateRange: ClosedRange<LocalDate>, password: String): ByteArray {
        // 1. Collect all data
        val data = collectExportData(dateRange)
        val jsonBytes = data.toJson().toByteArray()

        // 2. Generate salt and derive key
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        // 3. Encrypt with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(jsonBytes)

        // 4. Compute integrity hash of plaintext
        val integrityHash = MessageDigest.getInstance("SHA-256")
            .digest(jsonBytes).toHexString()

        // 5. Package everything
        return buildExportPackage(encrypted, salt, iv, integrityHash)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
```

---

## 6. Privacy Protection Checklist

### 6.1 What BlackBox NEVER Does
- Never records audio content (only volume levels)
- Never captures screen content or screenshots
- Never reads message content (SMS, WhatsApp, etc.)
- Never reads notification content
- Never accesses contacts or call logs
- Never sends data to any server
- Never requires an account or login
- Never shares data with third parties
- Never stores encryption keys in shared preferences
- Never logs personal data in production builds

### 6.2 Manifest Security Settings

```xml
<application
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules">

    <!-- All components are not exported -->
    <activity android:exported="true" ... /> <!-- Only MainActivity -->
    <service android:exported="false" ... />
    <receiver android:exported="false" ... />

    <!-- No content providers — data is never shared -->
</application>
```

```xml
<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" />
    </device-transfer>
</data-extraction-rules>
```

### 6.3 ProGuard / R8 Rules

```proguard
# Keep SQLCipher classes
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Obfuscate everything else for security
-repackageclasses 'bb'
-allowaccessmodification

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```
