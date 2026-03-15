# BlackBox — Algorithms

Deep-dive documentation for every non-trivial algorithm in the codebase.

---

## Table of Contents

1. [Location Stay Grouping (Timeline & Map)](#1-location-stay-grouping)
2. [Sleep Detection](#2-sleep-detection)
3. [Step Counting](#3-step-counting)
4. [Screen Time Calculation](#4-screen-time-calculation)
5. [Place Detection & Clustering](#5-place-detection--clustering)
6. [WiFi-Based Place Matching](#6-wifi-based-place-matching)
7. [Natural Language Query Pipeline](#7-natural-language-query-pipeline)
8. [Time Expression Parsing](#8-time-expression-parsing)
9. [App Name Cleaning](#9-app-name-cleaning)
10. [Daily Summary Generation](#10-daily-summary-generation)
11. [Haversine Distance](#11-haversine-distance)
12. [Battery Profile Selection](#12-battery-profile-selection)

---

## 1. Location Stay Grouping

**Where**: `GetTimelineUseCase.buildLocationEntries()`, `GetDayLocationSummaryUseCase`

**Purpose**: Convert a raw stream of GPS fixes (one every 5 minutes) into meaningful "stay" segments ("At Home for 4h 32m").

### Algorithm

```
Input: List<LocationEntry> ordered by timestamp

1. Start first group at locations[0]
2. For each subsequent point:
   a. Compute Haversine distance from group start to current point
   b. If distance < STAY_RADIUS (100m):
        → Extend current group (update groupEnd)
   c. Else:
        → Flush current group as a StayEntry
        → Start new group at current point
3. Flush final group
```

### Parameters
| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `STAY_RADIUS_METERS` | 100m | Wide enough to absorb GPS jitter (~20m typical error). Narrow enough to distinguish nearby locations (e.g., your apartment vs. the shop next door). |

### Why not use timestamps?
A purely time-based approach ("30 minutes with no movement = stay") would fail for:
- Users who are stationary for a long time at an unknown location
- GPS accuracy jumps that temporarily place the user 200m away

The distance approach correctly groups points that are geographically co-located regardless of time gaps.

### Stay duration
`durationMs = groupEnd.timestamp - groupStart.timestamp`

If `durationMin > 0`, a subtitle "Xm" is shown. Short transient points (< 1 minute at a location) show no subtitle.

---

## 2. Sleep Detection

**Where**: `DetectSleepSessionsUseCase`

**Purpose**: Determine when the user slept and woke up using only screen ON/UNLOCK events — without relying on screen OFF events which are unreliable. Secondary signals (airplane mode, ambient light) refine the result.

### Detection window
```
Previous day 20:00 ─────────────────────────────── Morning 12:00 (noon)
  (8pm is early enough for night owls)    (noon allows sleeping in)
```

### Algorithm

The pipeline runs in four stages for each night:

```
Input: Screen records in window [20:00 prev day → 12:00 today]
       nowMs (current time, to handle "still sleeping" case)

── Stage A: findSleepSession() ─────────────────────────────────────────

Step 1: extractActiveTimes() — extract all ON/UNLOCK timestamps
        If window is still open (before noon), append nowMs → activeTimes[]

Step 2: Group consecutive activeTimes into "phone sessions"
        → Sessions where gap between events ≤ BRIEF_WAKE_MS (10 min)
           belong together (reading at 2am with a few taps)
        Result: sessions[] = List of (start, end) pairs

Step 3: Build "dark periods" between consecutive sessions
        darkPeriods[i] = (sessions[i].end, sessions[i+1].start)

Step 4: Merge dark periods across brief phone sessions
        If duration of sessions[i] < BRIEF_WAKE_MS (10 min)
        AND sessions[i].start < 06:00 (morningMergeCutoffMs):
           → The user barely woke (alarm check, time check)
           → Merge surrounding dark periods into one segment
        If sessions[i].start ≥ 06:00 → NEVER merge regardless of duration
           → A 5-minute session at 7:27am is real morning activity, not a glance

Step 5: Find the best qualifying dark period:
        For each merged dark period (start, end):
          if duration >= MIN_SLEEP_MS (3h)
          AND end >= morningCutoffMs (04:00):
            effectiveStart = max(start, end - MAX_SLEEP_MS)
            candidate = SleepSession(effectiveStart, end)
        → Return candidate with longest duration  [baseline]

── Stage B: refineWithAirplaneMode() ───────────────────────────────────

If airplane mode was enabled within AIRPLANE_LOOKBACK_MS (90 min) before
baseline.sleepStart AND no screen-ON event occurred in the gap:
  → Extend sleepStart backward to the airplane-on timestamp
  → Recompute duration and quality
Guard: extension is rejected if newDuration > MAX_SLEEP_MS.
Note: networkType == NONE alone does NOT trigger this — only airplaneMode == true.

── Stage C: refineWithLightData() ──────────────────────────────────────

Count LIGHT sensor records during the sleep window.
If ≥ 3 records:
  dark/dim fraction ≥ 0.70 → lightValidated = true
  dark/dim fraction <  0.30 → lightValidated = false
  otherwise                 → lightValidated = null
Does NOT change sleep times or quality — validation signal only.
```

### getWeekTrend() — 7-entry list contract

```kotlin
// Always returns exactly `days` entries (default 7), oldest-first.
// Null = no session could be detected for that night.
// Callers must NOT assume a non-null list — use filterNotNull() before averaging.
suspend fun getWeekTrend(todayStr: String, days: Int = 7): List<SleepSession?>
```

### Constants
| Constant | Value | Rationale |
|----------|-------|-----------|
| `MIN_SLEEP_MS` | 3 hours | Minimum gap to qualify as sleep. A 2h 50m phone-off session is probably just dinner, not sleep. |
| `MAX_SLEEP_MS` | 9 hours | Trims from the start. Prevents "put phone down at 9pm, woke at 7am → 10h sleep" (user was watching TV for an hour). |
| `BRIEF_WAKE_MS` | 10 minutes | Phone sessions shorter than this are merged into adjacent dark periods — but only if the session starts before 06:00. |
| `MORNING_CUTOFF_HOUR` | 4am | Wake-up must be after 4am. Filters out "phone down at midnight, briefly on at 1am" that doesn't represent real sleep. |
| `morningMergeCutoffMs` | 06:00 (noon − 6h) | Sessions starting at/after this time are never treated as "brief", even if duration < `BRIEF_WAKE_MS`. Prevents a real 7:27am wake-up from being merged into the sleep session just because it was brief. |
| `AIRPLANE_LOOKBACK_MS` | 90 minutes | How far before detected bedtime to search for an airplane mode event. |
| `LIGHT_MIN_RECORDS` | 3 | Minimum ambient-light samples needed to form a validation verdict. |
| `LIGHT_DARK_FRACTION_HIGH` | 0.70 | Fraction of DARK/DIM readings that sets `lightValidated = true`. |
| `LIGHT_DARK_FRACTION_LOW` | 0.30 | Fraction below which `lightValidated = false`. |

### Why screen-ON only (not OFF)?
- Screen OFF events can be missed if the service restarts
- A synthetic ON record is emitted at service startup (either ON or OFF based on current screen state), so the algorithm anchors correctly even after restarts
- Looking only at "when did the phone become active" is robust against any gaps in the OFF stream

### Quality classification
```kotlin
minutes < 360  → POOR   (< 6h)
minutes < 420  → FAIR   (6–7h)
minutes ≤ 540  → GOOD   (7–9h)
else           → LONG   (> 9h)
```

---

## 3. Step Counting

**Where**: `GetInsightsBriefUseCase.computeTodaySteps()`, `StepCounterProvider`

**Purpose**: Count steps taken today accurately and efficiently.

### Primary: Hardware step counter

The Android `TYPE_STEP_COUNTER` sensor is a hardware pedometer that counts steps cumulatively since the last device reboot. It:
- Never resets intra-day
- Is very accurate (hardware-accelerated)
- Has near-zero battery cost

```
Baseline = counter value at service start (stored in memory)
TodaySteps = currentCounterValue - baseline
```

Edge case: if the device rebooted during the day, the baseline is reset. This is handled by clamping to ≥ 0.

### Fallback: Activity record deltas

If the hardware sensor is unavailable (some devices):
```kotlin
val cumulativeValues = activityRecords
    .mapNotNull { it.activityData.stepCountCumulative }
    .filter { it > 0 }

todaySteps = cumulativeValues.max() - cumulativeValues.min()
```

**Why not sum `stepCountDelta`?** Each `ActivityData.stepCountDelta` is "steps since the activity session started", not "steps since the previous record". Summing deltas overcounts every time a new activity session begins.

### 7-day trend
Derived directly from raw `ACTIVITY` records by `computeWeekStepTrend()` in `GetInsightsBriefUseCase`. Records are grouped by calendar day; each day's count is `max(cumulative) − min(cumulative)`. This always produces exactly 7 entries (zero for days with no records), avoiding the missing-bar bug that occurred when `DailySummaryWorker` skipped a day.

---

## 4. Screen Time Calculation

**Where**: `GetInsightsBriefUseCase.computeScreenMinutes()`

**Purpose**: Sum total screen-on time for the current day.

### Algorithm

```
State machine over sorted screen records:

lastOnMs = null
totalMs = 0

For each record sorted by timestamp:
  state = record.screenStateData.state

  if state == ON or UNLOCKED:
    if lastOnMs == null:
      lastOnMs = record.timestamp     ← screen turned on

  if state == OFF or LOCKED:
    if lastOnMs != null:
      totalMs += record.timestamp - lastOnMs
      lastOnMs = null                 ← screen turned off, add gap

After all records:
  if lastOnMs != null:
    totalMs += nowMs - lastOnMs       ← screen still on, count to now

return (totalMs / 60_000).toInt()
```

### Why the synthetic ON/OFF record matters
On service start, `ScreenStateCollector` emits a synthetic record matching the current screen state. Without this:
- If the service restarts while the screen is ON, there's no ON record before the next OFF record, so that session's time is lost.

### Per-app breakdown
`aggregateTopApps(appRecords, screenRecords, windowEndMs, nowMs, limit)` runs three phases:
1. **Base aggregation** — group APP_USAGE records by app name, sum `sessionDurationMs`.
2. **Orphaned tail** — the last app's time is extended to the first SCREEN_OFF after its last session ends, capped at 30 min. Captures usage that continued after the last record was written.
3. **Gap fill** — for completed screen-ON intervals (ended > 1 h ago) with no app record inside, the most recent preceding app (within 15 min lookback) is attributed the interval duration, capped at 60 min. Recovers long single-app sessions that UsageStatsManager split across reporting boundaries.

### 7-day screen time trend
Derived from raw `SCREEN_STATE` records by `computeWeekScreenTrend()` — same ON→OFF pairing as per-day calculation, grouped by calendar day. Always produces exactly 7 entries.

---

## 5. Place Detection & Clustering

**Where**: `DetectKnownPlacesUseCase`

**Purpose**: Automatically discover places the user visits frequently, without user input.

### Algorithm (simplified)

```
1. Fetch all location records from the last N days
2. Apply DBSCAN-style clustering:
   - Cluster points within 100m of each other
   - Minimum cluster size: 3 points (visited at least 3 times)
3. For each cluster:
   a. Compute centroid (mean lat/lng)
   b. Check if it matches an existing KnownPlace (within radius)
   c. If match: increment visit count, update last_visit
   d. If no match: create new KnownPlace (isAutoDetected = true)
4. Auto-categorise by time patterns:
   - Cluster visited > 8h/day → likely HOME
   - Cluster visited 8am–6pm weekdays → likely WORK
   - Otherwise → OTHER
```

### Key files
- `shared/.../domain/usecase/place/DetectKnownPlacesUseCase.kt`

---

## 6. WiFi-Based Place Matching

**Where**: `PlaceRepositoryImpl.findPlaceByWifiBssid()`, `GetTimelineUseCase.buildStayEntry()`

**Purpose**: Identify a known place by the connected WiFi router's MAC address (BSSID) when GPS is inaccurate.

### Algorithm

```
In buildStayEntry(start, end, wifiRecords):

1. Try GPS match:
   place = placeRepository.findNearestPlace(lat, lng)

2. If GPS match fails (place == null):
   a. Find WiFi records within the stay's time window
   b. Get the connectedBssid from the first matching record
   c. place = placeRepository.findPlaceByWifiBssid(bssid)

3. Use place.name if found, else show "(lat, lng)" coordinates
```

`findPlaceByWifiBssid(bssid)`:
```kotlin
getAllPlaces().firstOrNull { place -> place.wifiFingerprint.contains(bssid) }
```

### Why BSSID and not SSID?
- SSID is the human-readable network name ("HomeWifi"). Many networks share SSIDs.
- BSSID is the router's MAC address (e.g., "aa:bb:cc:dd:ee:ff"). It's globally unique.
- Using BSSID avoids false matches between different buildings with the same network name.

---

## 7. Natural Language Query Pipeline

**Where**: `QueryEngine`, `ProcessQueryUseCase`

**Purpose**: Convert a free-text question into a structured DB query and a human-readable answer.

### Pipeline stages

```
"Where was I yesterday afternoon?"
       ↓
[1. Normalise]  →  "where was i yesterday afternoon"
       ↓
[2. Detect Language]  →  Language.ENGLISH
       ↓
[3. TimeExpressionParser]  →  TimeRange(yesterday 12:00, yesterday 18:00)
       ↓
[4. IntentClassifier]  →  QueryIntent.LOCATION_QUERY
       ↓
[5. EntityExtractor]  →  entities = {} (no specific place mentioned)
       ↓
[6. QueryBuilder]  →  DB query: LocationRecord WHERE timestamp IN range
       ↓
[7. Execute]  →  List<LocationRecord> with addresses
       ↓
[8. ResponseGenerator]  →  "Yesterday afternoon you were at..."
       ↓
QueryResult { parsedQuery, data, responseText, suggestedFollowUps }
```

### Intent types
| Intent | Trigger keywords | What it fetches |
|--------|-----------------|----------------|
| LOCATION_QUERY | where, location, place, go | LocationRecord |
| ACTIVITY_QUERY | what, doing, activity, walk, run | ActivityRecord |
| TEMPORAL_QUERY | when, time, at what | Combined |
| PATTERN_QUERY | usually, often, always, habit | Aggregated by day-of-week |
| PROOF_QUERY | prove, confirm, alibi, was i | Multi-source evidence |
| SUMMARY_QUERY | summary, tell me, how was | DailySummary |
| DURATION_QUERY | how long, duration, spent | LocationRecord stays |
| COUNT_QUERY | how many, count, times | Aggregated count |

---

## 8. Time Expression Parsing

**Where**: `TimeExpressionParser`

**Purpose**: Convert time phrases to `TimeRange(startMs, endMs)`.

### English patterns supported

| Input | Output |
|-------|--------|
| "right now" / "currently" | now − 15min → now |
| "today" | today 00:00 → today 23:59 |
| "today morning" | today 05:00 → today 12:00 |
| "today afternoon" / "today noon" | today 12:00 → today 18:00 |
| "today evening" / "tonight" | today 18:00 → today 23:59 |
| "yesterday" | yesterday 00:00 → yesterday 23:59 |
| "yesterday morning/afternoon/evening" | yesterday sub-range |
| "last night" | yesterday 20:00 → today 00:00 |
| "this morning" | today 05:00 → today 12:00 |
| "3 days ago" | 3 days ago 00:00 → 3 days ago 23:59 |
| "two hours ago" | now − 2h → now |
| "in the last 3 hours" | now − 3h → now |
| "past two weeks" | now − 14d → now |
| "last week" | 7 days ago → yesterday |
| "this week" | start of week → today |
| "last month" | 30 days ago → yesterday |
| "Monday" / "last Tuesday" | most recent occurrence of that day |

### Word number support
Recognises: "a/an", "one" through "ten". So "two days ago", "a week ago" all parse correctly.

### Timezone handling
**All day boundaries are computed in the device's local timezone** using `kotlinx.datetime`. This prevents the UTC-midnight vs local-midnight bug that affects users in non-UTC timezones (e.g., UTC+2 Israel: "yesterday" would be off by 2 hours if computed with UTC modulo).

```kotlin
private fun startOfDay(epochMs: Long): Long {
    val tz = TimeZone.currentSystemDefault()
    val localDate = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date
    return localDate.atStartOfDayIn(tz).toEpochMilliseconds()
}
```

### Hebrew patterns
All English patterns have Hebrew equivalents:
- "היום" (today), "אתמול" (yesterday), "אמש" (last night)
- "הבוקר" (this morning), "הערב" (this evening)
- "לפני 3 ימים" (3 days ago)
- All Hebrew day names: "ראשון" through "שבת"

---

## 9. App Name Cleaning

**Where**: `GetInsightsBriefUseCase.cleanAppDisplayName()`

**Purpose**: Convert package names like `org.telegram.messenger.beta` to human-readable names like "Telegram Beta".

### Resolution order

```
1. Exact match in KNOWN_PACKAGES map
   "com.whatsapp" → "WhatsApp"

2. Check if name looks like a package:
   - Contains '.'
   - No spaces
   - All lower-case start
   If NOT a package (has spaces or mixed case) → return as-is

3. Prefix match:
   "org.telegram.messenger.beta" starts with "org.telegram.messenger"
   → "Telegram Beta"

4. Walk segments from right, skip GENERIC_APP_SEGMENTS:
   "com.example.myapp.debug"
   Segments right-to-left: "debug" (generic), "myapp" (≥3 chars, not generic)
   → capitalize → "Myapp"

5. Last resort: capitalize the longest segment
```

### GENERIC_APP_SEGMENTS
A set of ~30 words: `android`, `app`, `apps`, `mobile`, `lite`, `debug`, `beta`, `client`, `main`, `launcher`, `service`, `services`, `core`, `framework`, `system`, `provider`, `manager`, `helper`, `activity`, `feature`, `module`, `lib`, `library`, `common`, `base`, `internal`, `impl`, `utils`, `util`, `data`, `api`

### SYSTEM_PACKAGE_BLOCKLIST — OEM system-app leak guard

`UsageStatsManager` does not reliably set `isSystemApp = true` for all background system components across Android versions and OEM ROMs. For example:
- `com.android.cellbroadcastreceiver` → surfaces as "Cellbroadcastreceiver"
- `com.samsung.android.app.clockpackage` → surfaces as "Clockpackage"

`SYSTEM_PACKAGE_BLOCKLIST` is a hardcoded set of known background system package names that are **always excluded** from the popular-apps list regardless of the `isSystemApp` flag. The filter runs on `AppUsageData.foregroundApp` (the raw package name) before any display-name resolution:

```kotlin
.filter { !it.isSystemApp && it.displayName.isNotBlank() && !isBlockedPackage(it.foregroundApp) }
```

`isBlockedPackage(packageName)` simply checks `packageName in SYSTEM_PACKAGE_BLOCKLIST`.

**Packages currently blocked:**
- Emergency/carrier receivers: `com.android.cellbroadcastreceiver`, `com.android.cellbroadcastservice`, `com.google.android.cellbroadcastreceiver`
- OEM clock background services: `com.samsung.android.app.clockpackage`, `com.huawei.android.clockpackage`
- Core Android internals: `com.android.phone`, `com.android.systemui`, `com.android.keyguard`, `com.android.permissioncontroller`, `com.android.packageinstaller`, `com.google.android.packageinstaller`
- Google Play background services: `com.google.android.gms`, `com.google.android.gsf`

---

## 10. Daily Summary Generation

**Where**: `GenerateDailySummaryUseCase`, `DailySummaryWorker`

**Purpose**: Maintains a tamper-detection hash chain and triggers nightly place detection. Note: the Insights screen no longer reads from `DailySummary` rows for its charts — it queries raw records directly.

### What gets computed
- Total steps (from activity records)
- Total distance (from location records, sum of consecutive Haversine distances)
- Time at home / work / in transit (minutes)
- Screen on count and total screen time (from screen state records)
- Most used apps JSON array (top 5 by session duration)
- Locations visited JSON array (distinct addresses)
- Activity breakdown JSON (% time in each activity type)
- Average noise level dB (from audio level records)
- First screen on / last screen off timestamps
- Sleep start/end estimate (from DetectSleepSessionsUseCase)
- Day hash (SHA-256 of all record IDs for tamper detection)
- Previous day hash (links hash chain)

### When it runs
`DailySummaryWorker` runs once per day at midnight +/- a few minutes (WorkManager constraint: `RequiresNetwork.NOT_REQUIRED`, `RequiresBattery.NOT_LOW`).

The worker performs two jobs for "yesterday" (the now-completed day):
1. **Hash-chain integrity** — writes a `DailySummary` row with `day_hash` + `previous_day_hash`. The chain enables tamper detection but is not the Insights data source.
2. **Place detection** — runs `DetectKnownPlacesUseCase` to cluster yesterday's GPS data into new or updated `KnownPlace` entries.

### Relationship with Insights screen
`GetInsightsBriefUseCase` derives all weekly trend data (steps, screen time, sleep, contacts) **directly from raw collector records**, not from `DailySummary` rows. This guarantees all charts always have exactly 7 bars even when the worker is skipped (device off, battery critical, etc.). The deprecated `InsightRepository.getStepTrend()` / `getScreenTimeTrend()` methods remain in the interface for backwards compatibility but are no longer called by Insights.

---

## 11. Haversine Distance

**Where**: `GetTimelineUseCase`, `PlaceRepositoryImpl`, `GetDayLocationSummaryUseCase`

**Purpose**: Compute the great-circle distance between two GPS coordinates in meters.

### Formula
```kotlin
fun haversineDistance(lat1, lng1, lat2, lng2): Double {
    val r = 6_371_000.0  // Earth radius in meters
    val dLat = toRadians(lat2 - lat1)
    val dLng = toRadians(lng2 - lng1)
    val a = sin(dLat/2)^2 + cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(dLng/2)^2
    val c = 2 * atan2(sqrt(a), sqrt(1-a))
    return r * c
}
```

### Accuracy
Error < 0.5% for distances up to ~1,000 km. For the distances BlackBox uses (0–500m), error is negligible.

### Why not `Location.distanceBetween()`?
`Location.distanceBetween()` is an Android API. The shared `domain/` layer has no Android imports. The Haversine implementation is in pure Kotlin.

---

## 12. Battery Profile Selection

**Where**: `CollectorOrchestrator`

**Purpose**: Automatically reduce collection frequency when battery is low to extend battery life.

### Profile selection logic
```
Battery level       → Profile
─────────────────────────────────
Charging            → MAXIMUM
> 50%               → NORMAL
20% – 50%           → REDUCED
10% – 20%           → MINIMAL (location only)
< 10%               → CRITICAL (pause everything)
```

### Interval multipliers per profile
| Collector | MAXIMUM | NORMAL | REDUCED | MINIMAL | CRITICAL |
|-----------|---------|--------|---------|---------|---------|
| Location | 2 min | 5 min | 15 min | Significant changes only | OFF |
| Activity | On change | On change | On change | OFF | OFF |
| WiFi | 5 min | 15 min | 30 min | OFF | OFF |
| App Usage | 2 min | 5 min | 10 min | 15 min | OFF |
| Screen State | On change | On change | On change | On change | On change |
| Sensors (audio, barometer, light) | 5 min | 10 min | 30 min | OFF | OFF |

### Stationary detection
When the device hasn't moved > 50m in the last 30 minutes (detected via activity STILL + GPS stability), location collection interval is tripled automatically (even within NORMAL profile) to avoid wasting GPS on a stationary user.
