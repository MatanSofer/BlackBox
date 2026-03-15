# DATA_FLOW.md — Sensor to Screen Data Flow

How raw sensor readings travel from Android hardware all the way to your Compose UI.

---

## Overview

```
Android Sensor / System API
        │
        ▼
  Platform Collector          (androidApp/collector/)
        │  CollectedRecord
        ▼
  CollectorOrchestrator       (androidApp/collector/base/)
        │  CollectedRecord
        ▼
  SaveRecordUseCase           (shared/domain/usecase/record/)
        │  SQL INSERT
        ▼
  BlackBoxDatabase (SQLCipher) (shared/data/database/)
        │
        ▼
  Repository (query)          (shared/data/repository/)
        │  domain model
        ▼
  UseCase (business logic)    (shared/domain/usecase/)
        │  screen model
        ▼
  ViewModel (MVI state)       (shared/ui/{screen}/)
        │  StateFlow<State>
        ▼
  Compose Content             (shared/ui/{screen}/XxxContent.kt)
```

---

## Stage 1 — Sensor Reading (Platform Layer)

Each collector extends `BaseCollector` and emits `CollectedRecord` objects.

### Location (GPS)

```
FusedLocationProviderClient
    onLocationResult(LocationResult)
        │
        ▼  LocationRecord(lat, lon, accuracy, altitude, speed, bearing)
        ▼  wrapped in RecordData.Location
        ▼  wrapped in CollectedRecord(timestamp, CollectorType.LOCATION, data)
    emitRecord(record)
```

**Key files:**
- `androidApp/collector/LocationCollector.kt`
- `shared/domain/model/record/LocationData.kt`

### Activity Recognition

```
ActivityRecognitionClient  (transition API)
    onReceive(ActivityTransitionResult)
        │
        ▼  ActivityData(type=WALKING, confidence=90, isEntering=true)
        ▼  wrapped in CollectedRecord(timestamp, ACTIVITY, data)
    emitRecord(record)
```

Step counter runs in parallel on a 1-minute interval — reading `SensorEvent` from `TYPE_STEP_COUNTER` and computing delta from previous snapshot.

### WiFi

```
WifiManager
    connectionInfo  → WifiInfo  → connectedSsid, connectedBssid, signalStrength
    scanResults     → List<ScanResult> → nearbyNetworks[(ssid, bssid, level)]
        │
        ▼  WifiData(connectedSsid, connectedBssid, nearbyNetworks, signalStrength)
        ▼  wrapped in CollectedRecord(timestamp, WIFI, data)
    emitRecord(record)   [every 5 minutes]
```

### Screen State

```
BroadcastReceiver
    ACTION_SCREEN_ON  → ScreenStateData(isOn=true)
    ACTION_SCREEN_OFF → ScreenStateData(isOn=false)
        │
        ▼  CollectedRecord(timestamp, SCREEN_STATE, data)
    emitRecord(record)
```

### Call Log

```
ContentResolver.query(CallLog.Calls.CONTENT_URI)
    for each call newer than last seen timestamp:
        CallLogData(number, callType, durationSeconds, ...)
        ▼  CollectedRecord(timestamp, CALL_LOG, data)
    emitRecord(record)   [every 2 minutes]
```

On first run, looks back 30 days to import history.

### App Usage

```
UsageStatsManager.queryUsageStats(INTERVAL_BEST, start, end)
    for each UsageStats:
        AppUsageData(packageName, appName, foregroundMs, lastUsed)
        ▼  CollectedRecord(timestamp, APP_USAGE, data)
    emitRecord(record)   [every 15 minutes]
```

---

## Stage 2 — Batching and Persistence

### CollectorOrchestrator

All collectors are managed by `CollectorOrchestrator`. Each emits to its own `Flow<CollectedRecord>`. The orchestrator merges all flows and routes each record to `SaveRecordUseCase`.

```kotlin
// Simplified merge logic
merge(
    locationCollector.records,
    activityCollector.records,
    wifiCollector.records,
    screenStateCollector.records,
    appUsageCollector.records,
    callLogCollector.records,
    ...
).collect { record ->
    saveRecordUseCase(record)
}
```

### SaveRecordUseCase

```kotlin
suspend operator fun invoke(record: CollectedRecord): Result<Long> {
    return runCatching {
        val json = serializer.encode(record.data)  // RecordData → JSON string
        recordRepository.saveRecord(record, json)   // INSERT into DB
    }
}
```

**What happens at save:**
1. `RecordData` sealed class is serialized to a JSON string (using `kotlinx.serialization`)
2. Flat fields extracted: `collectorType`, `timestamp`
3. SQLDelight `INSERT` runs on `Dispatchers.IO`
4. Returns the generated row ID

### BlackBoxDatabase Schema (relevant tables)

```sql
CREATE TABLE collected_record (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    timestamp   INTEGER NOT NULL,
    type        TEXT    NOT NULL,   -- CollectorType name
    data_json   TEXT    NOT NULL    -- serialized RecordData
);

CREATE INDEX idx_record_type_time ON collected_record(type, timestamp);

CREATE TABLE location_record (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    timestamp   INTEGER NOT NULL,
    latitude    REAL    NOT NULL,
    longitude   REAL    NOT NULL,
    accuracy    REAL    NOT NULL,
    altitude    REAL,
    speed       REAL,
    bearing     REAL
);

CREATE TABLE daily_summary (
    date_str    TEXT    NOT NULL PRIMARY KEY,
    step_count  INTEGER NOT NULL,
    ...
);
```

---

## Stage 3 — Data Retrieval (Repository Layer)

Repositories are the only code that touches SQL. They return domain objects, never raw DB rows.

### RecordRepository

```kotlin
interface RecordRepository {
    suspend fun getRecordsByTypeInRange(
        type: CollectorType,
        start: Long,
        end: Long
    ): List<CollectedRecord>

    suspend fun getLatestRecord(type: CollectorType): CollectedRecord?
}
```

**Implementation flow:**

```
RecordRepositoryImpl.getRecordsByTypeInRange(LOCATION, t1, t2)
    │
    ▼  database.blackBoxDatabaseQueries
         .getRecordsByTypeInRange(type.name, t1, t2)
         .executeAsList()                    // List<Collected_record>  (DB row)
    │
    ▼  RecordMapper.toDomain(dbRow)          // DB row → CollectedRecord
         parse dbRow.data_json → RecordData  // JSON → sealed class
         return CollectedRecord(timestamp, type, data)
```

### LocationRepository

Location records have their own dedicated table for efficient geospatial queries.

```
LocationRepositoryImpl.getLocationsInRange(t1, t2)
    │
    ▼  database.blackBoxDatabaseQueries
         .getLocationRecordsInRange(t1, t2)
         .executeAsList()
    │
    ▼  LocationMapper.toDomain(dbRow)
         return LocationRecord(lat, lon, accuracy, ...)
```

---

## Stage 4 — Business Logic (Use Case Layer)

Use cases combine data from multiple repositories and apply algorithms.

### Timeline Use Case — Full Flow

```
GetTimelineUseCase(startTime, endTime)
    │
    ├── locationRepository.getLocationsInRange(start, end)  → List<LocationRecord>
    ├── recordRepository.getRecordsByTypeInRange(ACTIVITY)  → List<CollectedRecord>
    ├── recordRepository.getRecordsByTypeInRange(WIFI)      → List<CollectedRecord>
    ├── recordRepository.getRecordsByTypeInRange(CALL_LOG)  → List<CollectedRecord>
    │
    ├── buildLocationEntries(locations, wifiRecords)
    │       group locations into "stays" (same place > 5 min)
    │       for each stay:
    │           try GPS → placeRepository.findNearestPlace(lat, lon)
    │           fallback WiFi → placeRepository.findPlaceByWifiBssid(bssid)
    │           emit TimelineEntry(type=LOCATION_STAY, title=placeName|"Unknown")
    │
    ├── buildActivityEntries(activityRecords)
    │       deduplicate / filter short events
    │       emit TimelineEntry(type=ACTIVITY, title="Walking · 12 min")
    │
    ├── buildCallEntries(callRecords)
    │       for each call:
    │           emit TimelineEntry(type=EVENT, title="Incoming call · 3m 20s")
    │
    └── merge all entries, sort by timestamp
        return List<TimelineEntry>
```

### Insights Use Case — Full Flow

```
GetInsightsBriefUseCase()
    │
    ├── locationRepository.getLocationsInRange(today)
    ├── recordRepository.getRecordsByTypeInRange(SCREEN_STATE, today)
    ├── recordRepository.getRecordsByTypeInRange(APP_USAGE, today)
    ├── recordRepository.getRecordsByTypeInRange(ACTIVITY, today)
    ├── placeRepository.getAllPlaces()
    │
    ├── compute todayStepCount    (sum step deltas from ACTIVITY records)
    ├── compute todayScreenTime   (sum ON→OFF intervals from SCREEN_STATE records)
    ├── compute todayTopApps      (aggregate APP_USAGE by package, sort by totalMinutes)
    ├── detect todayTopPlace      (most time spent at a known place today)
    │
    └── return InsightsBrief(stepCount, screenTime, topApps, topPlace, ...)
```

---

## Stage 5 — ViewModel (MVI Layer)

ViewModels observe use case results and translate them into screen-specific `State`.

### Sequence Diagram — Timeline Screen Load

```
User opens Timeline tab
        │
        ▼
TimelineScreen
    LaunchedEffect(selectedDate) {
        viewModel.onAction(Action.DateSelected(today))
    }
        │
        ▼
TimelineViewModel.handleDateSelected(date)
    _state.update { it.copy(isLoading = true) }
    viewModelScope.launch {
        getTimelineUseCase(date.startMs, date.endMs)
            .onSuccess { entries ->
                _state.update { it.copy(
                    isLoading = false,
                    entries = entries,
                    selectedDate = date
                ) }
            }
            .onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.toUiError()) }
            }
    }
        │
        ▼
StateFlow<TimelineContract.State> emits new value
        │
        ▼
TimelineContent recomposes
    shows list of TimelineEntryCard(entry) for each entry
```

### Sequence Diagram — Search Query

```
User types "where was I yesterday?" and hits Search
        │
        ▼
SearchContent
    onAction(Action.QueryChanged("where was I yesterday?"))
    onAction(Action.SubmitQuery)
        │
        ▼
SearchViewModel.handleSubmitQuery()
    _state.update { isLoading = true }
    processQueryUseCase("where was I yesterday?")
        │  QueryEngine.parse(text)
        ▼
    TimeExpressionParser   → TimeRange(yesterday 00:00 → 23:59)
    IntentClassifier       → QueryIntent.LOCATION
    EntityExtractor        → entities = []
        │
        ▼
    QueryBuilder.build(parsedQuery) → selects appropriate repositories
    locationRepository.getLocationsInRange(yesterday)
    placeRepository.getAllPlaces()
        │
        ▼
    ResponseGenerator.generate(data) → "Yesterday you visited: Home (3h), Office (6h), ..."
        │
        ▼
    QueryResult(parsedQuery, rawData, humanResponse)
        │
        ▼
    _state.update { isLoading = false, result = queryResult }
        │
        ▼
SearchContent recomposes — shows QueryResultCard
```

---

## Stage 6 — Compose Rendering

```
StateFlow<State>
    │  collectAsStateWithLifecycle()
    ▼
State object (immutable data class)
    │  passed as parameter
    ▼
XxxContent(state, onAction)
    │  Compose reads only fields it uses
    ▼
Compose diff algorithm (smart recomposition)
    │  only recomposes scopes that read changed fields
    ▼
Rendered pixels on screen
```

**Important:** Compose only recomposes the parts of the tree that read a changed field. If `state.isLoading` changes but a composable only reads `state.entries`, that composable does NOT recompose. This is why the single-state-object MVI pattern is efficient.

---

## Data Flow for Background Collection

The service runs even when the app is closed.

```
Device Boot
    │
    ▼
BootReceiver.onReceive(ACTION_BOOT_COMPLETED)
    │  startForegroundService(BlackBoxServiceIntent)
    ▼
BlackBoxService.onCreate()
    │  starts foreground notification
    │  creates CollectorOrchestrator
    ▼
CollectorOrchestrator.start()
    │  starts all enabled collectors
    ▼
Each Collector (on its own coroutine / interval)
    │  emits CollectedRecord
    ▼
SaveRecordUseCase(record)
    │  INSERT to SQLCipher DB
    ▼
Data stored — ready for query when user opens app
```

**WorkManager jobs** (separate from service, scheduled daily):
```
DailySummaryWorker  [runs at 23:55]
    GenerateDailySummaryUseCase(today) → INSERT daily_summary row

CleanupWorker       [runs at 02:00]
    deletes records older than retention period (default: 90 days)
```

---

## Error Propagation

Errors flow upward as `Result<T>` failures, never as exceptions crossing layer boundaries.

```
SQLDelight throws SQLException
        │
        ▼
RecordRepositoryImpl.getRecordsByTypeInRange()
    runCatching { ... }
    .mapFailure { BlackBoxError.DatabaseError(it.message, it) }
        │
        ▼
GetTimelineUseCase
    repository call returns Result.failure(BlackBoxError.DatabaseError)
    use case returns Result.failure(same error)
        │
        ▼
TimelineViewModel.handleDateSelected()
    .onFailure { error ->
        _state.update { it.copy(error = error.toUiError()) }
        _events.emit(Event.ShowSnackbar(...))
    }
        │
        ▼
TimelineContent shows ErrorView composable
```

---

## Data Retention & Cleanup

```
CleanupWorker (daily at 02:00)
    │
    ▼
RetentionPolicy from SettingsRepository
    (default: 90 days = 7_776_000_000 ms)
        │
        ▼
cutoffTimestamp = now - retentionPeriodMs
        │
    ┌───┴────────────────────────┐
    ▼                            ▼
recordRepository              locationRepository
.deleteRecordsBefore          .deleteLocationsBefore
(cutoffTimestamp)             (cutoffTimestamp)
```

Old daily summaries are kept indefinitely (small size, high historical value).
