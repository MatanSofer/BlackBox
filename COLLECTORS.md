# BlackBox — Collectors Implementation Guide

---

## 1. Collector Architecture

### 1.1 Base Interface

All collectors implement this shared interface:

```kotlin
// shared/domain/model/settings/CollectorType.kt
enum class CollectorType {
    LOCATION,
    ACTIVITY,
    WIFI,
    APP_USAGE,
    SCREEN_STATE,
    AUDIO_LEVEL,
    BATTERY,
    CONNECTIVITY,
    BAROMETER,
    LIGHT,
}

// shared/platform/Collectors.kt
interface DataCollector {
    val collectorType: CollectorType
    val isEnabled: Boolean
    val collectionInterval: Duration

    /** Start collecting data. Called by the orchestrator. */
    suspend fun start()

    /** Stop collecting. Release all resources. */
    suspend fun stop()

    /** Perform a single collection cycle. Returns collected records. */
    suspend fun collect(): List<CollectedRecord>

    /** List of Android permissions this collector requires. */
    fun requiredPermissions(): List<String>

    /** Whether all required permissions are currently granted. */
    fun hasRequiredPermissions(): Boolean
}
```

### 1.2 Base Collector (Android)

```kotlin
// androidApp/collector/base/BaseCollector.kt
abstract class BaseCollector(
    protected val context: Context,
    protected val saveRecordUseCase: SaveRecordUseCase,
    protected val logger: BlackBoxLogger,
) : DataCollector {

    private var _isEnabled = false
    override val isEnabled: Boolean get() = _isEnabled

    override suspend fun start() {
        logger.i(TAG, "${collectorType.name} collector starting")
        _isEnabled = true
        onStart()
    }

    override suspend fun stop() {
        logger.i(TAG, "${collectorType.name} collector stopping")
        _isEnabled = false
        onStop()
    }

    /** Template method — subclasses implement their start logic. */
    protected abstract suspend fun onStart()

    /** Template method — subclasses implement their cleanup logic. */
    protected abstract suspend fun onStop()

    /** Save a record through the use case layer. */
    protected suspend fun saveRecord(record: CollectedRecord) {
        saveRecordUseCase(record).onFailure { error ->
            logger.e(TAG, "Failed to save ${collectorType.name} record: ${error.message}", error)
        }
    }

    companion object {
        private const val TAG = "BaseCollector"
    }
}
```

### 1.3 Collector Orchestrator

```kotlin
// androidApp/collector/base/CollectorOrchestrator.kt
/**
 * Manages all collectors — starts, stops, and adjusts collection
 * based on battery level and user settings.
 */
class CollectorOrchestrator(
    private val collectors: List<DataCollector>,
    private val settingsRepository: SettingsRepository,
    private val batteryMonitor: BatteryMonitor,
    private val logger: BlackBoxLogger,
) {
    private var currentProfile = CollectionProfile.NORMAL

    /**
     * Start all enabled collectors with appropriate intervals.
     * Called by BlackBoxService.onCreate().
     */
    suspend fun startAll() {
        val settings = settingsRepository.getAllSettings()
        collectors.forEach { collector ->
            val setting = settings.find { it.collectorType == collector.collectorType }
            if (setting?.isEnabled == true && collector.hasRequiredPermissions()) {
                collector.start()
            }
        }
        monitorBatteryProfile()
    }

    /**
     * Stop all collectors.
     * Called by BlackBoxService.onDestroy().
     */
    suspend fun stopAll() {
        collectors.forEach { it.stop() }
    }

    /**
     * Update collection profile based on battery level.
     * Dynamically adjusts collection intervals.
     */
    private fun monitorBatteryProfile() {
        batteryMonitor.batteryLevel.collect { level ->
            val newProfile = when {
                batteryMonitor.isCharging -> CollectionProfile.MAXIMUM
                level > 50 -> CollectionProfile.NORMAL
                level > 20 -> CollectionProfile.REDUCED
                level > 10 -> CollectionProfile.MINIMAL
                else -> CollectionProfile.CRITICAL
            }
            if (newProfile != currentProfile) {
                logger.i(TAG, "Battery profile changed: $currentProfile → $newProfile")
                currentProfile = newProfile
                applyProfile(newProfile)
            }
        }
    }
}
```

---

## 2. Collector Implementations

### 2.1 Location Collector

**Priority:** HIGH
**Android APIs:** `FusedLocationProviderClient`, `LocationRequest`
**Permissions:** `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`

```kotlin
// androidApp/collector/LocationCollector.kt
class LocationCollector(
    context: Context,
    saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
    private val fusedLocationClient: FusedLocationProviderClient,
) : BaseCollector(context, saveRecordUseCase, logger) {

    override val collectorType = CollectorType.LOCATION
    override val collectionInterval = 5.minutes

    private var locationCallback: LocationCallback? = null

    override suspend fun onStart() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            collectionInterval.inWholeMilliseconds,
        ).apply {
            setMinUpdateDistanceMeters(10f) // Don't report if moved < 10m
            setMaxUpdateDelayMillis(collectionInterval.inWholeMilliseconds * 2)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // Launch coroutine to save
                    CoroutineScope(Dispatchers.IO).launch {
                        processLocation(location)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper(),
        )
    }

    override suspend fun onStop() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    override suspend fun collect(): List<CollectedRecord> {
        // Location collector is event-driven via callback
        // This method is not used directly
        return emptyList()
    }

    private suspend fun processLocation(location: Location) {
        val record = CollectedRecord(
            collectorType = CollectorType.LOCATION,
            timestamp = location.time,
            dataJson = buildLocationJson(location),
            accuracyScore = calculateAccuracyScore(location),
        )
        saveRecord(record)
    }

    private fun calculateAccuracyScore(location: Location): Float {
        return when {
            location.accuracy <= 10 -> 1.0f
            location.accuracy <= 25 -> 0.9f
            location.accuracy <= 50 -> 0.7f
            location.accuracy <= 100 -> 0.5f
            else -> 0.3f
        }
    }

    override fun requiredPermissions() = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    )
}
```

**Battery profiles:**
| Profile | Interval | Accuracy Priority | Min Displacement |
|---------|----------|-------------------|------------------|
| MAXIMUM | 2 min | HIGH | 5m |
| NORMAL | 5 min | BALANCED | 10m |
| REDUCED | 15 min | LOW | 50m |
| MINIMAL | Significant changes only | LOW | 200m |
| CRITICAL | OFF | — | — |

### 2.2 Activity / Motion Collector

**Priority:** HIGH
**Android APIs:** `ActivityRecognitionClient`, `SensorManager` (TYPE_STEP_COUNTER)
**Permissions:** `ACTIVITY_RECOGNITION`

```kotlin
class ActivityCollector(
    context: Context,
    saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
    private val activityClient: ActivityRecognitionClient,
    private val sensorManager: SensorManager,
) : BaseCollector(context, saveRecordUseCase, logger) {

    override val collectorType = CollectorType.ACTIVITY
    override val collectionInterval = Duration.ZERO // event-driven

    private var transitionPendingIntent: PendingIntent? = null
    private var stepSensor: Sensor? = null
    private var stepListener: SensorEventListener? = null

    override suspend fun onStart() {
        // 1. Register for activity transitions
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_BICYCLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            // Add EXIT transitions for all types too
        )

        val request = ActivityTransitionRequest(transitions)
        // Register via PendingIntent → BroadcastReceiver

        // 2. Register step counter
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        // Register listener with SENSOR_DELAY_NORMAL (batched)
    }

    override fun requiredPermissions() = listOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
    )
}
```

**Important:** Activity transitions are extremely battery-efficient because Android batches them. This is the most efficient way to detect activity changes.

### 2.3 WiFi Environment Collector

**Priority:** MEDIUM
**Android APIs:** `WifiManager`, `ConnectivityManager`
**Permissions:** `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION` (required for WiFi scan on Android 10+)

```kotlin
class WifiCollector(
    context: Context,
    saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
    private val wifiManager: WifiManager,
) : BaseCollector(context, saveRecordUseCase, logger) {

    override val collectorType = CollectorType.WIFI
    override val collectionInterval = 15.minutes

    override suspend fun collect(): List<CollectedRecord> {
        val connectionInfo = wifiManager.connectionInfo
        val scanResults = wifiManager.scanResults

        val data = WifiData(
            connectedSsid = connectionInfo?.ssid?.removeSurrounding("\""),
            connectedBssid = connectionInfo?.bssid,
            signalStrength = connectionInfo?.rssi ?: 0,
            frequencyMhz = connectionInfo?.frequency ?: 0,
            nearbyNetworks = scanResults.take(10).map { result ->
                NearbyNetwork(
                    ssid = result.SSID,
                    bssid = result.BSSID,
                    rssi = result.level,
                )
            },
            networkCount = scanResults.size,
        )

        return listOf(
            CollectedRecord(
                collectorType = CollectorType.WIFI,
                timestamp = System.currentTimeMillis(),
                dataJson = data.toJson(),
                accuracyScore = if (data.connectedSsid != null) 1.0f else 0.5f,
            )
        )
    }
}
```

**Note on Android 10+ restrictions:** WiFi scanning is throttled to 4 scans per 2 minutes for foreground apps. Our 15-min interval is well within limits.

### 2.4 App Usage Collector

**Priority:** HIGH
**Android APIs:** `UsageStatsManager`
**Permissions:** `PACKAGE_USAGE_STATS` (requires Settings intent)

```kotlin
class AppUsageCollector(
    context: Context,
    saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
    private val usageStatsManager: UsageStatsManager,
    private val appInfoCache: AppInfoCache,
) : BaseCollector(context, saveRecordUseCase, logger) {

    override val collectorType = CollectorType.APP_USAGE
    override val collectionInterval = 5.minutes

    private var lastQueryTime: Long = System.currentTimeMillis()

    override suspend fun collect(): List<CollectedRecord> {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastQueryTime, now)
        lastQueryTime = now

        val records = mutableListOf<CollectedRecord>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val appInfo = appInfoCache.getOrFetch(event.packageName)
                records.add(
                    CollectedRecord(
                        collectorType = CollectorType.APP_USAGE,
                        timestamp = event.timeStamp,
                        dataJson = AppUsageData(
                            foregroundApp = event.packageName,
                            displayName = appInfo.displayName,
                            category = appInfo.category,
                            sessionStart = event.timeStamp,
                            isSystemApp = appInfo.isSystemApp,
                        ).toJson(),
                        accuracyScore = 1.0f,
                    )
                )
            }
        }
        return records
    }

    override fun requiredPermissions() = listOf(
        // Not a standard permission — requires special Settings intent
        "android.permission.PACKAGE_USAGE_STATS",
    )

    override fun hasRequiredPermissions(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
```

**Permission UX:** Since `PACKAGE_USAGE_STATS` requires navigating to system Settings, provide a clear UI with a button that opens the correct Settings page:
```kotlin
val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
context.startActivity(intent)
```

### 2.5 Screen State Collector

**Priority:** HIGH
**Android APIs:** `BroadcastReceiver` for `ACTION_SCREEN_ON`, `ACTION_SCREEN_OFF`, `ACTION_USER_PRESENT`
**Permissions:** None

```kotlin
class ScreenStateCollector(
    context: Context,
    saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
) : BaseCollector(context, saveRecordUseCase, logger) {

    override val collectorType = CollectorType.SCREEN_STATE
    override val collectionInterval = Duration.ZERO // event-driven

    private var receiver: BroadcastReceiver? = null

    override suspend fun onStart() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> "ON"
                    Intent.ACTION_SCREEN_OFF -> "OFF"
                    Intent.ACTION_USER_PRESENT -> "UNLOCKED"
                    else -> return
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val brightness = Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        128,
                    )

                    saveRecord(
                        CollectedRecord(
                            collectorType = CollectorType.SCREEN_STATE,
                            timestamp = System.currentTimeMillis(),
                            dataJson = ScreenStateData(
                                state = state,
                                brightness = brightness,
                                brightnessMode = getBrightnessMode(),
                                orientation = getOrientation(),
                            ).toJson(),
                            accuracyScore = 1.0f,
                        )
                    )
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(receiver, filter)
    }

    override suspend fun onStop() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }

    override fun requiredPermissions() = emptyList<String>() // No permissions needed
}
```

**This collector costs ZERO battery** — it's entirely event-driven using system broadcasts.

### 2.6 Audio Level Collector

**Priority:** MEDIUM
**Android APIs:** `MediaRecorder` or `AudioRecord`
**Permissions:** `RECORD_AUDIO`
**Default:** DISABLED

```kotlin
class AudioLevelCollector(
    context: Context,
    saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
) : BaseCollector(context, saveRecordUseCase, logger) {

    override val collectorType = CollectorType.AUDIO_LEVEL
    override val collectionInterval = 15.minutes

    override suspend fun collect(): List<CollectedRecord> {
        // Record 3 seconds of audio, compute metrics, discard raw audio
        val sampleDuration = 3_000L // 3 seconds
        val sampleRate = 8000 // Low quality — we only need volume levels
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        val buffer = ShortArray(bufferSize)
        audioRecord.startRecording()

        // Collect samples for 3 seconds
        val allSamples = mutableListOf<Short>()
        val endTime = System.currentTimeMillis() + sampleDuration
        while (System.currentTimeMillis() < endTime) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read > 0) {
                allSamples.addAll(buffer.take(read).toList())
            }
        }

        // IMMEDIATELY stop and release — no audio stored
        audioRecord.stop()
        audioRecord.release()

        // Compute RMS amplitude → dB
        val rms = sqrt(allSamples.map { it.toDouble() * it.toDouble() }.average())
        val db = if (rms > 0) 20 * log10(rms / Short.MAX_VALUE) + 90 else 0.0
        // +90 is a rough calibration offset to get approximate dB SPL

        val classification = classifyNoiseLevel(db)

        return listOf(
            CollectedRecord(
                collectorType = CollectorType.AUDIO_LEVEL,
                timestamp = System.currentTimeMillis(),
                dataJson = AudioLevelData(
                    dbLevel = db,
                    classification = classification,
                    sampleDurationMs = sampleDuration,
                ).toJson(),
                accuracyScore = 0.8f, // Audio level is approximate
            )
        )
    }

    private fun classifyNoiseLevel(db: Double): String = when {
        db < 30 -> "SILENT"
        db < 45 -> "QUIET"
        db < 65 -> "CONVERSATION"
        db < 80 -> "LOUD"
        else -> "VERY_LOUD"
    }

    override fun requiredPermissions() = listOf(
        Manifest.permission.RECORD_AUDIO,
    )
}
```

**Critical privacy note:** Raw audio buffer is NEVER written to disk. Only computed metrics (dB level, classification) are saved.

### 2.7 Battery Collector

**Priority:** LOW
**Android APIs:** `BroadcastReceiver` for `ACTION_BATTERY_CHANGED`
**Permissions:** None

Event-driven via `BroadcastReceiver`. Captures level, charging state, temperature. Zero battery cost since it uses system broadcasts.

### 2.8 Connectivity Collector

**Priority:** LOW
**Android APIs:** `ConnectivityManager`, `BluetoothManager`, `BroadcastReceiver`
**Permissions:** `BLUETOOTH_CONNECT` (Android 12+), `ACCESS_NETWORK_STATE`

Event-driven for connectivity changes. Periodic scan (15 min) for connected Bluetooth devices.

### 2.9 Barometer Collector

**Priority:** LOW
**Android APIs:** `SensorManager` (TYPE_PRESSURE)
**Permissions:** None
**Default:** DISABLED

Samples atmospheric pressure every 10 minutes. Uses a 1-second reading to avoid continuous sensor usage. Computes relative altitude changes for floor detection.

### 2.10 Light Collector

**Priority:** LOW
**Android APIs:** `SensorManager` (TYPE_LIGHT)
**Permissions:** None
**Default:** DISABLED

Samples ambient light every 10 minutes. Single 1-second reading. Classifies into DARK/DIM/INDOOR/OUTDOOR_SHADE/DIRECT_SUNLIGHT.

---

## 3. Foreground Service

```kotlin
// androidApp/service/BlackBoxService.kt
class BlackBoxService : Service() {

    private lateinit var orchestrator: CollectorOrchestrator

    override fun onCreate() {
        super.onCreate()
        orchestrator = get() // Koin inject

        // Create notification channel
        createNotificationChannel()

        // Start as foreground service
        val notification = buildNotification("Recording your day...")
        startForeground(NOTIFICATION_ID, notification)

        // Start all collectors
        CoroutineScope(Dispatchers.Default).launch {
            orchestrator.startAll()
        }
    }

    override fun onDestroy() {
        CoroutineScope(Dispatchers.Default).launch {
            orchestrator.stopAll()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlackBox")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(createOpenAppIntent())
            .addAction(R.drawable.ic_pause, "Pause", createPauseIntent())
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "blackbox_recording"

        fun start(context: Context) {
            val intent = Intent(context, BlackBoxService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BlackBoxService::class.java)
            context.stopService(intent)
        }
    }
}
```

### 3.1 Service Manifest Declaration

```xml
<service
    android:name=".service.BlackBoxService"
    android:exported="false"
    android:foregroundServiceType="location|connectedDevice" />

<receiver
    android:name=".service.BootReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

### 3.2 Boot Receiver

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            BlackBoxService.start(context)
        }
    }
}
```

---

## 4. Collection Profiles Summary

| Profile | Trigger | Location | Activity | WiFi | App Usage | Polling Collectors | Event Collectors |
|---------|---------|----------|----------|------|-----------|-------------------|-----------------|
| MAXIMUM | Charging | 2 min | Active | 5 min | 2 min | 5 min | Active |
| NORMAL | Battery > 50% | 5 min | Transitions | 15 min | 5 min | 10 min | Active |
| REDUCED | Battery 20-50% | 15 min | Transitions | 30 min | 10 min | 30 min | Active |
| MINIMAL | Battery 10-20% | Significant only | Transitions | OFF | 15 min | OFF | Active |
| CRITICAL | Battery < 10% | OFF | OFF | OFF | OFF | OFF | Screen events only |

**Key insight:** Event-driven collectors (screen state, activity transitions, battery changes) cost essentially zero battery and remain active in all profiles except CRITICAL.
