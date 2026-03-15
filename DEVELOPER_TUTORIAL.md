# BlackBox — Developer Tutorial

This tutorial takes you from zero to fully productive on the BlackBox codebase. Read it top to bottom if you're new; use it as a reference otherwise.

---

## Table of Contents

1. [Project Philosophy](#1-project-philosophy)
2. [Repository Setup](#2-repository-setup)
3. [Module Structure & Rules](#3-module-structure--rules)
4. [The MVI Pattern (mandatory)](#4-the-mvi-pattern-mandatory)
5. [Adding a New Use Case](#5-adding-a-new-use-case)
6. [Adding a New Collector](#6-adding-a-new-collector)
7. [Adding a New Screen](#7-adding-a-new-screen)
8. [Working with the Database](#8-working-with-the-database)
9. [Dependency Injection (Koin)](#9-dependency-injection-koin)
10. [Testing](#10-testing)
11. [Debugging Tips](#11-debugging-tips)
12. [Common Gotchas](#12-common-gotchas)

---

## 1. Project Philosophy

### Privacy first
BlackBox never transmits data. No analytics SDKs. No network calls except optional AI queries that the user explicitly enables. When in doubt, store locally or don't store at all.

### Offline first
Every feature must work without internet. Maps use OSMDroid (OpenStreetMap tiles stored on-device). The NLP query engine is 100% on-device. Even AI features degrade gracefully to the local query engine.

### Clean Architecture, strictly enforced
```
UI Layer
  → ViewModel (MVI)
    → Use Cases (business logic)
      → Repository Interfaces
        → Repository Implementations
          → Database / Platform APIs
```
**The inner layer never depends on the outer layer.** The `domain/` package has zero Android imports. The UI has no direct database access.

### Battery budget
Target: ≤ 3% daily battery on NORMAL profile. Prefer event-driven collectors (BroadcastReceiver) over polling. See `BATTERY.md` for full strategy.

---

## 2. Repository Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK API 35

### Steps

```bash
# 1. Clone
git clone <repo-url>
cd Kmpstealthapp

# 2. Create local.properties (if missing)
echo "sdk.dir=/path/to/your/Android/sdk" > local.properties

# 3. Sync Gradle
./gradlew build --dry-run

# 4. Install debug build
./gradlew :androidApp:installDebug

# 5. Run tests
./gradlew :shared:testDebugUnitTest
./gradlew :androidApp:testDebugUnitTest

# 6. Run lint
./gradlew :shared:detekt
./gradlew :androidApp:detekt
```

### Key Gradle Tasks

| Task | Purpose |
|------|---------|
| `:androidApp:assembleDebug` | Build debug APK |
| `:androidApp:installDebug` | Build + install on connected device |
| `:shared:testDebugUnitTest` | Run shared module tests |
| `:androidApp:testDebugUnitTest` | Run Android module tests |
| `:shared:detekt` | Lint shared module |
| `:androidApp:compileDebugKotlinAndroid` | Compile only (fast check) |
| `:shared:generateSqlDelightInterface` | Regenerate SQLDelight code |

---

## 3. Module Structure & Rules

### Two modules

**`shared/`** — Kotlin Multiplatform library
- `commonMain/`: All domain logic, data access, and UI. This is where ~70% of the code lives.
- `androidMain/`: Android `actual` implementations of `expect` declarations.
- `commonTest/`: Cross-platform tests.

**`androidApp/`** — Android application
- `androidMain/`: Collectors, foreground service, WorkManager jobs, security, DI modules.
- `test/`: Android-specific tests.

### Package map

```
com.blackbox
├── domain/
│   ├── model/          Data classes (pure Kotlin, no framework deps)
│   │   ├── record/     CollectedRecord, RecordData variants, CollectorType
│   │   ├── query/      ParsedQuery, QueryResult, QueryIntent, TimeRange
│   │   ├── timeline/   TimelineEntry, DailySummary, DerivedEvent
│   │   ├── place/      KnownPlace, PlaceCategory
│   │   ├── map/        LocationStay, DayLocationSummary
│   │   ├── sleep/      SleepSession, SleepQuality
│   │   └── settings/   CollectorSetting, CollectionProfile
│   ├── repository/     Interfaces only (no implementations)
│   ├── usecase/        One class per use case
│   ├── query/          NLP query engine components
│   ├── platform/       expect declarations
│   └── util/           BlackBoxLogger interface
│
├── data/
│   ├── database/       SQLDelight schema file + DatabaseFactory
│   ├── repository/     Repository implementations (use SQLDelight)
│   └── mapper/         DB entity ↔ domain model conversions
│
├── ui/
│   ├── search/         SearchContract + SearchViewModel + SearchContent + SearchScreen
│   ├── timeline/       ... same pattern
│   ├── map/
│   ├── insights/
│   ├── settings/
│   ├── onboarding/
│   ├── theme/          BlackBoxTheme, Colors, Typography, Dimens, CyberpunkModifiers
│   └── common/         Shared composables (ErrorView, LoadingIndicator, etc.)
│
└── (androidApp) com.blackbox.android
    ├── collector/      Sensor collectors
    ├── service/        BlackBoxService, BootReceiver
    ├── worker/         WorkManager jobs
    ├── security/       KeyManager, BiometricManager
    └── di/             Koin modules
```

### Import rules
| Layer | May import from | MUST NOT import from |
|-------|----------------|---------------------|
| `domain/` | Standard library, `kotlinx.*` | `android.*`, `data.*`, `ui.*` |
| `data/` | `domain/`, SQLDelight, Koin | `ui.*`, `android.app.*` |
| `ui/` | `domain/`, `data/` (via use cases only), Compose | Direct DB access |
| `androidApp/` | Everything | (no restrictions, but follow patterns) |

---

## 4. The MVI Pattern (mandatory)

Every screen uses Model-View-Intent. There are **four files** per screen.

### File 1: Contract (`XxxContract.kt`)
Defines the entire screen's contract in one place.

```kotlin
object TimelineContract {

    /** All UI state in one immutable snapshot. */
    data class State(
        val isLoading: Boolean = false,
        val entries: List<TimelineEntry> = emptyList(),
        val selectedDate: String = "",
        val error: String? = null,
    )

    /** Every interaction the user can perform. */
    sealed interface Action {
        data object Refresh : Action
        data class DateSelected(val date: String) : Action
        data class EntryTapped(val entry: TimelineEntry) : Action
    }

    /** One-time effects — navigation, toasts. Never put these in State. */
    sealed interface Event {
        data class NavigateToMap(val date: String) : Event
        data class ShowSnackbar(val message: String) : Event
    }
}
```

### File 2: ViewModel (`XxxViewModel.kt`)
Processes actions, updates state, emits events. **No business logic here — delegate to use cases.**

```kotlin
class TimelineViewModel(
    private val getTimelineUseCase: GetTimelineUseCase,
    private val getCollectorGroupsUseCase: GetCollectorGroupsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineContract.State())
    val state: StateFlow<TimelineContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimelineContract.Event>()
    val events: SharedFlow<TimelineContract.Event> = _events.asSharedFlow()

    fun onAction(action: TimelineContract.Action) {
        when (action) {
            is TimelineContract.Action.Refresh -> loadTimeline()
            is TimelineContract.Action.DateSelected -> handleDateSelected(action.date)
            is TimelineContract.Action.EntryTapped -> handleEntryTapped(action.entry)
        }
    }

    private fun loadTimeline() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getTimelineUseCase(startTime, endTime)
                .onSuccess { entries ->
                    _state.update { it.copy(isLoading = false, entries = entries) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }
}
```

### File 3: Screen root (`XxxScreen.kt`)
Wires the ViewModel to the content composable. **No UI logic here.**

```kotlin
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = koinViewModel(),
    onNavigateToMap: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TimelineContract.Event.NavigateToMap -> onNavigateToMap(event.date)
                is TimelineContract.Event.ShowSnackbar -> { /* show snackbar */ }
            }
        }
    }

    TimelineContent(state = state, onAction = viewModel::onAction)
}
```

### File 4: Content (`XxxContent.kt`)
Pure UI — only receives state and lambda, no ViewModel. Must have @Preview.

```kotlin
@Composable
fun TimelineContent(
    state: TimelineContract.State,
    onAction: (TimelineContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pure rendering based on state
    // Every user interaction dispatches an Action
}

@Preview
@Composable
private fun TimelineContentLoadingPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(isLoading = true),
            onAction = {},
        )
    }
}
```

### Rules
- Never pass a ViewModel into a Content composable
- State is `StateFlow` in ViewModel, `collectAsStateWithLifecycle()` in Screen
- One-time effects (navigation, toasts) go in `SharedFlow<Event>`, not in State
- State fields default to empty/false — the screen renders correctly with no data
- Every content composable has at least 2 `@Preview` annotations

---

## 5. Adding a New Use Case

Use cases are the single source of business logic. Each does exactly one thing.

### Step 1: Create the use case class in `shared/domain/usecase/`

```kotlin
// shared/src/commonMain/kotlin/com/blackbox/domain/usecase/place/DeletePlaceUseCase.kt

/**
 * Permanently deletes a known place from the database.
 *
 * @property placeRepository Source of place data.
 * @property logger Logger for diagnostics.
 */
class DeletePlaceUseCase(
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {
    /**
     * Deletes the place with the given [placeId].
     *
     * @return [Result.success] if deleted, [Result.failure] with the cause if not.
     */
    suspend operator fun invoke(placeId: Long): Result<Unit> = runCatching {
        logger.i(TAG, "Deleting place: placeId=$placeId")
        placeRepository.deletePlace(placeId)
    }

    companion object {
        private const val TAG = "DeletePlaceUseCase"
    }
}
```

**Rules for use cases**:
- `operator fun invoke(...)` is the public entry point
- Returns `Result<T>` — never throws
- No Android imports
- KDoc on every class and function
- Logs invocation and result summary (not data)

### Step 2: Register in Koin (`UseCaseModule.kt`)

```kotlin
// androidApp/.../di/UseCaseModule.kt
val useCaseModule = module {
    // ...existing...
    single { DeletePlaceUseCase(get(), get()) }
}
```

`get()` resolves dependencies automatically from other Koin modules.

### Step 3: Inject into ViewModel

```kotlin
class MapViewModel(
    // ...
    private val deletePlaceUseCase: DeletePlaceUseCase,  // add here
) : ViewModel() {

    private fun handleDeletePlace(placeId: Long) {
        viewModelScope.launch {
            deletePlaceUseCase(placeId)
                .onSuccess { loadPlaces() }
                .onFailure { _events.emit(MapContract.Event.ShowSnackbar("Failed")) }
        }
    }
}
```

### Step 4: Update ViewModel's Koin registration

```kotlin
viewModel { MapViewModel(get(), get(), get(), get(), get()) }
//                                                     ^ new parameter
```

### Step 5: Write a test

```kotlin
class DeletePlaceUseCaseTest {
    private val placeRepository: PlaceRepository = mockk()
    private val logger: BlackBoxLogger = mockk(relaxed = true)
    private val useCase = DeletePlaceUseCase(placeRepository, logger)

    @Test
    fun `invoke with valid id — delegates to repository`() = runTest {
        coEvery { placeRepository.deletePlace(42L) } just Runs

        val result = useCase(42L)

        assertTrue(result.isSuccess)
        coVerify { placeRepository.deletePlace(42L) }
    }

    @Test
    fun `invoke when repository throws — returns failure`() = runTest {
        coEvery { placeRepository.deletePlace(any()) } throws RuntimeException("DB error")

        val result = useCase(99L)

        assertTrue(result.isFailure)
    }
}
```

---

## 6. Adding a New Collector

### Step 1: Create a data class in `shared/domain/model/record/`

```kotlin
// MyNewData.kt
@Serializable
data class MyNewData(
    val someValue: Int,
    val anotherField: String,
)
```

### Step 2: Add a variant to `RecordData` sealed interface

```kotlin
// CollectedRecord.kt — add to RecordData
data class MyNew(val myNewData: MyNewData) : RecordData
```

### Step 3: Add to `CollectorType` enum

```kotlin
enum class CollectorType {
    LOCATION, ACTIVITY, WIFI, APP_USAGE, SCREEN_STATE,
    AUDIO_LEVEL, BATTERY, CONNECTIVITY, BAROMETER, LIGHT,
    CALL_LOG, MEDIA_PLAYBACK,
    MY_NEW,  // add here
}
```

### Step 4: Create the collector in `androidApp/collector/`

```kotlin
/**
 * Collects [MyNewData] every [POLL_INTERVAL_MS] milliseconds.
 */
class MyNewCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = POLL_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.MY_NEW

    private var sessionId: String = ""

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting MyNew collector")
        sessionId = UUID.randomUUID().toString()
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping MyNew collector")
    }

    override suspend fun collectData(): List<CollectedRecord> {
        val now = System.currentTimeMillis()
        val data = MyNewData(someValue = 42, anotherField = "hello")

        return listOf(
            CollectedRecord(
                timestamp = now,
                collectorType = CollectorType.MY_NEW,
                data = RecordData.MyNew(data),
                accuracyScore = 1.0f,
                sessionId = sessionId,
                createdAt = now,
            )
        )
    }

    companion object {
        private const val TAG = "MyNewCollector"
        private const val POLL_INTERVAL_MS = 10 * 60_000L  // 10 minutes
    }
}
```

### Step 5: Register in `CollectorModule.kt`

```kotlin
single { MyNewCollector(androidContext(), get()) }

// In the orchestrator block:
register(get<MyNewCollector>())
```

### Step 6: Handle serialization in database
If the data needs to be stored, ensure it's `@Serializable` and that the `RecordMapper` handles the new type:

```kotlin
// RecordMapper.kt
fun toJson(data: RecordData): String = when (data) {
    // ...existing...
    is RecordData.MyNew -> json.encodeToString(data.myNewData)
}

fun fromJson(type: CollectorType, json: String): RecordData = when (type) {
    // ...existing...
    CollectorType.MY_NEW -> RecordData.MyNew(Json.decodeFromString(json))
}
```

---

## 7. Adding a New Screen

### Step 1: Create Contract

```kotlin
// shared/src/commonMain/kotlin/com/blackbox/ui/newscreen/NewScreenContract.kt
object NewScreenContract {
    data class State(
        val isLoading: Boolean = false,
        val data: List<String> = emptyList(),
    )

    sealed interface Action {
        data object Load : Action
        data class ItemClicked(val item: String) : Action
    }

    sealed interface Event {
        data class ShowToast(val message: String) : Event
    }
}
```

### Step 2: Create ViewModel

```kotlin
// NewScreenViewModel.kt
class NewScreenViewModel(
    private val someUseCase: SomeUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(NewScreenContract.State())
    val state: StateFlow<NewScreenContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NewScreenContract.Event>()
    val events: SharedFlow<NewScreenContract.Event> = _events.asSharedFlow()

    init { load() }

    fun onAction(action: NewScreenContract.Action) {
        when (action) {
            is NewScreenContract.Action.Load -> load()
            is NewScreenContract.Action.ItemClicked -> handleItem(action.item)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            someUseCase()
                .onSuccess { data -> _state.update { it.copy(isLoading = false, data = data) } }
                .onFailure { _state.update { it.copy(isLoading = false) } }
        }
    }
}
```

### Step 3: Create Content composable

```kotlin
// NewScreenContent.kt
@Composable
fun NewScreenContent(
    state: NewScreenContract.State,
    onAction: (NewScreenContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingIndicator()
        return
    }
    LazyColumn(modifier = modifier) {
        items(state.data) { item ->
            Text(
                text = item,
                modifier = Modifier.clickable { onAction(NewScreenContract.Action.ItemClicked(item)) },
            )
        }
    }
}

@Preview
@Composable
private fun NewScreenContentPreview() {
    BlackBoxTheme {
        NewScreenContent(
            state = NewScreenContract.State(data = listOf("Item 1", "Item 2")),
            onAction = {},
        )
    }
}
```

### Step 4: Create Screen root

```kotlin
// NewScreen.kt
@Composable
fun NewScreen(
    viewModel: NewScreenViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NewScreenContract.Event.ShowToast -> { /* show toast */ }
            }
        }
    }

    NewScreenContent(state = state, onAction = viewModel::onAction)
}
```

### Step 5: Register ViewModel in Koin

```kotlin
// ViewModelModule.kt
viewModel { NewScreenViewModel(get()) }
```

### Step 6: Add to navigation graph

```kotlin
// BlackBoxNavHost.kt — add composable destination
composable(Screen.NewScreen.route) {
    NewScreen(onNavigateBack = navController::popBackStack)
}
```

```kotlin
// Screen.kt — add route
sealed class Screen(val route: String) {
    // ...existing...
    data object NewScreen : Screen("new_screen")
}
```

---

## 8. Working with the Database

BlackBox uses **SQLDelight** — a compile-time type-safe SQL library.

### Schema file location
`shared/src/commonMain/sqldelight/com/blackbox/data/database/BlackBoxDatabase.sq`

### Adding a new query

```sql
-- In BlackBoxDatabase.sq
-- Add to the appropriate section

-- Query: get all records of a specific type on a specific day
getRecordsByTypeOnDate:
SELECT * FROM BlackBoxRecord
WHERE collector_type = :type
  AND timestamp >= :dayStart
  AND timestamp < :dayEnd
ORDER BY timestamp ASC;
```

After adding, regenerate the Kotlin code:
```bash
./gradlew :shared:generateSqlDelightInterface
```

### Using the generated query

```kotlin
// In a repository implementation
val records = database.blackBoxDatabaseQueries
    .getRecordsByTypeOnDate(
        type = collectorType.name,
        dayStart = startMs,
        dayEnd = endMs,
    )
    .executeAsList()
    .map(mapper::toDomain)
```

### Important conventions
- All `REAL` columns → `Double` in Kotlin
- All `INTEGER` columns → `Long` in Kotlin
- Booleans stored as `INTEGER` `0`/`1`
- JSON arrays/objects stored as `TEXT` (serialized with `kotlinx.serialization`)
- Single query accessor: `database.blackBoxDatabaseQueries` (not per-table)

### Repository implementation pattern

```kotlin
class MyRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val logger: BlackBoxLogger,
) : MyRepository {

    override suspend fun getItems(): List<MyItem> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching items")
            database.blackBoxDatabaseQueries
                .getAllMyItems()
                .executeAsList()
                .map(MyMapper::toDomain)
        }
    }

    companion object {
        private const val TAG = "MyRepository"
    }
}
```

**Always**: use `withContext(Dispatchers.IO)` for all database operations.

---

## 9. Dependency Injection (Koin)

BlackBox uses Koin for DI. There are 6 modules loaded at application startup.

### Module loading order (from `BlackBoxApp.kt`)

```kotlin
startKoin {
    modules(
        appModule,          // Logger, KeyManager, BiometricManager
        databaseModule,     // Database instance
        repositoryModule,   // Repository implementations
        useCaseModule,      // All use cases
        collectorModule,    // All collectors + orchestrator
        viewModelModule,    // All ViewModels
    )
}
```

### Scopes

| Scope | Usage | Example |
|-------|-------|---------|
| `single { }` | One instance app-wide (singletons) | Repositories, use cases |
| `factory { }` | New instance each time | Rarely used |
| `viewModel { }` | Lifecycle-scoped | ViewModels |

### Adding a new dependency

1. Create the class with constructor injection
2. Register in the appropriate module
3. Add `get()` for each constructor parameter

```kotlin
// In UseCaseModule.kt
single { MyNewUseCase(get(), get()) }
//                    ^    ^ — resolved from other modules in order

// In ViewModelModule.kt
viewModel { MyNewViewModel(get()) }
```

### Resolving in Compose

```kotlin
@Composable
fun MyScreen(
    viewModel: MyNewViewModel = koinViewModel(),  // Compose integration
) { ... }
```

### Resolving in non-Compose contexts

```kotlin
// In a Service or Worker
class BlackBoxService : Service() {
    private val orchestrator: CollectorOrchestrator by inject()
}
```

---

## 10. Testing

### Test location

- `shared/src/commonTest/kotlin/com/blackbox/` — shared tests (use cases, mappers, query engine)
- `androidApp/src/test/kotlin/com/blackbox/android/` — Android-specific tests (ViewModels, services)

### Test naming convention

```kotlin
@Test
fun `submit query with empty text — does nothing`() { ... }

@Test
fun `detect sleep on night with 8h dark period — returns GOOD quality`() { ... }
```

### Use case test template

```kotlin
class MyUseCaseTest {

    // Mocks — use MockK
    private val repository: MyRepository = mockk()
    private val logger: BlackBoxLogger = mockk(relaxed = true)

    // System under test
    private val useCase = MyUseCase(repository, logger)

    @Test
    fun `invoke with valid data — returns mapped result`() = runTest {
        // Arrange
        val fakeData = listOf(FakeData("value"))
        coEvery { repository.getData() } returns fakeData

        // Act
        val result = useCase()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        coVerify { repository.getData() }
    }

    @Test
    fun `invoke when repository throws — returns failure`() = runTest {
        coEvery { repository.getData() } throws RuntimeException("error")

        val result = useCase()

        assertTrue(result.isFailure)
    }
}
```

### ViewModel test template (with Turbine)

```kotlin
class MyViewModelTest {
    private val useCase: MyUseCase = mockk()
    private val viewModel = MyViewModel(useCase)

    @Test
    fun `load action — emits loading then success state`() = runTest {
        coEvery { useCase() } returns Result.success(listOf("item"))

        viewModel.state.test {
            assertEquals(State(isLoading = false), awaitItem())  // initial

            viewModel.onAction(Action.Load)

            assertEquals(State(isLoading = true), awaitItem())
            assertEquals(State(isLoading = false, items = listOf("item")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### Fake repository pattern

```kotlin
class FakePlaceRepository : PlaceRepository {
    val places = mutableListOf<KnownPlace>()

    override suspend fun getAllPlaces(): List<KnownPlace> = places.toList()
    override suspend fun savePlace(place: KnownPlace): Long {
        places.add(place.copy(id = places.size.toLong() + 1))
        return places.last().id
    }
    override suspend fun deletePlace(placeId: Long) {
        places.removeAll { it.id == placeId }
    }
    // ... implement other methods as no-ops or simple lists
}
```

---

## 11. Debugging Tips

### View logs
All BlackBox logs use structured tags matching the class name:
```bash
adb logcat -s "BlackBoxService" "LocationCollector" "GetTimelineUseCase"
```

Or filter by app:
```bash
adb logcat --pid=$(adb shell pidof com.blackbox.android)
```

### Check database
Use the **DB dump** feature: Settings → scroll to bottom → "Dump DB Records" button. This dumps all records to logcat.

### Force-trigger collectors
Stop and restart the service via Settings → toggle any collector off, then back on. The service restarts and collectors re-initialize.

### Simulate data
For testing without a physical device, you can insert records directly via the `SaveRecordUseCase` in a test setup or a debug-only screen.

### Check if sleep detection sees data
```kotlin
// In a test or debug code:
val useCase = DetectSleepSessionsUseCase(recordRepository, logger)
val session = useCase("2026-03-11").getOrNull()
println("Sleep: ${session?.durationMinutes}min, ${session?.quality}")
```

---

## 12. Common Gotchas

### 1. UI code is in `commonMain`, not `androidApp`
All Compose screens live in `shared/src/commonMain/kotlin/com/blackbox/ui/`. Don't create UI code in `androidApp`.

### 2. String resources use CMP, not Android
```kotlin
// WRONG — Android only
import androidx.compose.ui.res.stringResource

// CORRECT — Compose Multiplatform
import org.jetbrains.compose.resources.stringResource
stringResource(Res.string.my_string)
```

### 3. Navigation Compose version is pinned
Use `org.jetbrains.androidx.navigation:navigation-compose:2.9.1` — do NOT upgrade to 2.9.6 (breaks KMP).

### 4. Material Icons must be explicit
CMP 1.8.2 removed the transitive icon dependency. Always add:
```kotlin
implementation(compose.materialIconsCore)
```

### 5. Single query accessor
SQLDelight has ONE accessor for all tables: `database.blackBoxDatabaseQueries`. There is no per-table accessor like `database.locationRecordQueries`.

### 6. Dispatchers.IO for all DB calls
Always wrap database operations in `withContext(Dispatchers.IO)`:
```kotlin
override suspend fun getAll(): List<Item> = withContext(Dispatchers.IO) {
    database.blackBoxDatabaseQueries.getAll().executeAsList()
}
```

### 7. Step counter is cumulative
`ActivityData.stepCountCumulative` is the total steps since reboot, not a delta. To get today's steps:
```kotlin
val todaySteps = cumulativeValues.max() - cumulativeValues.min()
```

### 8. Sleep detection needs at least 3h of screen-off
The minimum gap to qualify as sleep is `MIN_SLEEP_MS = 3 * HOUR_MS`. A phone that's off for 2h 55m will not be detected as a sleep session.

### 9. expect/actual warnings are expected
You'll see this in every build:
```
'expect'/'actual' classes are in Beta. Consider using '-Xexpect-actual-classes' flag.
```
This is a known KMP limitation — safe to ignore.

### 10. First location fix takes 10-14 minutes
The FusedLocationProvider needs a warm-up period. Don't expect data in the first few minutes after installation.
