# BlackBox — Features Guide

Complete reference for every user-facing feature: what it does, how it works, and where it lives in the code.

---

## Table of Contents

1. [Passive Background Collection](#1-passive-background-collection)
2. [Natural Language Search](#2-natural-language-search)
3. [Timeline](#3-timeline)
4. [Map & Route Replay](#4-map--route-replay)
5. [Known Places](#5-known-places)
6. [Insights Dashboard](#6-insights-dashboard)
7. [Sleep Detection](#7-sleep-detection)
8. [Step Counting](#8-step-counting)
9. [Screen Time Tracking](#9-screen-time-tracking)
10. [App Usage Tracking](#10-app-usage-tracking)
11. [Call Log in Timeline](#11-call-log-in-timeline)
12. [WiFi-Based Place Matching](#12-wifi-based-place-matching)
13. [Settings & Collector Toggles](#13-settings--collector-toggles)
14. [Data Security & Encryption](#14-data-security--encryption)
15. [Onboarding](#15-onboarding)

---

## 1. Passive Background Collection

### What the user sees
A persistent notification in the status bar: "BlackBox is recording." The app requires no interaction to collect data.

### What it collects
| Data Type | Collection Method | Interval |
|-----------|-----------------|---------|
| GPS location | FusedLocationProvider callback | ~5 min (configurable) |
| Physical activity | Activity transition events | On change |
| WiFi environment | WifiManager scan | Every 15 min |
| App in foreground | UsageStatsManager | Every 5 min |
| Screen on/off/unlock | BroadcastReceiver | On change |
| Ambient volume | AudioRecord sampling | Every 15 min |
| Battery level | BroadcastReceiver | On change |
| Network connectivity | ConnectivityManager | On change |
| Barometric pressure | SensorManager | Every 10 min |
| Ambient light | SensorManager | Every 10 min |
| Call log metadata | ContentProvider polling | Every 5 min |

### How it works
1. **BlackBoxService** starts as a foreground service on app launch or device boot.
2. The **CollectorOrchestrator** starts all enabled collectors.
3. Each **BaseCollector** runs on its own schedule (polling) or registers system listeners (event-driven).
4. Collected records are passed through **RecordBatcher** which buffers them and writes in batches.
5. **SaveRecordUseCase** validates and persists each record to the encrypted SQLite database.

### Key files
- `androidApp/.../service/BlackBoxService.kt` — Foreground service lifecycle
- `androidApp/.../collector/base/CollectorOrchestrator.kt` — Collector management
- `androidApp/.../collector/base/BaseCollector.kt` — Abstract base class
- `shared/.../domain/usecase/record/SaveRecordUseCase.kt` — Persistence logic

### Privacy notes
- Location coordinates are stored but never logged in production
- WiFi SSIDs are stored; BSSIDs are used for place fingerprinting
- Audio: only decibel level is recorded — never any audio content
- Call log: only type (incoming/outgoing/missed) and duration — never the phone number (stored as SHA-256 hash)
- App names are stored (no content, only which app was in foreground)

---

## 2. Natural Language Search

### What the user sees
A search bar at the top. Type or speak any question about your day. Get an answer instantly, with an optional AI-enhanced response.

### Supported queries (examples)

**Location**
- "Where was I yesterday?"
- "Where did I go last Friday?"
- "How long was I at home this week?"

**Activity**
- "What was I doing yesterday evening?"
- "When was I walking this morning?"
- "How many steps last week?"

**Time**
- "What did I do last night?"
- "Show me Tuesday afternoon"
- "Tell me about last week"

**Patterns**
- "Do I usually go to the gym on Fridays?"
- "Where do I spend the most time?"

**Summary**
- "Summarise my day"
- "What was my most active day this week?"

### How it works
The query pipeline has 6 stages:

```
Raw text
  ↓ 1. Normalise (lowercase, strip punctuation)
  ↓ 2. Detect language (Hebrew / English)
  ↓ 3. Parse time expression → TimeRange
  ↓ 4. Classify intent → QueryIntent enum
  ↓ 5. Extract entities (place names, activity types, app names)
  ↓ 6. Build & execute DB query
  ↓ 7. Generate response text
```

**Time expressions supported**:
- Relative: "yesterday", "last week", "3 days ago", "two hours ago"
- Named: "this morning", "last night", "yesterday afternoon"
- Duration: "in the last 3 hours", "past two weeks"
- Day names: "on Monday", "last Tuesday"
- Hebrew equivalents for all of the above

### Voice input
Available via the microphone icon in the search bar. Uses Android's SpeechRecognizer.

### AI mode
If the user configured an API key in Settings, the query also triggers an AI-enhanced response. While the AI is processing, the local result is shown immediately and replaced by the AI response when ready. The user always sees *something* within milliseconds.

### Key files
- `shared/.../domain/query/TimeExpressionParser.kt` — Parses date/time phrases
- `shared/.../domain/query/IntentClassifier.kt` — Determines query type
- `shared/.../domain/query/EntityExtractor.kt` — Extracts place/activity names
- `shared/.../domain/query/QueryBuilder.kt` — Converts intent to DB query
- `shared/.../domain/query/ResponseGenerator.kt` — Formats the answer
- `shared/.../domain/query/QueryEngine.kt` — Orchestrates the pipeline
- `shared/.../domain/usecase/query/ProcessQueryUseCase.kt` — Invokes the engine
- `shared/.../ui/search/SearchViewModel.kt` — AI dual-phase logic

---

## 3. Timeline

### What the user sees
A vertically scrolling list of events for a selected day. Each entry shows a time, icon, title, and optional subtitle. Entries can be:
- **Location stays** — "Home · 4h 32m", "Office · 6h 15m"
- **Activity transitions** — "Started walking (confidence: 95%)"
- **Calls** — "Incoming call · 3m 45s", "Missed call"
- **Derived events** — Arrival, departure, notable changes

### Date navigation
Swipe or tap arrows to move between days. A date picker allows jumping to any day.

### How it works
`GetTimelineUseCase.invoke(startTime, endTime)` builds the timeline:

1. **Location stays**: Raw GPS fixes are grouped by proximity (Haversine distance < 100m). Consecutive points within that radius form a single "stay" entry. Each stay is matched against KnownPlaces for a name.

2. **WiFi fallback**: If GPS matching doesn't find a place, the nearest WiFi record during the stay is checked — if the router BSSID is in a known place's fingerprint, that place name is used.

3. **Activity records**: Pulled from `ACTIVITY` collector type. Each transition is a point event.

4. **Call log records**: Pulled from `CALL_LOG` collector type. Formatted with type and duration.

5. **Derived events**: Pre-computed events stored in the `DerivedEvent` table.

6. All entries are merged and sorted by `startTimestamp`.

### Business logic highlights
- Location stays are only shown if the user was within 100m radius of the first point. Moves between locations create separate stays.
- Duration displayed as "Xh Ym" or "Ym" depending on length.
- Unknown locations show as "(lat, lng)" coordinates.
- Calls show actual duration for connected calls; "Missed call" / "Rejected call" for others.

### Key files
- `shared/.../domain/usecase/timeline/GetTimelineUseCase.kt` — Main timeline building
- `shared/.../domain/model/timeline/TimelineEntry.kt` — Entry model
- `shared/.../ui/timeline/TimelineViewModel.kt` — State management
- `shared/.../ui/timeline/TimelineContent.kt` — Rendering

---

## 4. Map & Route Replay

### What the user sees
The Map screen has two modes, toggled by chips at the top:

**Day mode**: Full-screen interactive map (OpenStreetMap) showing the day's location history as dots connected by a line. Tapping a dot shows a popup with place name, time, and duration.

**Places mode**: A scrollable list of all saved places with their category, coordinates, and visit count.

### Route replay
A Play button animates through the day's stays sequentially, highlighting each one. Stops after the last stay or when the user taps Pause.

### Date navigation
Arrows and a date picker pill (always centered) navigate between days. The date pill shows either the exact date or "Today / Yesterday / [day name]".

### Known places management
In Places mode, each saved place row shows an **edit icon** (pencil, indigo) and a **delete icon** (trash). The + FAB creates a new place.

**Add Place dialog** fields:
- Name (free text)
- Category selector (Home, Work, Gym, Restaurant, etc.)
- Latitude / Longitude (pre-filled from the last stay on the current day's map)
- Radius slider (50m – 500m, 25m steps) — how close you must be for BlackBox to consider you "at this place"

**Edit Place dialog** (opened by the pencil icon):
- Same Name / Category / Radius fields, pre-filled with the current values
- Coordinates are **not** editable — moving a place requires delete + re-create (changing coordinates would corrupt historical visit records)
- Saved via `UpdatePlaceUseCase` → `PlaceRepository.updatePlace()`

### How it works
`GetDayLocationSummaryUseCase` clusters GPS fixes for the day into `LocationStay` objects (same 100m Haversine grouping as timeline). The map draws markers at the centroid of each cluster.

The `MapViewModel` uses `getDayLocationSummaryUseCase.observe(date, start, end)` — a reactive Flow — so the map updates live as new location data arrives.

### Key files
- `shared/.../domain/usecase/map/GetDayLocationSummaryUseCase.kt`
- `shared/.../ui/map/MapViewModel.kt`
- `shared/.../ui/map/MapContent.kt` — OsmMapView, PlacesList, AddPlaceDialog
- `shared/.../domain/model/map/LocationStay.kt`

---

## 5. Known Places

### What the user sees
Places appear as names throughout the app: in timeline stays ("Home"), in map dots, in insight cards ("Top places this week").

### Creating a place
**Manual**: Map screen → Places tab → + button → fill in name, category, coordinates, radius.

**Auto-detection** (background): `DetectKnownPlacesUseCase` clusters frequently visited GPS coordinates. When a cluster appears consistently, it creates a KnownPlace automatically (marked `isAutoDetected = true`).

### Editing a place
Tap the pencil icon on any row in Places mode to open the **Edit Place** dialog. You can change:
- **Name** — rename to anything you like
- **Category** — reclassify (e.g., OTHER → HOME)
- **Radius** — tighten (50m) or loosen (up to 500m) the match zone

Coordinates are intentionally not editable. Changing them would corrupt historical stay records that were matched against the original position. To move a place, delete it and create a new one.

### Place matching algorithm
1. `PlaceRepository.findNearestPlace(lat, lng)` runs a SQL query that ranks all places by Haversine distance.
2. The nearest place is returned **only if** the distance is within `place.radiusMeters`.
3. If no GPS match, `findPlaceByWifiBssid(bssid)` checks if the current router BSSID is stored in any place's `wifiFingerprint`.

### WiFi fingerprinting
When a user is at a known place, the WiFi BSSID (router MAC address) is stored in the `wifi_fingerprint` JSON array. On future visits, even if GPS is inaccurate (inside buildings), the BSSID match identifies the place.

### Data model
```
KnownPlace {
    id, name, latitude, longitude,
    radiusMeters = 100.0,       // configurable via Add Place dialog
    wifiFingerprint: List<String>,  // BSSIDs
    category: PlaceCategory,    // HOME, WORK, GYM, RESTAURANT, etc.
    visitCount, totalTimeMinutes,
    firstVisit, lastVisit,
    isAutoDetected, isHidden
}
```

### Key files
- `shared/.../domain/model/place/KnownPlace.kt`
- `shared/.../domain/repository/PlaceRepository.kt`
- `shared/.../data/repository/PlaceRepositoryImpl.kt` — findNearestPlace + findPlaceByWifiBssid
- `shared/.../domain/usecase/place/SavePlaceUseCase.kt`
- `shared/.../domain/usecase/place/UpdatePlaceUseCase.kt` — persists name/category/radius changes
- `shared/.../domain/usecase/place/DeletePlaceUseCase.kt`
- `shared/.../domain/usecase/place/DetectKnownPlacesUseCase.kt`

---

## 6. Insights Dashboard

### What the user sees
A vertically scrolling feed of cards:
- **Today hero**: Big step count with gradient, plus Screen time / Places visited / Top app mini-cards
- **Screen time today**: Per-app breakdown with progress bars (top 5 apps)
- **Steps trend**: 7-day bar chart, tappable bars
- **Screen time trend**: 7-day bar chart
- **Sleep trend**: 7-day bar chart, tappable bars expand to show bedtime/wake time
- **Top places + Top apps**: Two side-by-side ranking cards
- **Top contacts this week**: Ranked list of contacts (display name, call count, total duration, missed count)
- **Observations**: AI-generated text observations (lazy-loaded)
- **Averages footer**: Avg steps, avg screen time, avg sleep, best step day

### Data sources
All data for this screen comes from `GetInsightsBriefUseCase`:

| Card | Source |
|------|--------|
| Today steps | Hardware step counter (SensorEvent TYPE_STEP_COUNTER) |
| Today screen time | Raw SCREEN_STATE records, sum of ON→OFF gaps |
| Today places count | Distinct addresses from LocationRecord today |
| Today top app | APP_USAGE records today, sum session durations, top 1 |
| Screen time breakdown | APP_USAGE records today, top 5 (with orphaned-tail + gap-fill) |
| Step trend (7 days) | Raw ACTIVITY records grouped by day — always 7 entries |
| Screen trend (7 days) | Raw SCREEN_STATE records grouped by day — always 7 entries |
| Sleep trend (7 nights) | `DetectSleepSessionsUseCase.getWeekTrend()` — always 7 entries, null = no detection |
| Top places (week) | LocationRecord.address, grouped by distinct days |
| Top apps (week) | APP_USAGE records, grouped by app, sum durations (with orphaned-tail + gap-fill) |
| Top contacts (week) | CALL_LOG records grouped by `numberHash`; display name from `contactName` when available |
| Observations | GenerateInsightObservationsUseCase (AI or rule-based) |

> **Design note:** All weekly trends are computed from raw records rather than `DailySummary` rows.
> This guarantees exactly 7 bars in every chart even if `DailySummaryWorker` missed a day.

### Lifecycle-aware refresh
`InsightsScreen` dispatches `Action.ScreenResumed` via `LifecycleResumeEffect` every time the screen comes back into focus. The ViewModel refreshes data only when the existing snapshot is older than 5 minutes — short back-navigation causes no visible reload flash.

### Sleep bars with no data
Nights where sleep could not be detected render as short (20% height) dimmed-gray bars rather than disappearing. This keeps all 7 bars always visible and distinguishes "no data" from "zero sleep".

### Key files
- `shared/.../domain/usecase/insight/GetInsightsBriefUseCase.kt`
- `shared/.../ui/insights/InsightsContent.kt`
- `shared/.../ui/insights/InsightsViewModel.kt`
- `shared/.../ui/insights/InsightsScreen.kt`

---

## 7. Sleep Detection

### What the user sees
A 7-bar chart in the Insights screen. Each bar is a night. Tapping a non-empty bar expands a detail panel showing bedtime, wake time, and duration. Nights with no detectable data show as short dimmed-gray bars (never disappear).

### How it detects sleep
The algorithm looks at **screen ON/UNLOCK events** (not screen OFF events) for a 16-hour window from 20:00 the previous day to 12:00 (noon) of the target day.

**Step 1 — Build phone sessions**: Consecutive ON/UNLOCK events within 10 minutes of each other belong to the same "phone session" (brief middle-of-night checks).

**Step 2 — Build dark periods**: The gaps between consecutive phone sessions are "dark periods" (potential sleep time).

**Step 3 — Merge across brief wakes**: If a phone session lasted < 10 minutes **and started before 06:00**, the dark periods on either side are merged (you just glanced at the alarm clock — that's not waking up). Phone sessions starting at or after 06:00 are *never* merged regardless of duration — a 5-minute session at 7:27am opening an app is real morning activity, not a night check.

**Step 4 — Find the best gap**: The longest merged dark period that:
- Is ≥ 3 hours long
- Ends after 04:00 (filters out evening screen-off idle time)
- Is capped at 9 hours from the end (trims early evening "phone down" time)

**Step 5 — Airplane mode refinement**: If the user enabled airplane mode within 90 minutes before the detected bedtime, and no screen-on event occurred in that gap, the sleep start is extended backward to the airplane-mode timestamp. Only `airplaneMode == true` records trigger this — poor connectivity alone does not.

**Step 6 — Light validation**: Ambient light readings during the sleep window are checked. If ≥ 70% are DARK/DIM, `SleepSession.lightValidated = true`. If < 30% are DARK/DIM (e.g., phone left on a bright desk), `lightValidated = false`. This field is informational — it does not change the detected sleep times.

### Quality classification
| Duration | Quality |
|----------|---------|
| < 6 hours | POOR |
| 6–7 hours | FAIR |
| 7–9 hours | GOOD |
| > 9 hours | LONG |

### Weekly trend contract
`getWeekTrend()` always returns **exactly 7 entries**, oldest-first. Null entries indicate nights where no sleep session could be detected (too little screen data, user stayed up past noon, etc.). UI renders nulls as short gray bars.

### Why this approach
Using only "screen ON" events (not OFF events) makes the algorithm robust against:
- Service restarts (synthetic ON emitted on restart)
- Missed broadcast events
- Paired event desyncs (OFF without ON, etc.)

### Key files
- `shared/.../domain/usecase/sleep/DetectSleepSessionsUseCase.kt`
- `shared/.../domain/model/sleep/SleepSession.kt`
- `androidApp/.../collector/ScreenStateCollector.kt`

---

## 8. Step Counting

### What the user sees
A large number on the Insights screen ("4,250 steps today"), a comparison badge (▲ +18% vs avg), and a 7-day step trend chart.

### How it works
Two complementary sources:

**Primary: Hardware step counter** (`StepCounterProvider`)
- Reads `SensorEvent.TYPE_STEP_COUNTER` directly
- This sensor gives a cumulative count since last reboot
- The service snapshots the baseline at startup
- Today's steps = current reading − baseline

**Fallback: Activity record deltas**
- If the hardware counter is unavailable, step count is derived from `ActivityData.stepCountCumulative` in activity records
- Steps = max(cumulative) − min(cumulative) over the day
- This avoids the overcounting bug from summing `stepCountDelta` (each delta is "steps since session start", not "since last record")

### 7-day trend
Computed directly from raw `ACTIVITY` records by `computeWeekStepTrend()`. Records are grouped by calendar day in the device timezone; each day's count is `max(cumulativeSteps) − min(cumulativeSteps)`. This always produces exactly 7 entries — zero for days with no activity records — eliminating the missing-bar bug that occurred when `DailySummaryWorker` skipped a night.

### Key files
- `androidApp/.../platform/StepCounterProvider.kt`
- `shared/.../domain/usecase/insight/GetInsightsBriefUseCase.kt` — `computeTodaySteps()`, `computeWeekStepTrend()`

---

## 9. Screen Time Tracking

### What the user sees
- "Screen · 1h 27m" in the Today hero card
- A 7-day screen time trend chart
- Today's per-app screen time breakdown (top 5 apps with progress bars)

### How it calculates screen time
The `computeScreenMinutes()` function pairs ON → OFF events:

```
Screen ON  at 08:00
Screen OFF at 08:12  →  +12 min
Screen ON  at 09:30
Screen OFF at 10:15  →  +45 min
Screen ON  at 14:00  (still on)
                     →  +N min (counted up to "now")
Total: 57 + N minutes
```

The `ScreenStateCollector` emits a synthetic record on service start (either ON or OFF based on current screen state). This anchors the computation even after a service restart mid-session.

### Per-app breakdown
`todayTopApps` aggregates APP_USAGE records for today, groups by app display name, sums session durations, and returns the top 5. Each entry shows:
- App name
- Duration (e.g., "45m")
- A proportional progress bar (relative to the longest-duration app)

### 7-day trend
Computed from raw `SCREEN_STATE` records by `computeWeekScreenTrend()` — same ON→OFF pairing logic, applied per calendar day. Always produces exactly 7 entries. This replaces the previous `DailySummary`-based approach, which produced fewer bars whenever the midnight worker missed a day.

### Key files
- `androidApp/.../collector/ScreenStateCollector.kt`
- `shared/.../domain/usecase/insight/GetInsightsBriefUseCase.kt` — `computeScreenMinutes()`, `computeWeekScreenTrend()`
- `shared/.../ui/insights/InsightsContent.kt` — `AppBreakdownCard`

---

## 10. App Usage Tracking

### What the user sees
- "Top app · YouTube" in the Insights hero
- Per-app screen time breakdown (top 5) with progress bars
- "Top apps this week" ranking card (top 4, by total minutes)

### What gets collected
Every 5 minutes, `AppUsageCollector` queries `UsageStatsManager` for the currently foreground app. It records:
- Package name
- Display name (looked up from PackageManager)
- Category (COMMUNICATION, GAMES, etc.)
- Session start/end timestamps
- Whether it's a system app

### System package filtering
`UsageStatsManager` does not reliably mark all background system components as `isSystemApp = true` on every Android version or OEM ROM. Packages like `com.android.cellbroadcastreceiver` and `com.samsung.android.app.clockpackage` can slip through the `!isSystemApp` filter and appear as "Cellbroadcastreceiver" / "Clockpackage" in the popular-apps list.

A hardcoded `SYSTEM_PACKAGE_BLOCKLIST` in `GetInsightsBriefUseCase` checks the raw `foregroundApp` package name before any display-name resolution. Packages on this list are always excluded regardless of the `isSystemApp` flag. See §9 in ALGORITHMS.md for the full list.

### Package name cleanup
Display names that look like package names (`com.android.example`) are cleaned to human-readable labels:
1. Check exact match in `KNOWN_PACKAGES` map (WhatsApp, YouTube, etc.)
2. Try prefix match
3. Walk package segments from right, skip generic words (android, app, service, etc.)
4. Capitalize the first meaningful segment

### Usage duration accuracy
`aggregateTopApps()` runs three phases to improve accuracy beyond raw `sessionDurationMs`:

1. **Base aggregation** — sum `sessionDurationMs` per app.
2. **Orphaned tail** — extend the last app's time to the first SCREEN_OFF after its session ends (capped at 30 min). Captures usage that continued after the last record was written to the DB.
3. **Gap fill** — for completed screen-ON intervals (ended > 1 h ago) that contain no APP_USAGE record, attribute the interval to the most recent preceding app within 15 min (capped at 60 min). Recovers long single-app sessions that UsageStatsManager split across reporting windows.

### Key files
- `androidApp/.../collector/AppUsageCollector.kt`
- `shared/.../domain/model/record/AppUsageData.kt`
- `shared/.../domain/usecase/insight/GetInsightsBriefUseCase.kt` — `aggregateTopApps()`, `buildScreenIntervals()`, `cleanAppDisplayName()`, `isBlockedPackage()`, `SYSTEM_PACKAGE_BLOCKLIST`

---

## 11. Call Log in Timeline

### What the user sees
Entries in the Timeline like:
- "Incoming call · 3m 45s"
- "Outgoing call · 12m 8s"
- "Missed call"
- "Rejected call"

### How it works
`CallLogCollector` polls the Android system `CallLog.Calls` content provider every 5 minutes, querying for calls newer than the last query time. On **first start**, it looks back **30 days** to import historical calls.

For privacy: only the call type and duration are stored in plain text. The phone number is stored as a truncated SHA-256 hash (16 hex chars, non-reversible).

The collector also reads `CallLog.Calls.CACHED_NAME` — the contact display name the system dialer had at the time of the call. This requires only `READ_CALL_LOG` (no `READ_CONTACTS`). The name is stored in `CallLogData.contactName` and is `null` for unknown/unsaved numbers.

`GetTimelineUseCase` queries `CALL_LOG` records for the day and formats them as `TimelineEntry.EVENT` items.

### Duration format
- `0s` calls (missed/rejected) — no duration shown
- Under 1 minute: "45s"
- 1 minute and above: "3m 45s"

### Top contacts card (Insights)
`aggregateTopContacts()` in `GetInsightsBriefUseCase` groups the week's CALL_LOG records by `numberHash`. For each group it:
1. Prefers a non-null `contactName` over "Unknown" (upgrades the label if any call in the group has a name)
2. Sums total calls, total duration, and missed call count
3. Sorts by total call count descending, returns top 5

The result is `List<ContactCallStat>` stored in `InsightsBrief.weekTopContacts`. The Insights screen renders this as a `TopContactsCard` if the list is non-empty. No additional permissions are required beyond `READ_CALL_LOG`.

### Key files
- `androidApp/.../collector/CallLogCollector.kt`
- `shared/.../domain/model/record/CallLogData.kt` — `contactName: String?` field
- `shared/.../domain/usecase/timeline/GetTimelineUseCase.kt` — call log section
- `shared/.../domain/usecase/insight/GetInsightsBriefUseCase.kt` — `aggregateTopContacts()`, `ContactCallStat`
- `shared/.../ui/insights/InsightsContent.kt` — `TopContactsCard`

---

## 12. WiFi-Based Place Matching

### What it does
Improves place recognition indoors where GPS is inaccurate (apartments, offices, underground). When the GPS-based place lookup returns nothing, the timeline builder checks if the router BSSID during the stay matches any known place's WiFi fingerprint.

### User-visible effect
Location stays in the Timeline and Map show a place name ("Home", "Office") even when you're deep inside a building where GPS would otherwise report an inaccurate or empty position.

### How to set up WiFi fingerprinting
Currently, BSSIDs are collected passively by the WiFi collector and stored in the `KnownPlace.wifiFingerprint` list when the user is within the GPS radius of a place. (A future "Link WiFi" UI would expose this explicitly.)

### Key files
- `shared/.../domain/repository/PlaceRepository.kt` — `findPlaceByWifiBssid()`
- `shared/.../data/repository/PlaceRepositoryImpl.kt` — implementation
- `shared/.../domain/usecase/timeline/GetTimelineUseCase.kt` — WiFi fallback in `buildStayEntry()`

---

## 13. Settings & Collector Toggles

### What the user sees
A list of all collectors with a toggle switch. Each toggle enables or disables that collector. Below the list: data retention period selector, and a debug dump button.

### Per-collector settings
Each collector has a `CollectorSetting` row in the database:
- `is_enabled` — whether the collector runs
- `collection_interval_ms` — overrides the default polling interval
- `custom_config_json` — arbitrary configuration (not used in MVP)

### Battery profiles
The `CollectorOrchestrator` applies a battery profile that adjusts all intervals simultaneously:

| Profile | Trigger | Effect |
|---------|---------|--------|
| MAXIMUM | Charging | Shortest intervals |
| NORMAL | Battery > 50% | Default intervals |
| REDUCED | Battery 20–50% | 3× longer intervals |
| MINIMAL | Battery 10–20% | Location only |
| CRITICAL | Battery < 10% | Everything paused |

### Data retention
Users can select how long data is kept: 30 days, 90 days, 180 days, or 1 year. `CleanupWorker` deletes records older than the retention period at 3 AM daily.

### Permissions display
Settings shows which permissions are granted. Tapping a permission label opens the Android system settings for that permission.

### Key files
- `shared/.../domain/model/settings/CollectorSetting.kt`
- `shared/.../domain/usecase/settings/UpdateCollectorSettingUseCase.kt`
- `shared/.../ui/settings/SettingsViewModel.kt`
- `androidApp/.../collector/base/CollectorOrchestrator.kt`

---

## 14. Data Security & Encryption

### Encryption at rest
All data is stored in a SQLCipher-encrypted SQLite database. The encryption key is:
1. A 256-bit AES key generated and stored in the Android Keystore
2. The Keystore key encrypts a PBKDF2-derived passphrase
3. The passphrase is passed to SQLCipher at database open

The Keystore uses **StrongBox** (hardware security module) when available, falling back to TEE (Trusted Execution Environment).

### Biometric lock
The app can be locked with biometric authentication (fingerprint or face). Authentication is required to open the app after 1 minute in the background. This is implemented in `BiometricManager` using Android's `BiometricPrompt`.

### Hash chain integrity
Each `DailySummary` contains:
- `day_hash`: SHA-256 hash of all records for that day
- `previous_day_hash`: Hash of the previous day's summary

This creates a tamper-detection chain — modifying any record breaks the chain from that day forward.

### Privacy commitment
- No network calls except optional AI queries
- Logs never contain GPS coordinates, WiFi SSIDs, phone numbers, or app content
- Audio recording permission is used only to measure volume (dB level), never to record audio content
- The `READ_CALL_LOG` permission is used only to read metadata (type, duration) — numbers are immediately hashed and the plaintext is discarded

### Key files
- `androidApp/.../security/KeyManager.kt`
- `androidApp/.../security/BiometricManager.kt`
- `shared/.../platform/EncryptionManager.kt` (expect/actual)

---

## 15. Onboarding

### What the user sees
On first launch: a 5-page flow:
1. **Welcome** — "Your life has a black box" introduction
2. **Location permission** — Explains why, requests permission
3. **Activity permission** — Explains why, requests permission
4. **Notification permission** — Required for foreground service (Android 13+)
5. **Usage access** — Special permission for AppUsageCollector
6. **Ready** — "BlackBox is now recording" confirmation

### Skip logic
- If all permissions are already granted, onboarding is skipped
- Each page can be skipped individually (some collectors work without all permissions)

### Persistence
Onboarding completion is stored in `SettingsRepository` via a boolean flag. On every launch, the app checks this flag to decide the start destination.

### Key files
- `shared/.../ui/onboarding/OnboardingViewModel.kt`
- `shared/.../ui/onboarding/OnboardingContent.kt`
- `androidApp/.../di/ViewModelModule.kt` — permission checks injected as lambdas
