# ARCHITECTURE_DEEP_DIVE.md — Architecture Decisions and Layer Boundaries

A thorough explanation of every architectural choice in BlackBox, why it was made, and what the tradeoffs are.

---

## Core Architectural Principles

### 1. Clean Architecture

BlackBox follows Uncle Bob's Clean Architecture strictly. The key rule is the **Dependency Rule**: source code dependencies can only point inward — toward higher-level policy.

```
┌─────────────────────────────────────────────┐
│  UI (Compose)                               │  ← outermost
│  ┌─────────────────────────────────────┐    │
│  │  ViewModel (MVI)                    │    │
│  │  ┌───────────────────────────────┐  │    │
│  │  │  Use Cases                    │  │    │
│  │  │  ┌─────────────────────────┐  │  │    │
│  │  │  │  Domain Models +        │  │  │    │
│  │  │  │  Repository Interfaces  │  │  │    │
│  │  │  └─────────────────────────┘  │  │    │
│  │  └───────────────────────────────┘  │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
         Data Layer + Platform Layer
         (outermost, implements interfaces)
```

**Why:**
- Business logic (use cases, domain models) is pure Kotlin with no Android dependency. It can be unit tested on the JVM without an emulator.
- Swapping the database from SQLDelight to Room would only touch the data layer.
- Swapping GPS from FusedLocation to raw Android LocationManager would only touch the platform collector.
- All of this without touching a single line of business logic.

### 2. Kotlin Multiplatform (KMP)

The project is built with KMP so that ~70% of the code lives in the shared module and is reusable across platforms.

```
shared/src/
├── commonMain/     ← Pure Kotlin. Runs everywhere.
│   ├── domain/     ← Business logic, models, repository interfaces
│   ├── data/       ← SQLDelight implementations, mappers
│   └── ui/         ← Compose Multiplatform screens
│
├── androidMain/    ← Android-specific actual declarations
└── iosMain/        ← iOS stubs (future)

androidApp/
└── src/androidMain/ ← Android shell, collectors, service, DI
```

**Why KMP over pure Android:**
- Future iOS port will reuse all business logic and UI without rewriting.
- Forces clean layer separation (can't accidentally import `android.util.Log` in domain).
- `expect/actual` pattern makes platform differences explicit and testable.

**expect/actual pattern:**

```kotlin
// shared/src/commonMain/platform/DatabaseDriverFactory.kt
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

// shared/src/androidMain/platform/DatabaseDriverFactory.kt
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = BlackBoxDatabase.Schema,
            context = context,
            name = "blackbox.db",
            factory = SupportFactory(SQLiteDatabase.openOrCreateDatabase(...))
        )
    }
}

// shared/src/iosMain/platform/DatabaseDriverFactory.kt
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(BlackBoxDatabase.Schema, "blackbox.db")
    }
}
```

---

## MVI Pattern Deep Dive

### Why MVI (not MVVM or MVP)?

| Pattern | State | Problem |
|---------|-------|---------|
| MVP | Presenter calls view methods | Multiple state mutations → inconsistent UI |
| MVVM | Multiple LiveData/StateFlow | Race conditions, complex state merging |
| MVI | Single immutable state object | Predictable, debuggable, testable |

MVI's single state means: at any point in time, you can print `state.toString()` and fully understand what the UI is showing. You can also replay a sequence of actions to reproduce any bug.

### State, Action, Event — The Contract

```kotlin
// Contract defines the "language" of a screen

data class State(           // What the UI currently shows (persists)
    val isLoading: Boolean = false,
    val entries: List<TimelineEntry> = emptyList(),
    val selectedDate: LocalDate = LocalDate.today(),
    val error: UiError? = null
)

sealed interface Action {   // What the user can do (input)
    data class DateSelected(val date: LocalDate) : Action
    data object RefreshClicked : Action
    data class EntryClicked(val entry: TimelineEntry) : Action
}

sealed interface Event {    // One-time responses (navigation, toasts)
    data class ShowSnackbar(val message: UiText) : Event
    data class NavigateToMap(val date: LocalDate) : Event
}
```

**Why two output channels (State + Event)?**

`State` is persistent — if you rotate the screen, the ViewModel survives and the UI redraws from the current state. Events that live in state would re-trigger on recomposition (e.g., a snackbar would re-show every time anything re-renders).

`Event` is consumed once — it flows through `SharedFlow` which does NOT replay to new collectors. Navigation and toasts are perfect events.

### ViewModel as Pure State Machine

```kotlin
fun onAction(action: Action) = when (action) {
    is Action.DateSelected -> handleDateSelected(action.date)
    is Action.RefreshClicked -> handleRefresh()
    is Action.EntryClicked -> handleEntryClicked(action.entry)
}
```

The `onAction` function is the only entry point. This makes ViewModel testing trivial:

```kotlin
// Test: action → expected state
viewModel.onAction(Action.DateSelected(yesterday))
turbine.awaitItem()  // isLoading = true
turbine.awaitItem()  // isLoading = false, entries = [...]
```

---

## Module Dependency Graph

```
androidApp
    │  depends on
    ▼
shared (KMP library)
    │  depends on (gradle dependencies)
    ▼
SQLDelight runtime
Koin
Compose Multiplatform
kotlinx.serialization
kotlinx.datetime
kotlinx.coroutines
```

```
androidApp internal dependencies:

BlackBoxApplication
    └── startKoin { modules(...) }
            ├── appModule
            │     └── BlackBoxLogger, KeyManager, BiometricManager
            ├── databaseModule
            │     └── DatabaseDriverFactory → BlackBoxDatabase
            ├── repositoryModule
            │     └── 7 repository impls (inject DB)
            ├── useCaseModule
            │     └── 11 use cases (inject repositories)
            ├── collectorModule
            │     └── 10 collectors + CollectorOrchestrator
            └── viewModelModule
                  └── 6 ViewModels (inject use cases)
```

---

## Database Architecture

### Why SQLDelight?

| Option | Pros | Cons |
|--------|------|------|
| Room | Familiar, annotation-based | Android-only — breaks KMP |
| SQLDelight | KMP-native, type-safe SQL, multiplatform | Less familiar, SQL-first |
| Realm | Easy, offline-first | Commercial, heavy |
| SQLite raw | No magic | Error-prone, boilerplate |

SQLDelight generates Kotlin code from `.sq` files. Queries are compile-time checked — a typo in SQL becomes a build error, not a runtime crash.

### Why SQLCipher?

All data is encrypted at rest with AES-256. The encryption key is stored in Android Keystore (hardware-backed on modern devices). This means even if someone extracts the database file from the device, it is unreadable without the key — and the key never leaves the Keystore.

```
User unlocks device with biometrics
        │
        ▼
KeyManager.getOrCreateDatabaseKey()
    Android Keystore → AES-256-GCM key
        │
        ▼
SupportFactory(SQLiteDatabase.openOrCreateDatabase(path, key, ...))
        │
        ▼
SQLCipher decrypts pages on-the-fly as queries run
```

### Schema Design Philosophy

BlackBox uses two storage strategies:

**1. Generic record table** — for all sensor data:
```sql
CREATE TABLE collected_record (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    timestamp   INTEGER NOT NULL,
    type        TEXT    NOT NULL,
    data_json   TEXT    NOT NULL
);
```

Pros: one table for all 10+ collector types; adding a new collector requires zero schema changes.
Cons: no column-level queries on sensor data fields; JSON parsing overhead.

**2. Specialized tables** — for frequently queried data:
```sql
CREATE TABLE location_record ( lat, lon, accuracy, ... );
CREATE TABLE daily_summary ( date_str, step_count, screen_minutes, ... );
CREATE TABLE known_place ( id, name, lat, lon, radius_meters, ... );
```

Location records need geospatial range queries. Daily summaries are pre-aggregated for fast Insights rendering. Known places need to be loaded for every Timeline entry.

---

## Collector Architecture

### BaseCollector

All 10 collectors extend `BaseCollector`:

```kotlin
abstract class BaseCollector(
    protected val scope: CoroutineScope,
    protected val logger: BlackBoxLogger,
) {
    private val _records = MutableSharedFlow<CollectedRecord>(replay = 0, extraBufferCapacity = 64)
    val records: SharedFlow<CollectedRecord> = _records.asSharedFlow()

    abstract val collectorType: CollectorType
    abstract val interval: Long  // ms between polls (0 = event-driven)

    abstract suspend fun collect(): CollectedRecord?

    fun start() {
        if (interval > 0) {
            scope.launch {
                while (isActive) {
                    try {
                        collect()?.let { _records.emit(it) }
                    } catch (e: Exception) {
                        logger.e(TAG, "Collection error", e)
                    }
                    delay(interval)
                }
            }
        }
        // event-driven collectors register their own BroadcastReceiver or callback
    }
}
```

### Event-driven vs Poll-based Collectors

| Collector | Strategy | Why |
|-----------|----------|-----|
| LocationCollector | Event-driven (FusedLocation callback) | GPS wakes CPU rarely — push is more efficient |
| ActivityCollector | Event-driven (ActivityTransition API) | Only fires on transition, not every second |
| ScreenStateCollector | Event-driven (BroadcastReceiver) | Screen on/off is an event by nature |
| WifiCollector | Poll every 5 min | No push API for WiFi change |
| AppUsageCollector | Poll every 15 min | UsageStatsManager is query-based |
| BatteryCollector | Poll every 5 min | batteryChanged broadcast is too frequent |
| CallLogCollector | Poll every 2 min | ContentObserver has issues on some OEMs |
| AudioLevelCollector | Poll every 60 sec | Microphone access = battery + privacy cost |
| BarometerCollector | Poll every 30 sec | Pressure changes slowly |
| LightCollector | Poll every 60 sec | Ambient light changes slowly |

### CollectorOrchestrator

The orchestrator is responsible for:
1. Starting/stopping all collectors based on `CollectorSetting` (user can disable any)
2. Merging all collector flows into a single stream
3. Routing each `CollectedRecord` to `SaveRecordUseCase`
4. Handling collector errors without crashing the service

```kotlin
class CollectorOrchestrator(
    private val collectors: List<BaseCollector>,
    private val saveRecordUseCase: SaveRecordUseCase,
    private val settingsRepository: SettingsRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            val enabledTypes = settingsRepository.getEnabledCollectors()
            val enabledCollectors = collectors.filter { it.collectorType in enabledTypes }

            enabledCollectors.forEach { it.start() }

            merge(*enabledCollectors.map { it.records }.toTypedArray())
                .collect { record ->
                    saveRecordUseCase(record)
                }
        }
    }
}
```

---

## Query Engine Architecture

The NLP query engine is a custom rule-based pipeline — no ML, no cloud, fully offline.

### Pipeline Stages

```
Raw text: "where was I last tuesday evening?"
        │
        ▼
1. TimeExpressionParser
   Identifies: "last tuesday evening" → TimeRange(Tue 17:00 – 21:00)
        │
        ▼
2. IntentClassifier
   Keywords: "where", "was I" → QueryIntent.LOCATION
        │
        ▼
3. EntityExtractor
   Extracts: place names, activity types, app names, contact names
   Result: entities = []
        │
        ▼
4. QueryBuilder
   Intent = LOCATION → fetch location records + known places
   Builds: DataQuery(type=LOCATION, timeRange=..., entities=[])
        │
        ▼
5. Data Retrieval (repositories)
   locationRepository.getLocationsInRange(tue17, tue21)
   placeRepository.getAllPlaces()
        │
        ▼
6. ResponseGenerator
   Groups locations into stays, resolves place names
   Returns: "Tuesday evening: Home (5pm–7pm), you were walking near Office area from 7pm"
        │
        ▼
QueryResult(parsedQuery, rawData, humanResponse)
```

### Why Rule-Based?

| Approach | Pros | Cons |
|----------|------|------|
| Cloud NLP (GPT, etc.) | Better understanding | Requires internet, sends data to cloud, cost |
| On-device ML (TFLite) | Offline, private | Large model file (100MB+), slow inference |
| Rule-based parser | Zero latency, offline, tiny, private | Limited query variety |

BlackBox's privacy mandate (everything stays on-device) and the constrained query domain (questions about personal timeline data) make rule-based the right choice. The parser handles the ~50 most common query patterns and continues to improve.

### Bilingual Support (English + Hebrew)

The parser detects the language (script check: Hebrew Unicode range `\u0590-\u05FF`) and routes to `parseEnglish()` or `parseHebrew()`. Each language has its own regex patterns and word lists but shares the same `TimeRange` output type.

---

## Security Architecture

### Threat Model

| Threat | Mitigation |
|--------|-----------|
| Physical device theft | AES-256 SQLCipher, biometric lock |
| App data extraction (root) | Android Keystore key never exported |
| Cloud breach | No cloud — data never leaves device |
| Screenshot/screen recording | `FLAG_SECURE` on MainActivity |
| Clipboard leaks | No sensitive data copied to clipboard |
| Log leaks | Personal data never logged in production |

### Key Management

```
First launch:
    KeyManager.getOrCreateDatabaseKey()
    → KeyGenerator(AES, 256, AndroidKeyStore)
    → Key stored in hardware-backed Keystore
    → Key ID: "blackbox_db_key"
    → Key never extractable, tied to this device

Every launch:
    KeyManager.getOrCreateDatabaseKey()
    → KeyStore.getInstance("AndroidKeyStore")
    → .getKey("blackbox_db_key", null)
    → Returns key handle (not raw bytes)
    → Passed to SQLCipher SupportFactory
```

### Biometric Authentication

The app can be configured to require biometrics on open. The biometric gate uses `BiometricPrompt` with a `CryptoObject` that binds authentication to the database key — the key is only usable after successful authentication.

---

## Background Execution Strategy

### Why Foreground Service?

Android aggressively kills background processes to save battery. A foreground service with a visible notification is the only reliable way to keep a process alive for 24/7 data collection.

```
BlackBoxService (foreground service)
    visible notification: "BlackBox is recording"
    notification channel: IMPORTANCE_LOW (no sound)

    wakelock: PARTIAL_WAKE_LOCK
    (prevents CPU sleep while collecting, but allows screen off)
```

### WorkManager for Scheduled Tasks

WorkManager jobs survive app restarts and device reboots. They run even if the foreground service is off.

```
DailySummaryWorker
    constraints: network NOT required
    schedule: periodic, every 24h, preferred window: 23:55

CleanupWorker
    constraints: charging preferred, network NOT required
    schedule: periodic, every 24h, preferred window: 02:00
```

### Battery Optimization

1. **Adaptive intervals**: Location collection rate decreases when activity is STILL (from 30s to 5min).
2. **Batched writes**: `SaveRecordUseCase` uses a `RecordBatcher` that groups small records into bulk INSERTs (max 50 records or 5 seconds, whichever comes first).
3. **Doze-aware**: WorkManager constraints ensure heavy work (cleanup, summaries) only runs when device is charging/idle.

---

## Dependency Injection (Koin)

### Why Koin vs Hilt?

| Option | Pros | Cons |
|--------|------|------|
| Hilt | Standard Android DI, compile-time | Android-only — breaks KMP shared module |
| Dagger | Compile-time checks | Complex setup, annotation hell |
| Koin | KMP-native, simple DSL, runtime | Runtime failures (not compile-time) |

Since shared module ViewModels and use cases need DI, and Hilt cannot inject into KMP code, Koin is the pragmatic choice.

### Module Structure

```kotlin
// appModule — foundational singletons
val appModule = module {
    single<BlackBoxLogger> { AndroidLogger() }
    single { KeyManager(androidContext()) }
    single { BiometricManager(androidContext()) }
}

// databaseModule — database singleton (created once, lives forever)
val databaseModule = module {
    single {
        val factory = DatabaseDriverFactory(androidContext())
        DatabaseFactory.create(factory)
    }
}

// repositoryModule — one singleton per repository
val repositoryModule = module {
    single<RecordRepository> { RecordRepositoryImpl(get(), get()) }
    single<LocationRepository> { LocationRepositoryImpl(get(), get()) }
    // ...
}

// useCaseModule — use cases can be single or factory
// single: stateless use cases (safer, avoids accidental state)
val useCaseModule = module {
    single { ProcessQueryUseCase(get(), get(), get(), get()) }
    single { GetTimelineUseCase(get(), get(), get(), get()) }
    // ...
}

// viewModelModule — must use viewModel { } for lifecycle scoping
val viewModelModule = module {
    viewModel { SearchViewModel(get(), get()) }
    viewModel { TimelineViewModel(get()) }
    // ...
}
```

---

## UI Architecture

### Three-File Screen Pattern

Every screen is split into three files for testability and separation of concerns:

**XxxContract.kt** — Pure data types. No logic. No Android imports. Fully serializable.

**XxxViewModel.kt** — Business wiring. Calls use cases, manages state, emits events. Never touches Compose.

**XxxContent.kt** — Pure Compose. No ViewModel, no coroutines, no side effects. Fully previewable.

**XxxScreen.kt** — Connector. Collects state, subscribes to events, calls navigation callbacks.

### Why `onAction: (Action) -> Unit` instead of direct callbacks?

```kotlin
// Instead of this:
SearchContent(
    onQueryChanged = viewModel::handleQueryChanged,
    onSubmit = viewModel::handleSubmit,
    onClear = viewModel::handleClear,
    ...  // grows unboundedly
)

// We do this:
SearchContent(
    onAction = viewModel::onAction  // single function, always
)
```

Benefits:
1. Single callback reference — Compose stability is simpler (one lambda vs many).
2. `onAction` never changes — prevents unnecessary recomposition.
3. Adding a new action doesn't change any function signatures — only the `Action` sealed class.
4. Easy to test: `onAction = {}` in previews.

### Compose Stability

BlackBox avoids common Compose performance pitfalls:

- **State hoisting**: State lives in ViewModel, not in composable local state.
- **`remember` for expensive calculations**: Anything computed from state that is expensive uses `remember(key)`.
- **`key` for lists**: All `LazyColumn` items use `key = { entry.startTimestamp }` to preserve scroll state on recomposition.
- **Stable action lambdas**: `onAction = viewModel::onAction` is stable (method reference, not lambda literal).
- **`derivedStateOf`**: Used where a value is computed from state but should only trigger recomposition when the computed result changes (not every time the source state changes).

---

## Navigation

### Single-Activity Architecture

BlackBox has exactly one Activity (`MainActivity`). All navigation is handled by Compose Navigation with a `NavHost`.

```kotlin
@Composable
fun BlackBoxNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(navController, startDestination) {
        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToTimeline = { date ->
                    navController.navigate(Screen.Timeline.route(date))
                }
            )
        }
        composable(Screen.Timeline.route) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date")
            TimelineScreen(
                date = LocalDate.parse(date ?: LocalDate.today().toString()),
                onNavigateToMap = { navController.navigate(Screen.Map.route) }
            )
        }
        // ...
    }
}
```

**Why single-activity?**
- Compose Navigation handles back stack natively.
- Shared ViewModel scoping (map + timeline share the same date selection).
- No `Intent` passing between activities (type-unsafe, verbose).

### Onboarding Gate

On first launch, the `startDestination` is `Screen.Onboarding.route`. Once onboarding completes, a flag is written to `SettingsRepository` and future launches start at `Screen.Search.route`.

```kotlin
val startDestination = if (hasCompletedOnboarding) Screen.Search.route else Screen.Onboarding.route
```

---

## Testing Architecture

### Layer-Specific Testing Strategies

**Domain (use cases):** Pure JUnit 5 on JVM. Use fake repository implementations (not mocks). Tests live in `shared/src/commonTest/`.

```kotlin
class GetTimelineUseCaseTest {
    private val fakeLocationRepo = FakeLocationRepository()
    private val fakePlaceRepo = FakePlaceRepository()
    private val useCase = GetTimelineUseCase(fakeLocationRepo, fakeRecordRepo, fakePlaceRepo)

    @Test
    fun `returns empty list when no data for date range`() = runTest {
        val result = useCase(today.startMs, today.endMs)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }
}
```

**ViewModels:** Turbine for Flow testing. Use fake use cases.

```kotlin
@Test
fun `date selected — loads entries and updates state`() = runTest {
    viewModel.state.test {
        awaitItem()  // initial state
        viewModel.onAction(Action.DateSelected(yesterday))
        val loading = awaitItem()
        assertTrue(loading.isLoading)
        val loaded = awaitItem()
        assertFalse(loaded.isLoading)
        assertEquals(2, loaded.entries.size)
    }
}
```

**Query Engine:** Table-driven tests with 50+ query strings.

```kotlin
@ParameterizedTest
@MethodSource("queryProvider")
fun `query parses correctly`(input: String, expectedIntent: QueryIntent, expectedRange: TimeRange?) {
    val result = queryEngine.parse(input)
    assertEquals(expectedIntent, result.intent)
    if (expectedRange != null) assertEquals(expectedRange, result.timeRange)
}
```

---

## Design Decisions Log

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Architecture | Clean + MVI | MVVM | Better state consistency, testability |
| Platform | KMP + CMP | Pure Android | Future iOS, forces clean layers |
| Database | SQLDelight | Room | KMP support |
| Encryption | SQLCipher | Android EncryptedSharedPrefs | Full database encryption, not just key-value |
| DI | Koin | Hilt | KMP compatible |
| Maps | OSMDroid | Google Maps | Offline support, no API key |
| NLP | Custom rule-based | Cloud AI / TFLite | Privacy, zero latency, no model size |
| Background | Foreground Service | WorkManager only | Reliability for 24/7 collection |
| Icons | Material Icons Core (pinned 1.7.3) | CMP default | CMP 1.8.2+ removed transitive dep |
| Navigation | Compose Navigation KMP 2.9.1 | 2.9.6 | Stability (2.9.6 had issues) |
