# BlackBox — Battery Optimization Guide

---

## 1. Battery Budget

**Target: < 3% daily battery consumption** on NORMAL profile.

| Component | Estimated Daily Impact |
|-----------|----------------------|
| Foreground Service (idle) | ~0.3% |
| Location (5-min interval) | ~1.0% |
| Activity Recognition | ~0.1% (event-driven) |
| WiFi Scanning (15 min) | ~0.3% |
| App Usage Polling (5 min) | ~0.2% |
| Screen State Events | ~0.0% (event-driven) |
| Sensor Sampling (10 min) | ~0.2% |
| SQLite Writes | ~0.1% |
| **TOTAL** | **~2.2%** |

---

## 2. Optimization Strategies

### 2.1 Prefer Events Over Polling

| Collector | Strategy | Battery Cost |
|-----------|----------|-------------|
| Screen State | BroadcastReceiver events | ~0% |
| Activity | ActivityTransition API | ~0.1% |
| Battery | BroadcastReceiver events | ~0% |
| Connectivity | NetworkCallback events | ~0% |
| Location | FusedLocation with intervals | ~1% |
| WiFi | Polling (no reliable event) | ~0.3% |
| App Usage | Polling UsageStats | ~0.2% |

**Rule:** If an event-driven API exists, ALWAYS use it instead of polling.

### 2.2 Batch Sensor Readings

Instead of keeping sensors active:
```kotlin
// BAD — sensor always active
sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

// GOOD — sample for 1 second every N minutes
suspend fun sampleSensor(sensor: Sensor, durationMs: Long = 1000): Float {
    val values = mutableListOf<Float>()
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            values.add(event.values[0])
        }
        override fun onAccuracyChanged(s: Sensor, a: Int) {}
    }

    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    delay(durationMs)
    sensorManager.unregisterListener(listener)

    return values.average().toFloat()
}
```

### 2.3 Stationary Detection

When the device hasn't moved for 30+ minutes, reduce collection aggressively:

```kotlin
class StationaryDetector(
    private val activityCollector: ActivityCollector,
) {
    private var stationarySince: Long? = null

    fun onActivityChanged(activity: DetectedActivity) {
        if (activity.type == DetectedActivity.STILL) {
            if (stationarySince == null) {
                stationarySince = System.currentTimeMillis()
            }
        } else {
            stationarySince = null
        }
    }

    val isStationary: Boolean
        get() {
            val since = stationarySince ?: return false
            return System.currentTimeMillis() - since > 30 * 60 * 1000 // 30 min
        }
}
```

When stationary:
- Location: Reduce to 30-min interval (we know where they are)
- WiFi: Reduce to 30-min interval
- Sensors: Reduce to 30-min interval
- Activity: Keep active (to detect when movement resumes)

### 2.4 Night Mode

Between estimated sleep time and wake time:
- Location: OFF (user is at home)
- WiFi: OFF
- App Usage: Reduce to 30-min
- Audio: OFF
- Sensors: OFF
- Keep active: Screen state (to detect if phone is picked up at night)

### 2.5 Coalesce Wakeups

```kotlin
// Use inexact alarms to let Android batch with other apps
val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
alarmManager.setInexactRepeating(
    AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + intervalMs,
    intervalMs,
    pendingIntent,
)

// Or use WorkManager for periodic work
val workRequest = PeriodicWorkRequestBuilder<CollectionWorker>(
    15, TimeUnit.MINUTES,
    5, TimeUnit.MINUTES, // flex interval
).setConstraints(
    Constraints.Builder()
        .setRequiresBatteryNotLow(false) // We handle battery ourselves
        .build()
).build()
```

### 2.6 Database Write Batching

Don't write every record immediately. Batch them:

```kotlin
class RecordBatcher(
    private val repository: RecordRepository,
    private val batchSize: Int = 20,
    private val maxDelayMs: Long = 60_000, // flush every 60 seconds max
) {
    private val buffer = mutableListOf<CollectedRecord>()
    private var lastFlush = System.currentTimeMillis()

    suspend fun add(record: CollectedRecord) {
        buffer.add(record)
        if (buffer.size >= batchSize ||
            System.currentTimeMillis() - lastFlush > maxDelayMs
        ) {
            flush()
        }
    }

    suspend fun flush() {
        if (buffer.isEmpty()) return
        repository.saveRecordsBatch(buffer.toList())
        buffer.clear()
        lastFlush = System.currentTimeMillis()
    }
}
```

---

## 3. Manufacturer-Specific Issues

### 3.1 Known Problem ROMs

Some manufacturers aggressively kill background services:
- **Xiaomi/MIUI:** Must disable "battery optimization" AND add to "autostart"
- **Samsung/OneUI:** Must disable "adaptive battery" for the app
- **Huawei/EMUI:** Must add to "protected apps" and disable "power-intensive prompt"
- **OnePlus/OxygenOS:** Must disable "battery optimization" and "deep optimization"

### 3.2 User Guidance

On first launch, detect the manufacturer and show specific instructions:

```kotlin
fun getManufacturerBatteryInstructions(): BatteryInstructions? {
    return when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi" -> BatteryInstructions(
            title = "Xiaomi Battery Settings",
            steps = listOf(
                "Go to Settings → Apps → Manage Apps → BlackBox",
                "Tap 'Autostart' and enable it",
                "Tap 'Battery saver' and select 'No restrictions'",
            )
        )
        "samsung" -> BatteryInstructions(
            title = "Samsung Battery Settings",
            steps = listOf(
                "Go to Settings → Battery → Background usage limits",
                "Remove BlackBox from 'Sleeping apps' and 'Deep sleeping apps'",
            )
        )
        "huawei", "honor" -> BatteryInstructions(...)
        "oneplus" -> BatteryInstructions(...)
        else -> null // Stock Android — usually fine
    }
}
```

---

## 4. Monitoring Battery Impact

### 4.1 Self-Monitoring

The app tracks its own battery consumption:

```kotlin
class BatteryImpactTracker {
    private var lastBatteryLevel: Int = -1
    private var lastCheckTime: Long = 0

    /**
     * Estimate hourly battery drain attributable to BlackBox.
     * Compare actual drain vs expected drain without BlackBox.
     */
    fun trackDrain(currentLevel: Int) {
        if (lastBatteryLevel == -1) {
            lastBatteryLevel = currentLevel
            lastCheckTime = System.currentTimeMillis()
            return
        }

        val elapsed = System.currentTimeMillis() - lastCheckTime
        val drain = lastBatteryLevel - currentLevel
        val hourlyDrain = (drain.toFloat() / elapsed) * 3_600_000

        // Log for debugging
        // If hourlyDrain > 2.0, something is wrong

        lastBatteryLevel = currentLevel
        lastCheckTime = System.currentTimeMillis()
    }
}
```

### 4.2 Debug Battery Stats

In Settings (debug mode only), show:
- Per-collector wakeup count
- Per-collector average execution time
- Total DB writes per hour
- Estimated daily battery percentage
- Comparison: today's drain vs average
