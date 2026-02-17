# BlackBox — Personal Life Flight Recorder
## Complete Product Specification v1.0

---

## 1. Vision & Philosophy

BlackBox is a **privacy-first, offline-first personal life recorder** that passively captures contextual data from your device — never content, only metadata and environmental signals. It runs silently in the background, building a searchable timeline of your life. You never think about it... until you need it.

**Core Principles:**
- **Zero-effort capture** — the user does nothing; the app works silently
- **Privacy by architecture** — all data stays on-device, encrypted, no cloud, no accounts, no login
- **Metadata only** — never records call content, message text, or conversation audio; only captures patterns, context, and environmental signals
- **Natural language retrieval** — ask questions in plain human language, powered by an on-device AI model
- **User sovereignty** — the user controls exactly what is collected and can delete anything at any time

**Tagline:** *"Your life has a black box. Now you can read it."*

---

## 2. Target Users & Use Cases

### 2.1 Primary User Personas

**The Forgetful Professional** — "Where was I last Tuesday at 3pm? I need to fill out my timesheet."

**The Dispute Resolver** — "My landlord claims I wasn't home when the leak happened. I can prove I was."

**The Self-Optimizer** — "I feel like I'm more productive on days I walk to work. Can the data confirm this?"

**The Concerned Parent** — "My teenager says they were at a friend's house. Were they?"
*(Note: parental use case is secondary and requires the teen's own device with their knowledge)*

**The Legal Protector** — "I need to prove I was at work during the hours my employer claims I wasn't."

### 2.2 Core Use Cases

| Use Case | Example Query | Data Sources Used |
|----------|---------------|-------------------|
| Alibi / location proof | "Where was I last Friday evening?" | GPS, WiFi, cell tower |
| Activity recall | "What was I doing yesterday afternoon?" | App usage, movement, location |
| Pattern discovery | "Do I sleep better on days I exercise?" | Movement, screen time, phone usage patterns |
| Expense context | "When was the last time I was at IKEA?" | Location history |
| Health correlation | "Show me my activity levels this week vs last week" | Step count, movement intensity |
| Device forensics | "Was someone using my phone at 2am?" | Screen on/off, app usage, touch patterns |
| Routine tracking | "What time do I usually leave work?" | Location departure patterns |
| Meeting proof | "Was I at the office on January 15th?" | Location, WiFi connections |
| Travel log | "Show me everywhere I went last month" | GPS breadcrumbs, timeline |
| Habit awareness | "How much time did I spend at home this week?" | Location duration analysis |

---

## 3. App Architecture Overview

### 3.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                    │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Search UI   │  │  Timeline UI  │  │  Settings UI  │  │
│  │  (Compose)   │  │  (Compose)    │  │  (Compose)    │  │
│  └──────┬──────┘  └──────┬───────┘  └───────┬───────┘  │
├─────────┴────────────────┴───────────────────┴──────────┤
│                    SHARED KMP MODULE                     │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Domain Layer (Pure Kotlin)            │   │
│  │  - Query Engine (NL → structured query)           │   │
│  │  - Timeline Builder                               │   │
│  │  - Pattern Analyzer                               │   │
│  │  - Data Aggregator                                │   │
│  │  - Encryption Manager                             │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Data Layer (KMP)                      │   │
│  │  - SQLDelight Database                            │   │
│  │  - Repository Interfaces                          │   │
│  │  - Data Models                                    │   │
│  └──────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────┤
│               PLATFORM-SPECIFIC LAYER                    │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Android (POC)           │  iOS (Future)          │   │
│  │  - Foreground Service    │  - Background Tasks    │   │
│  │  - SensorManager         │  - CoreMotion          │   │
│  │  - FusedLocationProvider │  - CoreLocation        │   │
│  │  - UsageStatsManager     │  - Screen Time API     │   │
│  │  - WifiManager           │  - NEHotspotHelper     │   │
│  │  - AccessibilityService  │  - (limited on iOS)    │   │
│  │  - Android Keystore      │  - Secure Enclave      │   │
│  │  - On-device ML (ONNX)   │  - CoreML              │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 3.2 KMP Module Structure

```
blackbox/
├── shared/                          # KMP shared module (~70% of code)
│   ├── domain/
│   │   ├── model/                   # Data classes for all record types
│   │   ├── query/                   # Natural language query engine
│   │   ├── timeline/                # Timeline construction & aggregation
│   │   ├── patterns/                # Pattern detection algorithms
│   │   ├── encryption/              # Encryption interfaces
│   │   └── usecase/                 # Use cases (Clean Architecture)
│   ├── data/
│   │   ├── database/                # SQLDelight schemas & queries
│   │   ├── repository/              # Repository implementations
│   │   └── mapper/                  # DB entity ↔ domain model mappers
│   └── platform/                    # expect declarations
│       ├── Collectors.kt            # expect for each data collector
│       ├── Encryption.kt            # expect for platform crypto
│       ├── MLEngine.kt              # expect for on-device inference
│       └── DeviceInfo.kt            # expect for device identifiers
│
├── androidApp/                      # Android-specific (POC)
│   ├── collectors/                  # actual implementations
│   │   ├── LocationCollector.kt
│   │   ├── ActivityCollector.kt
│   │   ├── WifiCollector.kt
│   │   ├── AppUsageCollector.kt
│   │   ├── SensorCollector.kt
│   │   ├── ScreenStateCollector.kt
│   │   ├── AudioLevelCollector.kt
│   │   ├── BatteryCollector.kt
│   │   ├── ConnectivityCollector.kt
│   │   └── BluetoothCollector.kt
│   ├── service/
│   │   └── BlackBoxService.kt       # Foreground service orchestrator
│   ├── ml/
│   │   └── OnnxQueryEngine.kt       # On-device NL model
│   ├── ui/                          # Jetpack Compose screens
│   └── di/                          # Koin modules
│
└── iosApp/                          # iOS (future phase)
    ├── collectors/                   # actual implementations
    └── ml/
        └── CoreMLQueryEngine.kt
```

---

## 4. Data Collection System

### 4.1 Data Types & Collection Strategy

Each data type is an independent **Collector** that can be enabled/disabled by the user. All collectors implement a common interface:

```kotlin
// shared/platform/Collector.kt
interface DataCollector {
    val collectorType: CollectorType
    val isEnabled: Boolean
    val collectionInterval: Duration
    suspend fun collect(): CollectedRecord
    fun requiredPermissions(): List<String>
}
```

### 4.2 Collector Specifications

#### 4.2.1 Location Collector (HIGH PRIORITY)
**What it captures:**
- GPS coordinates (latitude, longitude, altitude)
- Horizontal accuracy in meters
- Speed and bearing
- Location source (GPS, WiFi, cell tower, fused)

**Collection strategy:**
- **Active mode:** Every 5 minutes when battery > 30%
- **Passive mode:** Every 15 minutes when battery 15-30%
- **Minimal mode:** Only on significant location change when battery < 15%
- Uses `FusedLocationProviderClient` with `PRIORITY_BALANCED_POWER_ACCURACY`
- Geofence-based optimization: reduce frequency when stationary for > 30 minutes

**Storage per record:** ~80 bytes
**Daily estimate:** ~288 records (5-min interval) = ~23 KB/day

**Android implementation:**
```kotlin
// androidApp/collectors/LocationCollector.kt
actual class LocationCollector(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) : DataCollector {
    // Uses LocationRequest.Builder with appropriate intervals
    // Falls back to passive provider when battery is low
    // Detects "stationary" state via ActivityRecognition to reduce polling
}
```

#### 4.2.2 Activity / Motion Collector (HIGH PRIORITY)
**What it captures:**
- Motion state: STILL, WALKING, RUNNING, IN_VEHICLE, ON_BICYCLE, TILTING
- Confidence level (0-100) for each detected activity
- Step count (cumulative and per-interval)
- Movement intensity score (derived from accelerometer magnitude)

**Collection strategy:**
- Activity transitions captured via `ActivityRecognitionClient` (event-driven, very battery efficient)
- Step count sampled every 10 minutes from `TYPE_STEP_COUNTER`
- Movement intensity derived from accelerometer RMS every 5 minutes (sampled for 10 seconds only)

**Storage per record:** ~60 bytes
**Daily estimate:** ~200 records = ~12 KB/day

#### 4.2.3 WiFi Environment Collector (MEDIUM PRIORITY)
**What it captures:**
- Connected WiFi SSID (network name)
- BSSID (router MAC address — for indoor location differentiation)
- Signal strength (RSSI in dBm)
- List of nearby WiFi networks (SSIDs only, for environment fingerprinting)

**Why this matters:**
- WiFi is more accurate than GPS indoors
- "Connected to office WiFi" proves you were at the office
- Different BSSIDs on the same SSID can differentiate floors/rooms
- WiFi environment fingerprint is unique per location

**Collection strategy:**
- Scan on every location change event
- Periodic scan every 15 minutes
- Record connected network changes as events

**Storage per record:** ~200 bytes (with nearby networks list)
**Daily estimate:** ~96 records = ~19 KB/day

#### 4.2.4 App Usage Collector (HIGH PRIORITY)
**What it captures:**
- Foreground app package name
- Category of the app (social, productivity, entertainment, communication, etc.)
- Duration in foreground
- Session start and end timestamps

**What it does NOT capture:**
- Screen content
- Typed text
- Notification content
- In-app actions

**Collection strategy:**
- Uses `UsageStatsManager` to query usage events
- Polls every 5 minutes for foreground app changes
- Categorizes apps using a local mapping table (bundled, not from internet)

**Storage per record:** ~100 bytes
**Daily estimate:** ~300 records = ~30 KB/day

**Android requirement:** `PACKAGE_USAGE_STATS` permission (requires user to enable in system Settings)

#### 4.2.5 Screen State Collector (HIGH PRIORITY)
**What it captures:**
- Screen ON/OFF events with timestamps
- Screen unlock events (without method details)
- Screen brightness level
- Phone orientation (portrait/landscape/face-down/face-up)

**Why this matters:**
- "Phone was face-down from 11pm to 7am" = sleeping pattern
- "Screen unlocked 47 times today" = usage intensity
- Brightness changes can indicate indoor/outdoor transitions

**Collection strategy:**
- Event-driven via `BroadcastReceiver` for screen on/off/unlock — zero battery cost
- Orientation sampled every 2 minutes from accelerometer (1-second sample)

**Storage per record:** ~40 bytes
**Daily estimate:** ~150 events = ~6 KB/day

#### 4.2.6 Ambient Audio Level Collector (MEDIUM PRIORITY)
**What it captures:**
- Decibel level (dB SPL equivalent) from microphone
- Sound classification: SILENT, QUIET, CONVERSATION, LOUD, VERY_LOUD
- Ambient noise frequency profile (low/mid/high bands — NOT speech recognition)

**What it absolutely does NOT capture:**
- Audio recordings of any kind
- Speech content
- Voice recognition
- Identifiable sounds

**Collection strategy:**
- Every 15 minutes, activate microphone for 3 seconds only
- Compute RMS amplitude → convert to dB scale
- Run basic FFT for frequency band classification
- Immediately discard raw audio buffer after computing metrics
- **Never writes raw audio to storage**

**Storage per record:** ~50 bytes
**Daily estimate:** ~96 records = ~5 KB/day

**Privacy note:** This is the most sensitive collector. The UI must clearly explain that NO audio is recorded or stored — only volume levels are measured, similar to a decibel meter app.

#### 4.2.7 Battery & Charging Collector (LOW PRIORITY)
**What it captures:**
- Battery percentage
- Charging state (charging/discharging/full)
- Charging source (USB, AC, wireless)
- Battery temperature

**Why this matters:**
- Charging events correlate with being at specific locations (home, office, car)
- Battery drain rate indicates usage intensity
- "Phone was charging from 11pm to 7am" supports sleep pattern detection

**Collection strategy:**
- Event-driven via `BroadcastReceiver` for charging state changes
- Battery level sampled every 30 minutes

**Storage per record:** ~30 bytes
**Daily estimate:** ~60 records = ~2 KB/day

#### 4.2.8 Connectivity Collector (LOW PRIORITY)
**What it captures:**
- Network type (WiFi, cellular 4G/5G, none)
- Cellular carrier name
- Airplane mode state
- Bluetooth on/off state
- Connected Bluetooth device names (not MAC addresses of unknown devices)

**Why this matters:**
- "Connected to car Bluetooth" = driving
- "Connected to office WiFi + work Bluetooth headset" = at office working
- "Airplane mode on for 4 hours" = was on a flight
- Carrier changes can indicate international travel

**Collection strategy:**
- Event-driven for state changes (WiFi connected/disconnected, Bluetooth connected/disconnected)
- Periodic Bluetooth scan every 15 minutes for nearby known devices

**Storage per record:** ~120 bytes
**Daily estimate:** ~80 records = ~10 KB/day

#### 4.2.9 Barometric Pressure Collector (LOW PRIORITY)
**What it captures:**
- Atmospheric pressure (hPa)
- Relative altitude changes

**Why this matters:**
- Floor detection in buildings (each floor ≈ 0.12 hPa difference)
- "You went up 5 floors at 9:03am" when combined with office location = took elevator to office
- Weather correlation with behavior patterns

**Collection strategy:**
- Sample every 10 minutes from `TYPE_PRESSURE` sensor
- Compute relative changes for floor/altitude detection

**Storage per record:** ~20 bytes
**Daily estimate:** ~144 records = ~3 KB/day

#### 4.2.10 Light Sensor Collector (LOW PRIORITY)
**What it captures:**
- Ambient light level in lux
- Classification: DARK, DIM, INDOOR, OUTDOOR_SHADE, DIRECT_SUNLIGHT

**Why this matters:**
- Dark environment + still + screen off = likely sleeping
- Sudden bright light after darkness = woke up
- Indoor vs outdoor classification supports activity detection

**Collection strategy:**
- Sample every 10 minutes from `TYPE_LIGHT` sensor (1-second reading)

**Storage per record:** ~20 bytes
**Daily estimate:** ~144 records = ~3 KB/day

### 4.3 Storage Estimates Summary

| Collector | Priority | Daily Storage | Monthly Storage | Yearly Storage |
|-----------|----------|---------------|-----------------|----------------|
| Location | HIGH | 23 KB | 690 KB | 8.2 MB |
| Activity/Motion | HIGH | 12 KB | 360 KB | 4.3 MB |
| WiFi Environment | MEDIUM | 19 KB | 570 KB | 6.8 MB |
| App Usage | HIGH | 30 KB | 900 KB | 10.7 MB |
| Screen State | HIGH | 6 KB | 180 KB | 2.1 MB |
| Audio Level | MEDIUM | 5 KB | 150 KB | 1.8 MB |
| Battery | LOW | 2 KB | 60 KB | 0.7 MB |
| Connectivity | LOW | 10 KB | 300 KB | 3.6 MB |
| Barometer | LOW | 3 KB | 90 KB | 1.1 MB |
| Light | LOW | 3 KB | 90 KB | 1.1 MB |
| **TOTAL** | | **113 KB** | **3.4 MB** | **40.4 MB** |

**1 year of complete life recording ≈ 40 MB.** This is tiny. A single photo is larger.

The timeline index and derived data (aggregations, daily summaries) add approximately 20% overhead, bringing the total to roughly **~50 MB per year**. This means the app can easily store 5+ years of data on any modern device.

---

## 5. Database Design

### 5.1 SQLDelight Schema

```sql
-- Core record table: every collected data point
CREATE TABLE BlackBoxRecord (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,           -- Unix epoch milliseconds
    collector_type TEXT NOT NULL,          -- enum: LOCATION, ACTIVITY, WIFI, etc.
    data_json TEXT NOT NULL,              -- JSON-encoded collector-specific data
    accuracy_score REAL DEFAULT 1.0,      -- 0.0-1.0 confidence in this record
    session_id TEXT NOT NULL,             -- groups records from same collection cycle
    created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

CREATE INDEX idx_record_timestamp ON BlackBoxRecord(timestamp);
CREATE INDEX idx_record_collector ON BlackBoxRecord(collector_type, timestamp);
CREATE INDEX idx_record_session ON BlackBoxRecord(session_id);

-- Location records (denormalized for fast geo-queries)
CREATE TABLE LocationRecord (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id INTEGER NOT NULL REFERENCES BlackBoxRecord(id) ON DELETE CASCADE,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    altitude REAL,
    accuracy_meters REAL,
    speed REAL,
    bearing REAL,
    source TEXT NOT NULL,                  -- GPS, WIFI, CELL, FUSED
    timestamp INTEGER NOT NULL
);

CREATE INDEX idx_location_timestamp ON LocationRecord(timestamp);
CREATE INDEX idx_location_coords ON LocationRecord(latitude, longitude);

-- Daily summaries (pre-computed for fast retrieval)
CREATE TABLE DailySummary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL UNIQUE,             -- "2026-02-14"
    total_steps INTEGER DEFAULT 0,
    total_distance_meters REAL DEFAULT 0,
    time_at_home_minutes INTEGER DEFAULT 0,
    time_at_work_minutes INTEGER DEFAULT 0,
    time_in_transit_minutes INTEGER DEFAULT 0,
    screen_on_count INTEGER DEFAULT 0,
    total_screen_time_minutes INTEGER DEFAULT 0,
    most_used_apps TEXT,                   -- JSON: [{"app": "...", "minutes": N}]
    locations_visited TEXT,                -- JSON: [{"name": "...", "lat": N, "lng": N, "minutes": N}]
    activity_breakdown TEXT,              -- JSON: {"still": 480, "walking": 60, ...}
    noise_avg_db REAL,
    first_screen_on INTEGER,              -- timestamp of first screen unlock
    last_screen_off INTEGER,              -- timestamp of last screen off
    sleep_start_estimate INTEGER,         -- derived: when user likely fell asleep
    sleep_end_estimate INTEGER,           -- derived: when user likely woke up
    summary_text TEXT,                    -- AI-generated natural language summary
    created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

CREATE INDEX idx_summary_date ON DailySummary(date);

-- Known places (learned over time)
CREATE TABLE KnownPlace (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,                    -- user-assigned or auto-generated: "Home", "Office", "Gym"
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    radius_meters REAL NOT NULL DEFAULT 100,
    wifi_fingerprint TEXT,                -- JSON: associated WiFi BSSIDs
    category TEXT NOT NULL DEFAULT 'OTHER', -- HOME, WORK, GYM, SHOPPING, RESTAURANT, etc.
    visit_count INTEGER DEFAULT 0,
    total_time_minutes INTEGER DEFAULT 0,
    first_visit INTEGER,
    last_visit INTEGER,
    is_auto_detected INTEGER DEFAULT 1,   -- 1 = auto-detected, 0 = user-named
    created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

-- Significant events (derived from raw data)
CREATE TABLE DerivedEvent (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    event_type TEXT NOT NULL,              -- ARRIVED, DEPARTED, STARTED_DRIVING, SLEEP_START, SLEEP_END, etc.
    description TEXT NOT NULL,             -- "Arrived at Office"
    place_id INTEGER REFERENCES KnownPlace(id),
    metadata_json TEXT,                   -- additional context
    confidence REAL DEFAULT 1.0,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

CREATE INDEX idx_event_timestamp ON DerivedEvent(timestamp);
CREATE INDEX idx_event_type ON DerivedEvent(event_type, timestamp);

-- User settings for collectors
CREATE TABLE CollectorSetting (
    collector_type TEXT PRIMARY KEY,
    is_enabled INTEGER NOT NULL DEFAULT 1,
    collection_interval_ms INTEGER NOT NULL,
    custom_config_json TEXT               -- collector-specific settings
);
```

### 5.2 Data Retention Policy

- **Raw records:** Kept for a configurable duration (default: 1 year)
- **Daily summaries:** Kept indefinitely (tiny storage footprint)
- **Derived events:** Kept indefinitely
- **Known places:** Kept indefinitely
- Auto-cleanup runs nightly, deleting raw records older than the retention period
- User can manually purge any time range at will

---

## 6. On-Device AI Query Engine

### 6.1 Overview

The query engine allows the user to ask questions in natural language and get answers from the locally stored data. Everything runs on-device — no internet required.

### 6.2 Architecture

```
User Query (natural language)
        │
        ▼
┌─────────────────────┐
│  Query Preprocessor  │  ← Normalize text, detect language (EN/HE), extract time references
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│  Intent Classifier   │  ← On-device model: classify query type
│  (ONNX Runtime)      │     LOCATION_QUERY, ACTIVITY_QUERY, TEMPORAL_QUERY,
└──────────┬──────────┘     PATTERN_QUERY, PROOF_QUERY, SUMMARY_QUERY
           ▼
┌─────────────────────┐
│  Entity Extractor    │  ← Extract: dates, times, places, activities, durations
│  (Rule-based + ML)   │
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│  Query Builder       │  ← Convert intent + entities → SQL query / aggregation pipeline
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│  Data Retriever      │  ← Execute against SQLDelight DB
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│  Response Generator  │  ← Format results into human-readable answer
│  (Template-based)    │
└─────────────────────┘
```

### 6.3 Query Types & Examples

#### Type 1: LOCATION_QUERY
**Examples:**
- "Where was I yesterday at 3pm?" → GPS lookup at timestamp
- "Where was I last Friday evening?" → Location records for Friday 18:00-23:59
- "When was the last time I was at the mall?" → Reverse lookup: find latest record matching known place
- "How long was I at work today?" → Duration calculation at "Work" known place
- "Show me everywhere I went last week" → Full location timeline for date range

**Resolution strategy:** Direct DB lookup on LocationRecord table filtered by time range, optionally joined with KnownPlace for named location matching.

#### Type 2: ACTIVITY_QUERY
**Examples:**
- "What was I doing yesterday afternoon?" → Composite: location + activity + app usage for time range
- "How many steps did I take today?" → DailySummary lookup
- "Was I driving at midnight?" → Activity records filtered by type + time
- "How much time did I spend on my phone today?" → Screen state aggregation

**Resolution strategy:** Combines ActivityRecord, AppUsage, and ScreenState data for holistic picture.

#### Type 3: TEMPORAL_QUERY
**Examples:**
- "What time did I leave home this morning?" → DerivedEvent where type=DEPARTED and place=Home
- "What time did I wake up yesterday?" → Sleep end estimate from DailySummary
- "What time did I arrive at work?" → DerivedEvent where type=ARRIVED and place=Work
- "When did I get home last night?" → DerivedEvent reverse lookup

**Resolution strategy:** Primarily uses DerivedEvent table, falls back to raw location transitions.

#### Type 4: PATTERN_QUERY
**Examples:**
- "What time do I usually wake up?" → Aggregate sleep_end_estimate across DailySummary
- "Do I sleep better on days I exercise?" → Correlate step count with sleep duration
- "How much time do I spend at home on weekends vs weekdays?" → Aggregate time_at_home by day type
- "Am I spending more time on social media this month?" → App usage trend analysis
- "Which day of the week do I walk the most?" → Step count grouped by weekday

**Resolution strategy:** Aggregation queries across DailySummary and raw records, with statistical analysis.

#### Type 5: PROOF_QUERY
**Examples:**
- "Prove I was at the office on January 15th" → Generate proof report with all supporting data
- "Show evidence I was home all evening on Saturday" → Consolidated evidence from multiple collectors
- "Was my phone used while I was sleeping?" → Detect anomalous screen activity during estimated sleep

**Resolution strategy:** Collects all available data for the time range, cross-references multiple collectors for corroboration, generates a confidence score.

#### Type 6: SUMMARY_QUERY
**Examples:**
- "Summarize my day yesterday" → Full day narrative from DailySummary
- "What did my week look like?" → Weekly aggregation with highlights
- "Tell me about last Monday" → Specific day detailed summary

**Resolution strategy:** Reads from DailySummary, enhanced with DerivedEvents for narrative.

### 6.4 On-Device Model Strategy

**For the POC (Android):**

**Phase 1 — Rule-Based NLP (MVP):**
- Use a lightweight rule-based parser — no ML model needed initially
- Regex-based time extraction: "yesterday", "last Friday", "this morning", "January 15th", "3pm", Hebrew equivalents
- Keyword-based intent classification: location words (where, place, location), time words (when, what time), activity words (doing, steps, walking)
- This alone covers 80%+ of realistic queries

```kotlin
// shared/domain/query/RuleBasedQueryParser.kt
class RuleBasedQueryParser {

    fun parse(rawQuery: String): ParsedQuery {
        val normalizedQuery = normalize(rawQuery)
        val timeRange = extractTimeRange(normalizedQuery)
        val intent = classifyIntent(normalizedQuery)
        val entities = extractEntities(normalizedQuery)
        return ParsedQuery(intent, timeRange, entities)
    }

    private fun extractTimeRange(query: String): TimeRange {
        // "yesterday" → yesterday 00:00 to 23:59
        // "yesterday afternoon" → yesterday 12:00 to 18:00
        // "last Friday evening" → last Friday 18:00 to 23:59
        // "this morning" → today 06:00 to 12:00
        // "January 15th" → Jan 15 00:00 to 23:59
        // "last week" → Monday to Sunday of previous week
        // Hebrew: "אתמול", "שישי שעבר", "הבוקר"
    }

    private fun classifyIntent(query: String): QueryIntent {
        // Keyword matching with priority:
        // "where" / "location" / "place" / "איפה" → LOCATION_QUERY
        // "when" / "what time" / "מתי" → TEMPORAL_QUERY
        // "how long" / "how much time" / "כמה זמן" → DURATION_QUERY
        // "usually" / "pattern" / "average" / "בדרך כלל" → PATTERN_QUERY
        // "prove" / "evidence" / "show that" / "הוכח" → PROOF_QUERY
        // "summarize" / "tell me about" / "סכם" → SUMMARY_QUERY
        // Default: ACTIVITY_QUERY
    }
}
```

**Phase 2 — On-Device ML (Post-MVP):**
- Use ONNX Runtime for Android with a small intent classification model
- Model: Fine-tuned DistilBERT (~66MB) or TinyBERT (~15MB) for intent + entity extraction
- Alternatively: Use Google's ML Kit Entity Extraction (free, on-device, supports Hebrew)
- Training data: Generate synthetic query-intent pairs covering all query types

**Phase 3 — Advanced (Future):**
- Integrate a small on-device LLM (like Gemma 2B or Phi-3 Mini) for truly conversational queries
- This would enable follow-up questions: "Where was I?" → "At the office" → "How long?" → "4 hours"
- Requires modern devices with sufficient RAM (~4GB+ available)

### 6.5 Time Expression Parser

This is the most critical NLP component. Must handle:

```
ABSOLUTE:
- "January 15th" / "15/1" / "15.1.2026" / "2026-01-15"
- "January 15th at 3pm" / "15/1 בשלוש"

RELATIVE:
- "yesterday" / "אתמול"
- "today" / "היום"
- "2 days ago" / "לפני יומיים"
- "last week" / "שבוע שעבר"
- "last month" / "חודש שעבר"
- "last Friday" / "שישי שעבר"
- "this morning" / "הבוקר"
- "3 hours ago" / "לפני 3 שעות"

TIME OF DAY MODIFIERS:
- "morning" → 06:00-12:00 / "בוקר"
- "afternoon" → 12:00-18:00 / "צהריים" / "אחר הצהריים"
- "evening" → 18:00-22:00 / "ערב"
- "night" → 22:00-06:00 / "לילה"
- "at 3pm" → 15:00-15:59

RANGES:
- "between 2pm and 5pm"
- "from Monday to Wednesday"
- "all of last week"
- "this whole month"
```

### 6.6 Response Generation

Responses are generated using templates with dynamic data insertion:

```kotlin
// shared/domain/query/ResponseGenerator.kt

// LOCATION_QUERY response:
"At [TIME], you were at [PLACE_NAME] ([ADDRESS]).
You arrived at [ARRIVAL_TIME] and left at [DEPARTURE_TIME] ([DURATION])."

// ACTIVITY_QUERY response:
"Yesterday afternoon (12:00-18:00):
You were at [PLACE] from 12:00-14:30.
You then drove to [PLACE2] (arrived 15:10).
You took [STEPS] steps and spent [SCREEN_TIME] on your phone.
Most used app: [APP_NAME] ([DURATION])."

// PATTERN_QUERY response:
"Over the last [PERIOD], you typically wake up at [AVG_TIME] (±[STD_DEV] min).
Earliest: [MIN] on [DAY]. Latest: [MAX] on [DAY]."

// PROOF_QUERY response:
"Evidence for your location on [DATE]:
✓ GPS: [COORDS] (accuracy: [X]m) — [N] readings
✓ WiFi: Connected to [SSID] — [N] connection events
✓ Activity: [STATE] detected — consistent with being at [PLACE]
✓ App usage: [APPS_USED] — [N] sessions
Confidence: [SCORE]% — based on [N] independent data sources"
```

---

## 7. Feature Specifications

### 7.1 Main Screen — The Search Interface

The app opens directly to the search screen. No onboarding walls, no tutorials (unless first launch). The philosophy: **the search bar is the app.**

```
┌─────────────────────────────────┐
│  🔍 Ask anything about your day │  ← Search bar (always focused on app open)
├─────────────────────────────────┤
│                                 │
│  Quick Actions:                 │
│  ┌─────────┐ ┌──────────────┐  │
│  │ Today's  │ │  Where was   │  │
│  │ Summary  │ │  I at...?    │  │
│  └─────────┘ └──────────────┘  │
│  ┌─────────┐ ┌──────────────┐  │
│  │ Weekly   │ │  My Patterns │  │
│  │ Report   │ │              │  │
│  └─────────┘ └──────────────┘  │
│                                 │
│  Recent Queries:                │
│  • "Where was I yesterday       │
│     afternoon?"                 │
│  • "How many steps this week?"  │
│  • "What time did I leave       │
│     work on Monday?"            │
│                                 │
│  ─── Recording Status ───       │
│  ● Active · 7 collectors · 2.3  │
│    MB today                     │
│                                 │
└─────────────────────────────────┘
```

**Search interaction flow:**
1. User types or speaks a query
2. Loading animation: "Searching your black box..."
3. Response appears as a card with formatted answer
4. Below the card: "View raw data" expandable section showing the actual records used
5. Below that: suggested follow-up queries

### 7.2 Timeline Screen

A scrollable, zoomable visual timeline of the user's day/week/month.

```
┌─────────────────────────────────┐
│  ◀  February 14, 2026  ▶       │  ← Date picker (swipe to change days)
├─────────────────────────────────┤
│  [Day] [Week] [Month]          │  ← Zoom level tabs
├─────────────────────────────────┤
│                                 │
│  07:15 ☀️ Woke up               │
│  ────────────────────           │
│  07:30 🏠 At Home               │
│    │  Screen: 12 min            │
│    │  Apps: WhatsApp, News      │
│  ────────────────────           │
│  08:45 🚗 Driving               │
│    │  Duration: 35 min          │
│    │  Distance: 18 km           │
│  ────────────────────           │
│  09:20 🏢 Arrived at Office     │
│    │  WiFi: BankCorp-5G         │
│    │  Screen: 4h 20min          │
│    │  Top app: Android Studio   │
│    │  Steps: 1,240              │
│  ────────────────────           │
│  13:05 🍽️ Left Office           │
│    │  Drove 5 min               │
│  ────────────────────           │
│  13:10 📍 Restaurant (Aroma)    │
│    │  Duration: 45 min          │
│  ────────────────────           │
│  13:55 🏢 Back at Office        │
│  ...                            │
│                                 │
└─────────────────────────────────┘
```

**Timeline generation algorithm:**
1. Group location records by proximity (cluster points within 100m)
2. Match clusters to KnownPlaces
3. Detect transitions between clusters → ARRIVED/DEPARTED events
4. Merge with activity data → enrich each cluster with what happened there
5. Add screen state, app usage, and step data as sub-items
6. Derive travel segments between location clusters

### 7.3 Map Screen

An optional map view showing location history.

```
┌─────────────────────────────────┐
│  Map · February 14              │
├─────────────────────────────────┤
│                                 │
│     ┌───────────────────┐       │
│     │                   │       │
│     │     MAP VIEW      │       │
│     │                   │       │
│     │  📍 Home          │       │
│     │    ╲              │       │
│     │     ╲ 🚗          │       │
│     │      ╲            │       │
│     │  📍 Office         │       │
│     │    ╲              │       │
│     │  📍 Restaurant    │       │
│     │                   │       │
│     └───────────────────┘       │
│                                 │
│  Visited today:                 │
│  • Home (7h 15m)               │
│  • Office (6h 40m)             │
│  • Aroma Espresso (45m)        │
│                                 │
└─────────────────────────────────┘
```

**Implementation:**
- Use OSMDroid (OpenStreetMap) for offline maps — no Google dependency, truly offline
- Pre-download map tiles for user's common areas
- Draw polylines between location points colored by transport mode
- Tap a location point to see all data captured at that time

### 7.4 Patterns & Insights Screen

Auto-generated insights from accumulated data.

**Insights the app can derive:**

**Sleep Patterns:**
- Estimated bedtime and wake time (from screen off + still + dark + home)
- Sleep duration trends
- Weekend vs weekday sleep difference
- "You've been going to bed 30 minutes later each week"

**Movement Patterns:**
- Daily step counts with weekly/monthly trends
- "You walk 40% less on Wednesdays"
- Average commute time and variability
- "Your commute was 15 minutes longer than usual today"

**Location Patterns:**
- Time spent at home vs office vs other
- "You've spent 12% more time at home this month vs last month"
- New places visited this week/month
- Routine deviation alerts: "You usually go to the gym on Tuesdays but haven't gone in 3 weeks"

**Screen Patterns:**
- Daily screen time trends
- First pickup time trends
- Most used apps by week
- "Your social media usage increased 25% this week"

**Compound Insights (cross-collector):**
- "On days you walk more than 8,000 steps, you go to bed 20 minutes earlier"
- "You spend more time on social media on days you don't go to the office"
- "Your phone usage spikes between 10pm-midnight on weekdays"

### 7.5 Settings & Privacy Control Center

```
┌─────────────────────────────────┐
│  ⚙️ Settings                     │
├─────────────────────────────────┤
│                                 │
│  DATA COLLECTION                │
│  ─────────────                  │
│  📍 Location           [✓ ON]   │
│     Interval: ● 5min ○ 15min   │
│                                 │
│  🏃 Activity & Motion  [✓ ON]   │
│                                 │
│  📶 WiFi Environment   [✓ ON]   │
│                                 │
│  📱 App Usage          [✓ ON]   │
│                                 │
│  🔆 Screen State       [✓ ON]   │
│                                 │
│  🔊 Ambient Audio Level[○ OFF]  │
│     ⓘ Only measures volume,     │
│       never records audio       │
│                                 │
│  🔋 Battery & Charging [✓ ON]   │
│                                 │
│  🌐 Connectivity       [✓ ON]   │
│                                 │
│  🌡️ Barometric Pressure[○ OFF]  │
│                                 │
│  💡 Light Sensor       [○ OFF]  │
│                                 │
│  KNOWN PLACES                   │
│  ────────────                   │
│  🏠 Home — [Set / Change]       │
│  🏢 Work — [Set / Change]       │
│  + Add custom place             │
│                                 │
│  DATA MANAGEMENT                │
│  ───────────────                │
│  Retention period: [1 year ▾]   │
│  Storage used: 14.3 MB          │
│  Total records: 127,450         │
│  [Export All Data as JSON]      │
│  [Delete All Data]              │
│  [Delete Date Range...]         │
│                                 │
│  BATTERY OPTIMIZATION           │
│  ─────────────────────          │
│  Profile: ● Balanced            │
│           ○ Maximum recording    │
│           ○ Battery saver        │
│                                 │
│  LANGUAGE                       │
│  ────────                       │
│  Query language: [Auto-detect ▾]│
│    ○ English                    │
│    ○ עברית                      │
│    ● Auto-detect                │
│                                 │
│  ABOUT                          │
│  ─────                          │
│  BlackBox v1.0                  │
│  [Privacy Policy]               │
│  [How Your Data Is Protected]   │
│                                 │
└─────────────────────────────────┘
```

### 7.6 First Launch Experience

Minimal, respectful onboarding:

**Screen 1:** App logo + "BlackBox records the context of your life — where you go, what you do, how your day flows. Everything stays on your phone. No accounts, no cloud, no tracking."

**Screen 2:** "Choose what to record" — shows all collectors with toggles, pre-selected based on a "Recommended" preset. Three preset buttons at top: "Essential" (location + activity + screen), "Balanced" (all high + medium priority), "Maximum" (everything on).

**Screen 3:** Permission requests — explained one by one with clear reasons:
- Location: "To know where you are and where you've been"
- Activity Recognition: "To detect walking, driving, and stillness"
- Usage Stats: "To know which apps you're using (not what you do in them)"
- (Optional) Microphone: "To measure ambient noise levels only — no audio is ever recorded or stored"

**Screen 4:** "Set your home location" — map picker. Optional but enables sleep detection and "time at home" insights.

**Screen 5:** "You're all set. BlackBox is now recording. Forget about it — it's there when you need it."
→ Opens directly to search screen.

### 7.7 Proof Report Generator

When a user asks a PROOF_QUERY, the app generates a comprehensive evidence report.

**Report contents:**
- Date and time range
- For each collector that has data in the range:
  - Summary of what was recorded
  - Number of independent data points
  - Confidence level
- Cross-collector corroboration score
- Visual timeline of the period
- Map with location points
- Option to export as PDF

**Corroboration scoring:**
- 1 collector confirming = Low confidence (60%)
- 2 collectors confirming = Medium confidence (80%)
- 3+ collectors confirming = High confidence (95%)
- Contradictory data between collectors = flagged with explanation

### 7.8 Smart Notifications (Optional, Off by Default)

Users can opt in to receive intelligent notifications:

- **Daily summary:** "Your day: 8,421 steps, 6h at office, 2h screen time, bed at 11:30pm"
- **Routine breaks:** "You usually go to the gym on Tuesday but haven't been in 2 weeks"
- **Records milestone:** "BlackBox has recorded 100 days of your life"
- **Anomaly alerts:** "Unusual: your phone was used at 3am for 15 minutes"

---

## 8. Security & Privacy Architecture

### 8.1 Encryption

**Database encryption:**
- Use SQLCipher for SQLDelight database encryption
- 256-bit AES encryption for the entire database
- Encryption key derived from a key stored in Android Keystore (hardware-backed)
- Database is unreadable without the device's hardware security module

**Key management:**
```kotlin
// androidApp/security/KeyManager.kt
class AndroidKeyManager : KeyManager {
    // Generate a master key in Android Keystore
    // Key is hardware-backed (StrongBox when available)
    // Key is non-exportable — cannot leave the secure hardware
    // Use this to derive the SQLCipher database key
}
```

### 8.2 Data Access Protection

- App requires device authentication (PIN/fingerprint/face) to open
- Uses BiometricPrompt API on Android
- Auto-locks after 1 minute in background
- No data is accessible via `ContentProvider` or any IPC mechanism
- `android:exported="false"` on all components

### 8.3 Export Security

- Exported data (JSON/PDF) is encrypted with a user-chosen password
- Uses AES-256-GCM with PBKDF2 key derivation
- Export file contains a hash of its contents for integrity verification

### 8.4 Anti-Tampering

- Each day's data is signed with the device key
- Daily hash chain: each DailySummary includes the hash of the previous day's summary
- If any day's data is tampered with, the chain breaks — detectable during proof verification

---

## 9. Battery Optimization Strategy

Battery life is the #1 risk for this type of app. The strategy:

### 9.1 Adaptive Collection Engine

```kotlin
// shared/domain/CollectionOrchestrator.kt
class CollectionOrchestrator {

    fun determineCollectionProfile(): CollectionProfile {
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()
        val isStationary = isDeviceStationary()
        val userProfile = getUserBatteryPreference()

        return when {
            isCharging -> CollectionProfile.MAXIMUM  // collect everything, charging
            batteryLevel > 50 -> CollectionProfile.NORMAL
            batteryLevel > 20 -> CollectionProfile.REDUCED
            batteryLevel > 10 -> CollectionProfile.MINIMAL
            else -> CollectionProfile.CRITICAL  // only event-driven, no polling
        }
    }
}
```

### 9.2 Collection Profiles

| Profile | Location | Activity | WiFi | App Usage | Screen | Audio | Sensors |
|---------|----------|----------|------|-----------|--------|-------|---------|
| MAXIMUM | 2 min | Continuous | 5 min | 2 min | Events | 5 min | 5 min |
| NORMAL | 5 min | Transitions | 15 min | 5 min | Events | 15 min | 10 min |
| REDUCED | 15 min | Transitions | 30 min | 10 min | Events | OFF | 30 min |
| MINIMAL | Significant change only | Transitions | OFF | 15 min | Events | OFF | OFF |
| CRITICAL | OFF | OFF | OFF | OFF | Events | OFF | OFF |

### 9.3 Smart Optimizations

- **Stationary detection:** If device hasn't moved for 30 min, reduce location polling to every 30 min
- **Batch collection:** Group sensor readings into a single wake-up cycle
- **Event-driven over polling:** Use system callbacks wherever possible (screen on/off, WiFi connected, activity transitions)
- **Coalesce alarms:** Use `setInexactRepeating` and `WorkManager` to let Android batch our wakeups with other apps
- **Night mode:** Between estimated sleep time and wake time, reduce all collection to minimum

### 9.4 Battery Impact Target

**Target: < 3% daily battery consumption** with NORMAL profile.

For reference:
- Google Maps timeline (always-on location): ~5-8% daily
- Facebook (background): ~3-5% daily
- WhatsApp (background): ~1-2% daily

BlackBox should be in the 2-3% range because we're collecting metadata, not streaming data.

---

## 10. Development Roadmap

### Phase 1: Foundation (Weeks 1-3)
**Goal: Basic recording works, data goes into DB**

- [ ] Set up KMP project structure (shared + androidApp + iosApp stubs)
- [ ] Implement SQLDelight schema and database setup
- [ ] Create `DataCollector` interface and base infrastructure
- [ ] Implement LocationCollector (Android)
- [ ] Implement ActivityCollector (Android)
- [ ] Implement ScreenStateCollector (Android)
- [ ] Create Foreground Service to orchestrate collection
- [ ] Basic notification showing recording status
- [ ] Implement CollectionOrchestrator with battery profiles
- [ ] Verify data is being written correctly

### Phase 2: Core UI (Weeks 4-6)
**Goal: User can see their data**

- [ ] Search screen (UI only, static results)
- [ ] Timeline screen — basic day view showing events chronologically
- [ ] Settings screen with collector toggles
- [ ] Implement remaining collectors: WiFi, AppUsage, Battery, Connectivity
- [ ] DailySummary generation job (end-of-day aggregation)
- [ ] KnownPlace auto-detection (cluster frequent locations)
- [ ] DerivedEvent generation (arrival/departure detection)
- [ ] First-launch onboarding flow

### Phase 3: Query Engine (Weeks 7-9)
**Goal: Natural language search works**

- [ ] Implement TimeExpressionParser (English + Hebrew)
- [ ] Implement RuleBasedQueryParser (intent classification)
- [ ] Implement QueryBuilder (intent → SQL)
- [ ] Implement ResponseGenerator (templates)
- [ ] Wire search UI to query engine
- [ ] Add "View raw data" expansion in results
- [ ] Add suggested follow-up queries
- [ ] Test with ~50 real-world queries, iterate

### Phase 4: Map & Insights (Weeks 10-12)
**Goal: Visual timeline and patterns**

- [ ] Integrate OSMDroid for offline map
- [ ] Map screen showing daily location path
- [ ] Patterns screen with basic insights (sleep, steps, screen time trends)
- [ ] Implement compound insights (cross-collector correlations)
- [ ] Proof report generator
- [ ] Export functionality (JSON + PDF)

### Phase 5: Polish & Security (Weeks 13-14)
**Goal: Production-ready**

- [ ] SQLCipher encryption integration
- [ ] Biometric lock
- [ ] Hash chain implementation for daily summaries
- [ ] Data retention auto-cleanup
- [ ] Battery optimization tuning
- [ ] Edge case handling and error recovery
- [ ] UI polish and animations

### Phase 6: Advanced (Post-MVP, Optional)
- [ ] On-device ML model for smarter query understanding
- [ ] Conversational follow-up queries
- [ ] Widget showing today's summary
- [ ] Wear OS companion (for activity data enrichment)
- [ ] iOS implementation
- [ ] Ambient audio level collector
- [ ] Barometric and light collectors

---

## 11. Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin (shared + Android), Swift (iOS future) |
| KMP Framework | Kotlin Multiplatform |
| UI (Android) | Jetpack Compose + Material 3 |
| UI (iOS future) | SwiftUI |
| Database | SQLDelight + SQLCipher |
| DI | Koin (multiplatform) |
| Async | Kotlin Coroutines + Flow |
| Location | Google FusedLocationProvider |
| Activity | Google Activity Recognition API |
| Maps | OSMDroid (OpenStreetMap) — offline capable |
| NLP (Phase 1) | Custom rule-based parser (Kotlin) |
| NLP (Phase 2) | ONNX Runtime for Android |
| Encryption | SQLCipher + Android Keystore |
| Background Work | Foreground Service + WorkManager |
| Sensors | Android SensorManager |
| Testing | JUnit + Turbine (Flow testing) + MockK |
| Build | Gradle with KMP plugin |

---

## 12. Permissions Required (Android)

| Permission | Required For | User-Facing Explanation |
|-----------|-------------|----------------------|
| `ACCESS_FINE_LOCATION` | GPS coordinates | Know where you are |
| `ACCESS_BACKGROUND_LOCATION` | Location when app not open | Record location throughout the day |
| `ACTIVITY_RECOGNITION` | Motion detection | Detect walking, driving, stillness |
| `FOREGROUND_SERVICE` | Background recording | Keep recording running |
| `FOREGROUND_SERVICE_LOCATION` | Location service type | Required for background location |
| `PACKAGE_USAGE_STATS` | App usage tracking | Know which apps you use (not what you do in them) |
| `RECEIVE_BOOT_COMPLETED` | Restart after reboot | Resume recording after phone restart |
| `ACCESS_WIFI_STATE` | WiFi scanning | Identify your location by WiFi networks |
| `BLUETOOTH_CONNECT` | Connected BT devices | Detect car/headset connections |
| `RECORD_AUDIO` | Ambient noise level only | Measure noise levels (optional, no recording stored) |
| `USE_BIOMETRIC` | App lock | Protect your data with fingerprint/face |
| `POST_NOTIFICATIONS` | Status notification | Show recording status |
| `WAKE_LOCK` | Keep collection running | Prevent collection from being killed |

---

## 13. Known Challenges & Mitigations

### 13.1 Android Background Restrictions
**Problem:** Android aggressively kills background services to save battery.
**Mitigation:**
- Use a proper Foreground Service with a persistent notification
- Request battery optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
- Use `WorkManager` as a backup to restart collection if service is killed
- Use `RECEIVE_BOOT_COMPLETED` to restart after device reboot
- On manufacturer-specific ROMs (Xiaomi, Samsung, Huawei), guide users to disable battery optimization for BlackBox specifically

### 13.2 Location Accuracy Indoors
**Problem:** GPS is inaccurate indoors.
**Mitigation:**
- Primarily use WiFi-based location indoors (FusedLocationProvider handles this)
- Use WiFi BSSID fingerprinting for more precise indoor positioning
- Use barometric pressure for floor detection
- Accept that indoor accuracy is "building-level" not "room-level" in the POC

### 13.3 iOS Background Limitations (Future)
**Problem:** iOS severely restricts background execution.
**Mitigation (for future iOS version):**
- Use `CLLocationManager` with `significantLocationChange` (battery efficient, always works)
- Use `CMMotionActivityManager` for activity (background capable)
- Use `BGTaskScheduler` for periodic data collection
- Accept that iOS version will have fewer data points than Android
- This is why POC is Android-first — Android gives us more freedom

### 13.4 User Privacy Perception
**Problem:** Users may be uncomfortable with the idea of being "recorded."
**Mitigation:**
- Emphasize "metadata only" — show concrete examples of what IS and ISN'T captured
- Default sensitive collectors (audio level) to OFF
- Show exactly what data exists via "View raw data" in every query result
- Provide easy "delete range" and "delete all" options
- Privacy-first onboarding that builds trust before asking for permissions

### 13.5 Storage on Old Devices
**Problem:** Some older devices have limited storage.
**Mitigation:**
- ~50MB/year is negligible even on 16GB devices
- Configurable retention period (default 1 year, can reduce to 3 months)
- Show storage usage prominently in settings
- Smart cleanup: compress old raw records, keep only daily summaries for data older than retention period

---

## 14. Success Metrics

For portfolio demonstration purposes:

- [ ] Records 24 hours of complete life data without user interaction
- [ ] Answers 10 different natural language queries correctly
- [ ] Generates accurate daily timeline with location, activity, and app usage
- [ ] Proof report correctly corroborates location claims with multiple data sources
- [ ] Battery consumption under 3% daily in NORMAL profile
- [ ] Database handles 6 months of simulated data without performance degradation
- [ ] Complete offline operation — works in airplane mode
- [ ] Biometric-locked, encrypted database passes security review
- [ ] KMP shared module contains 70%+ of total code

---

## 15. File Structure for AI-Assisted Development

The following MD files should be placed in the repository root for AI agents:

```
blackbox/
├── docs/
│   ├── SPEC.md                  # This document
│   ├── ARCHITECTURE.md          # Detailed module architecture & dependency graph
│   ├── DATABASE.md              # Complete SQLDelight schema with sample queries
│   ├── QUERY_ENGINE.md          # NLP parser rules, intent patterns, response templates
│   ├── COLLECTORS.md            # Each collector's detailed implementation guide
│   ├── SECURITY.md              # Encryption, keystore, hash chain implementation
│   ├── BATTERY.md               # Optimization strategies and profiling guide
│   └── UI_SCREENS.md            # Compose screen specifications with state management
├── CLAUDE.md                    # Instructions for AI agents working on this codebase
├── CONVENTIONS.md               # Coding conventions, naming, patterns
└── TODO.md                      # Current development status and next tasks
```

**CLAUDE.md should contain:**
- Project overview (1 paragraph)
- Tech stack summary
- How to build and run
- Module responsibility boundaries
- Testing expectations
- Link to relevant spec files for each area of work
