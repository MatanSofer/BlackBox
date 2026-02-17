# CLAUDE.md — BlackBox Project Instructions for AI Agents

## Project Overview

BlackBox is a **privacy-first, offline-first personal life flight recorder** built with Kotlin Multiplatform (KMP). It passively captures contextual metadata from the user's device (location, activity, app usage, sensor data — never content) and stores everything locally in an encrypted database. Users retrieve information through natural language queries powered by an on-device query engine. The Android app is the POC; iOS is a future phase.

**Tagline:** "Your life has a black box. Now you can read it."

---

## Quick Start

```bash
# Build the project
./gradlew build

# Run Android app
./gradlew :androidApp:installDebug

# Run shared module tests
./gradlew :shared:testDebugUnitTest

# Run Android-specific tests
./gradlew :androidApp:testDebugUnitTest

# Check lint
./gradlew :shared:detekt
./gradlew :androidApp:detekt
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0+ |
| KMP Framework | Kotlin Multiplatform |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVI |
| Database | SQLDelight + SQLCipher |
| DI | Koin (Multiplatform) |
| Async | Kotlin Coroutines + Flow (StateFlow for state, SharedFlow for events) |
| Location | Google FusedLocationProvider |
| Activity | Google Activity Recognition API |
| Maps | OSMDroid (OpenStreetMap, offline) |
| NLP | Custom rule-based parser (Kotlin, shared module) |
| Encryption | SQLCipher + Android Keystore |
| Background | Foreground Service + WorkManager |
| Sensors | Android SensorManager |
| Testing | JUnit 5 + Turbine + MockK + Robolectric |
| Static Analysis | Detekt |
| Build | Gradle with KMP plugin + Version Catalogs |

---

## Project Structure

```
blackbox/
├── shared/                              # KMP shared module (~70% of code)
│   ├── src/commonMain/kotlin/com/blackbox/
│   │   ├── domain/                      # Domain layer (pure Kotlin, no platform deps)
│   │   │   ├── model/                   # Domain data classes & value objects
│   │   │   │   ├── record/              # BlackBoxRecord, LocationRecord, etc.
│   │   │   │   ├── query/               # ParsedQuery, QueryIntent, QueryResult
│   │   │   │   ├── timeline/            # TimelineEntry, DailySummary, DerivedEvent
│   │   │   │   ├── place/               # KnownPlace, PlaceCategory
│   │   │   │   └── settings/            # CollectorSetting, CollectionProfile
│   │   │   ├── usecase/                 # Use cases (single responsibility)
│   │   │   │   ├── query/               # ProcessQueryUseCase, ParseTimeExpressionUseCase
│   │   │   │   ├── timeline/            # GetTimelineUseCase, GenerateDailySummaryUseCase
│   │   │   │   ├── record/              # SaveRecordUseCase, GetRecordsUseCase
│   │   │   │   ├── place/               # DetectKnownPlacesUseCase, GetPlacesUseCase
│   │   │   │   ├── insight/             # GetPatternsUseCase, GetInsightsUseCase
│   │   │   │   ├── proof/               # GenerateProofReportUseCase
│   │   │   │   └── settings/            # UpdateCollectorSettingUseCase
│   │   │   ├── repository/              # Repository interfaces (contracts)
│   │   │   │   ├── RecordRepository.kt
│   │   │   │   ├── LocationRepository.kt
│   │   │   │   ├── TimelineRepository.kt
│   │   │   │   ├── PlaceRepository.kt
│   │   │   │   ├── InsightRepository.kt
│   │   │   │   └── SettingsRepository.kt
│   │   │   └── query/                   # Query engine (NL parsing)
│   │   │       ├── QueryEngine.kt       # Orchestrates parsing → building → executing
│   │   │       ├── TimeExpressionParser.kt
│   │   │       ├── IntentClassifier.kt
│   │   │       ├── EntityExtractor.kt
│   │   │       ├── QueryBuilder.kt
│   │   │       └── ResponseGenerator.kt
│   │   ├── data/                        # Data layer
│   │   │   ├── database/                # SQLDelight
│   │   │   │   ├── BlackBoxDatabase.sq  # Schema definitions
│   │   │   │   └── migrations/          # Schema migrations
│   │   │   ├── repository/              # Repository implementations
│   │   │   │   ├── RecordRepositoryImpl.kt
│   │   │   │   ├── LocationRepositoryImpl.kt
│   │   │   │   └── ...
│   │   │   └── mapper/                  # DB entity ↔ domain model mappers
│   │   │       ├── RecordMapper.kt
│   │   │       ├── LocationMapper.kt
│   │   │       └── ...
│   │   └── platform/                    # expect declarations
│   │       ├── DatabaseDriverFactory.kt # expect: provides SQLDelight driver
│   │       ├── Collectors.kt            # expect: platform data collectors
│   │       ├── EncryptionManager.kt     # expect: platform crypto
│   │       ├── DeviceInfo.kt            # expect: device identifiers
│   │       └── DateTimeProvider.kt      # expect: current time (testable)
│   ├── src/commonTest/                  # Shared tests
│   ├── src/androidMain/                 # Android actual declarations
│   └── src/iosMain/                     # iOS actual declarations (stubs for now)
│
├── androidApp/                          # Android application
│   ├── src/main/kotlin/com/blackbox/android/
│   │   ├── BlackBoxApp.kt              # Application class, Koin init
│   │   ├── MainActivity.kt             # Single activity
│   │   ├── ui/                          # Compose UI (MVI pattern)
│   │   │   ├── navigation/
│   │   │   │   ├── BlackBoxNavHost.kt   # Navigation graph
│   │   │   │   └── Screen.kt           # Sealed class for routes
│   │   │   ├── search/                  # Search screen
│   │   │   │   ├── SearchScreen.kt      # Compose root (lambdas for actions)
│   │   │   │   ├── SearchContent.kt     # Pure UI with @Preview
│   │   │   │   ├── SearchViewModel.kt   # MVI ViewModel
│   │   │   │   ├── SearchContract.kt    # State, Action, Event definitions
│   │   │   │   └── components/          # Screen-specific composables
│   │   │   ├── timeline/                # Timeline screen
│   │   │   │   ├── TimelineScreen.kt
│   │   │   │   ├── TimelineContent.kt
│   │   │   │   ├── TimelineViewModel.kt
│   │   │   │   ├── TimelineContract.kt
│   │   │   │   └── components/
│   │   │   ├── map/                     # Map screen
│   │   │   │   ├── MapScreen.kt
│   │   │   │   ├── MapContent.kt
│   │   │   │   ├── MapViewModel.kt
│   │   │   │   ├── MapContract.kt
│   │   │   │   └── components/
│   │   │   ├── insights/                # Patterns & Insights screen
│   │   │   │   ├── InsightsScreen.kt
│   │   │   │   ├── InsightsContent.kt
│   │   │   │   ├── InsightsViewModel.kt
│   │   │   │   ├── InsightsContract.kt
│   │   │   │   └── components/
│   │   │   ├── settings/                # Settings screen
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   ├── SettingsContent.kt
│   │   │   │   ├── SettingsViewModel.kt
│   │   │   │   ├── SettingsContract.kt
│   │   │   │   └── components/
│   │   │   ├── onboarding/              # First-launch onboarding
│   │   │   │   ├── OnboardingScreen.kt
│   │   │   │   ├── OnboardingContent.kt
│   │   │   │   ├── OnboardingViewModel.kt
│   │   │   │   └── OnboardingContract.kt
│   │   │   ├── theme/                   # Material 3 theme
│   │   │   │   ├── BlackBoxTheme.kt
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Typography.kt
│   │   │   │   ├── Shape.kt
│   │   │   │   └── Dimens.kt
│   │   │   └── common/                  # Shared composables
│   │   │       ├── BlackBoxTopBar.kt
│   │   │       ├── BlackBoxBottomBar.kt
│   │   │       ├── LoadingIndicator.kt
│   │   │       ├── ErrorView.kt
│   │   │       ├── EmptyStateView.kt
│   │   │       └── ...
│   │   ├── collector/                   # Platform collectors (actual implementations)
│   │   │   ├── base/
│   │   │   │   ├── BaseCollector.kt     # Abstract base with common logic
│   │   │   │   └── CollectorOrchestrator.kt
│   │   │   ├── LocationCollector.kt
│   │   │   ├── ActivityCollector.kt
│   │   │   ├── WifiCollector.kt
│   │   │   ├── AppUsageCollector.kt
│   │   │   ├── ScreenStateCollector.kt
│   │   │   ├── AudioLevelCollector.kt
│   │   │   ├── BatteryCollector.kt
│   │   │   ├── ConnectivityCollector.kt
│   │   │   ├── BarometerCollector.kt
│   │   │   └── LightCollector.kt
│   │   ├── service/
│   │   │   ├── BlackBoxService.kt       # Foreground service
│   │   │   └── BootReceiver.kt          # Restart on boot
│   │   ├── security/
│   │   │   ├── KeyManager.kt            # Android Keystore wrapper
│   │   │   └── BiometricManager.kt      # Biometric authentication
│   │   ├── worker/
│   │   │   ├── DailySummaryWorker.kt    # End-of-day summary generation
│   │   │   └── CleanupWorker.kt         # Data retention cleanup
│   │   └── di/                          # Koin dependency injection
│   │       ├── AppModule.kt
│   │       ├── ViewModelModule.kt
│   │       ├── RepositoryModule.kt
│   │       ├── UseCaseModule.kt
│   │       ├── CollectorModule.kt
│   │       └── DatabaseModule.kt
│   └── src/test/                        # Android unit tests
│
├── docs/                                # Documentation
│   ├── SPEC.md                          # Product specification
│   ├── DEV_SPECS.md                     # Development standards & conventions
│   ├── ARCHITECTURE.md                  # Architecture decisions & diagrams
│   ├── DATABASE.md                      # DB schema, queries, migrations
│   ├── QUERY_ENGINE.md                  # NLP parser documentation
│   ├── COLLECTORS.md                    # Each collector's implementation guide
│   ├── SECURITY.md                      # Encryption & security implementation
│   ├── BATTERY.md                       # Battery optimization guide
│   └── UI_SCREENS.md                    # Screen specs with wireframes
│
├── CLAUDE.md                            # THIS FILE — AI agent instructions
└── TODO.md                              # Current tasks & progress
```

---

## Architecture Rules — MUST FOLLOW

### 1. Clean Architecture Layers

```
UI (Compose) → ViewModel (MVI) → UseCase → Repository Interface → Repository Impl → Database/Platform
```

**Dependency rule:** Inner layers NEVER depend on outer layers. Domain knows nothing about Android, Compose, or SQLDelight specifics.

- **Domain layer** (`shared/domain/`): Pure Kotlin. No Android imports. No framework dependencies. Contains models, use cases, repository interfaces, and the query engine.
- **Data layer** (`shared/data/`): Implements repository interfaces. Contains SQLDelight queries, mappers, and data source logic.
- **Platform layer** (`shared/platform/` + `androidApp/`): Android-specific implementations. Collectors, services, Keystore, sensors.
- **UI layer** (`androidApp/ui/`): Jetpack Compose screens with MVI ViewModels.

### 2. MVI Pattern (MANDATORY for all ViewModels)

Every screen follows this exact pattern:

```kotlin
// ── Contract file: SearchContract.kt ──

/**
 * MVI contract for the Search screen.
 * Defines all possible states, user actions, and one-time events.
 */
object SearchContract {

    /**
     * Single immutable UI state. The ViewModel exposes this as StateFlow.
     * Compose observes this state and re-renders accordingly.
     */
    data class State(
        val query: String = "",
        val isLoading: Boolean = false,
        val result: QueryResult? = null,
        val recentQueries: List<String> = emptyList(),
        val suggestedQueries: List<String> = emptyList(),
        val error: UiError? = null,
        val recordingStatus: RecordingStatus = RecordingStatus.Idle
    )

    /**
     * Actions dispatched FROM the UI TO the ViewModel.
     * Every user interaction maps to exactly one Action.
     */
    sealed interface Action {
        data class QueryChanged(val text: String) : Action
        data object SubmitQuery : Action
        data class QuickActionClicked(val quickAction: QuickAction) : Action
        data class RecentQueryClicked(val query: String) : Action
        data object ClearResults : Action
        data object ViewRawDataClicked : Action
        data class SuggestedQueryClicked(val query: String) : Action
    }

    /**
     * One-time events FROM the ViewModel TO the UI.
     * Used for navigation, toasts, snackbars — things that should NOT persist in state.
     */
    sealed interface Event {
        data class ShowSnackbar(val message: UiText) : Event
        data class NavigateToTimeline(val date: LocalDate) : Event
        data class NavigateToMap(val date: LocalDate) : Event
        data object ScrollToTop : Event
    }
}
```

```kotlin
// ── ViewModel: SearchViewModel.kt ──

/**
 * ViewModel for the Search screen.
 * Processes Actions, updates State, and emits Events.
 */
class SearchViewModel(
    private val processQueryUseCase: ProcessQueryUseCase,
    private val getRecentQueriesUseCase: GetRecentQueriesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchContract.State())
    val state: StateFlow<SearchContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SearchContract.Event>()
    val events: SharedFlow<SearchContract.Event> = _events.asSharedFlow()

    init {
        loadRecentQueries()
    }

    /**
     * Single entry point for all UI actions.
     * Maps each action to the appropriate handler.
     */
    fun onAction(action: SearchContract.Action) {
        when (action) {
            is SearchContract.Action.QueryChanged -> handleQueryChanged(action.text)
            is SearchContract.Action.SubmitQuery -> handleSubmitQuery()
            is SearchContract.Action.QuickActionClicked -> handleQuickAction(action.quickAction)
            is SearchContract.Action.RecentQueryClicked -> handleRecentQuery(action.query)
            is SearchContract.Action.ClearResults -> handleClearResults()
            is SearchContract.Action.ViewRawDataClicked -> handleViewRawData()
            is SearchContract.Action.SuggestedQueryClicked -> handleSuggestedQuery(action.query)
        }
    }

    private fun handleSubmitQuery() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            processQueryUseCase(query)
                .onSuccess { result ->
                    _state.update { it.copy(isLoading = false, result = result) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toUiError()) }
                    _events.emit(SearchContract.Event.ShowSnackbar(UiText.Resource(R.string.query_failed)))
                }
        }
    }

    // ... other handlers follow same pattern
}
```

```kotlin
// ── Screen root: SearchScreen.kt ──
// This composable connects ViewModel to UI. NO business logic here.

/**
 * Screen root — connects ViewModel to the pure UI content.
 * Collects state, handles events, and passes action lambdas down.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = koinViewModel(),
    onNavigateToTimeline: (LocalDate) -> Unit,
    onNavigateToMap: (LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Collect one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchContract.Event.ShowSnackbar -> { /* show snackbar */ }
                is SearchContract.Event.NavigateToTimeline -> onNavigateToTimeline(event.date)
                is SearchContract.Event.NavigateToMap -> onNavigateToMap(event.date)
                is SearchContract.Event.ScrollToTop -> { /* scroll list to top */ }
            }
        }
    }

    SearchContent(
        state = state,
        onAction = viewModel::onAction,
    )
}
```

```kotlin
// ── Pure UI: SearchContent.kt ──
// This composable is PURE — depends only on state and lambdas.
// Must have @Preview.

/**
 * Pure UI content — receives state and emits actions.
 * No ViewModel, no side effects, fully previewable.
 */
@Composable
fun SearchContent(
    state: SearchContract.State,
    onAction: (SearchContract.Action) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = state.query,
            onQueryChanged = { onAction(SearchContract.Action.QueryChanged(it)) },
            onSubmit = { onAction(SearchContract.Action.SubmitQuery) },
        )

        if (state.isLoading) {
            LoadingIndicator()
        }

        state.result?.let { result ->
            QueryResultCard(
                result = result,
                onViewRawData = { onAction(SearchContract.Action.ViewRawDataClicked) },
            )
        }

        state.error?.let { error ->
            ErrorView(error = error)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(
                query = "Where was I yesterday?",
                result = QueryResult.mock(),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentLoadingPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(isLoading = true),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentEmptyPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(),
            onAction = {},
        )
    }
}
```

### 3. Use Cases (MANDATORY)

Every business operation goes through a use case. Use cases are the single source of business logic.

```kotlin
/**
 * Processes a natural language query and returns structured results.
 *
 * Steps:
 * 1. Parse the raw query into intent + entities + time range
 * 2. Build a data retrieval plan based on parsed query
 * 3. Execute retrieval against repositories
 * 4. Format results into a human-readable response
 *
 * @param query The raw natural language query string
 * @return [Result] containing [QueryResult] on success or an error
 */
class ProcessQueryUseCase(
    private val queryEngine: QueryEngine,
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
    private val timelineRepository: TimelineRepository,
) {
    suspend operator fun invoke(query: String): Result<QueryResult> {
        return runCatching {
            val parsed = queryEngine.parse(query)
            val data = retrieveData(parsed)
            val response = queryEngine.generateResponse(parsed, data)
            QueryResult(parsed = parsed, data = data, response = response)
        }
    }
}
```

**Use case rules:**
- One public `invoke` function (callable with `useCase(params)`)
- Returns `Result<T>` for error handling
- Named descriptively: verb + noun + "UseCase"
- Constructor-injected dependencies only
- No Android imports — pure Kotlin
- Must have KDoc explaining what the use case does

### 4. Repository Pattern

```kotlin
// ── Interface in domain layer ──
/**
 * Repository for location records.
 * Provides access to stored location data with various query capabilities.
 */
interface LocationRepository {
    suspend fun getLocationsInRange(start: Long, end: Long): List<LocationRecord>
    suspend fun getLastKnownLocation(): LocationRecord?
    suspend fun getLocationAt(timestamp: Long, toleranceMs: Long = 300_000): LocationRecord?
    fun observeLocations(start: Long, end: Long): Flow<List<LocationRecord>>
    suspend fun saveLocation(record: LocationRecord)
    suspend fun deleteLocationsInRange(start: Long, end: Long)
}

// ── Implementation in data layer ──
/**
 * SQLDelight-backed implementation of [LocationRepository].
 */
class LocationRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val mapper: LocationMapper,
    private val logger: BlackBoxLogger,
) : LocationRepository {

    override suspend fun getLocationsInRange(start: Long, end: Long): List<LocationRecord> {
        logger.d(TAG, "Fetching locations from $start to $end")
        return withContext(Dispatchers.IO) {
            database.locationRecordQueries
                .getInRange(start, end)
                .executeAsList()
                .map(mapper::toDomain)
        }
    }

    companion object {
        private const val TAG = "LocationRepository"
    }
}
```

### 5. Compose Component Rules

**Every screen has 3 files minimum:**
1. `XxxContract.kt` — State, Action, Event sealed classes
2. `XxxScreen.kt` — Compose root that connects ViewModel (NO UI here, only wiring)
3. `XxxContent.kt` — Pure UI composable with `@Preview` annotations

**Composable rules:**
- Content composables receive `state: State` and `onAction: (Action) -> Unit` only
- NEVER pass ViewModel directly to content composables
- Every content composable has at least 2 `@Preview` functions (default state + populated state)
- Screen-specific components live in `components/` subfolder
- Shared components live in `ui/common/`
- Use `Modifier` as first optional parameter on all composables
- Never hardcode strings — use `stringResource()` or `UiText`

### 6. Logging

Use a custom logger abstraction — never call `Log.d()` directly:

```kotlin
// shared/domain/util/BlackBoxLogger.kt
/**
 * Platform-agnostic logger interface.
 * Android implementation wraps android.util.Log.
 * Logs are for development/debugging only — never log user data.
 */
interface BlackBoxLogger {
    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
```

**Logging rules:**
- NEVER log personal data (locations, app names, WiFi SSIDs) in production builds
- Use `BuildConfig.DEBUG` guard for verbose data logging during development
- Every collector logs: start, stop, collection cycle count, errors
- Every use case logs: invocation and result summary (success/failure, not data)
- Every repository logs: query execution time for performance monitoring

### 7. Error Handling

```kotlin
// Domain errors — shared module
sealed class BlackBoxError {
    data class DatabaseError(val message: String, val cause: Throwable? = null) : BlackBoxError()
    data class QueryParsingError(val query: String, val reason: String) : BlackBoxError()
    data class CollectorError(val collector: CollectorType, val message: String) : BlackBoxError()
    data class PermissionDenied(val permission: String) : BlackBoxError()
    data object InsufficientData : BlackBoxError()
    data object NoResultsFound : BlackBoxError()
}

// UI errors — android module
sealed class UiError {
    data class Snackbar(val message: UiText) : UiError()
    data class FullScreen(val title: UiText, val message: UiText, val retryAction: (() -> Unit)? = null) : UiError()
    data class Inline(val message: UiText) : UiError()
}
```

### 8. Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase, dot-separated | `com.blackbox.domain.usecase` |
| Class | PascalCase | `ProcessQueryUseCase` |
| Interface | PascalCase (no "I" prefix) | `RecordRepository` |
| Function | camelCase, verb-first | `getLocationsInRange()` |
| Property | camelCase | `isLoading`, `collectorType` |
| Constant | UPPER_SNAKE_CASE | `MAX_COLLECTION_INTERVAL` |
| Compose function | PascalCase | `SearchContent()` |
| State field | camelCase, descriptive | `isSearchExpanded` |
| Action | PascalCase, past tense or descriptive | `QueryChanged`, `SubmitQuery` |
| Event | PascalCase, imperative | `ShowSnackbar`, `NavigateToTimeline` |
| File name | Matches primary class | `ProcessQueryUseCase.kt` |
| Test file | Class + Test suffix | `ProcessQueryUseCaseTest.kt` |
| Preview function | Private, class + Preview + variant | `SearchContentLoadingPreview()` |
| Tag (logging) | Class simple name | `companion object { private const val TAG = "LocationCollector" }` |

### 9. KDoc Rules

**EVERY public class, function, and property MUST have KDoc.** This is non-negotiable.

```kotlin
/**
 * Detects known places by clustering frequently visited locations.
 *
 * Runs periodically (default: daily) to analyze location history
 * and identify clusters that represent meaningful places.
 * New clusters are compared against existing [KnownPlace] entries
 * and either matched or created as new auto-detected places.
 *
 * @property locationRepository Source of raw location records
 * @property placeRepository Storage for known places
 * @property clusteringConfig Configuration for the clustering algorithm
 */
class DetectKnownPlacesUseCase(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val clusteringConfig: ClusteringConfig,
) {
    /**
     * Runs place detection on location data from the specified time range.
     *
     * @param startTimestamp Beginning of the analysis window (epoch ms)
     * @param endTimestamp End of the analysis window (epoch ms)
     * @return List of newly detected or updated [KnownPlace] entries
     */
    suspend operator fun invoke(startTimestamp: Long, endTimestamp: Long): Result<List<KnownPlace>> {
        // ...
    }
}
```

**KDoc requirements:**
- Classes: Describe purpose, behavior, and key dependencies
- Use cases: Describe the business operation and steps
- Functions: Describe what it does, parameters, return value
- Complex algorithms: Describe the approach and any non-obvious behavior
- Data classes with non-obvious fields: Document each field
- NO KDoc needed on: private functions (unless complex), obvious getters, test functions

---

## Development Workflow

### When Implementing a New Feature

1. **Read the relevant spec file** in `docs/` first
2. **Start with domain models** — define data classes in `shared/domain/model/`
3. **Define repository interface** — add methods to existing or new repository in `shared/domain/repository/`
4. **Write the use case** — implement business logic in `shared/domain/usecase/`
5. **Write shared tests** — test use case with mocked repository in `shared/src/commonTest/`
6. **Implement repository** — SQLDelight queries + mapper in `shared/data/`
7. **Create the Contract** — State, Action, Event in `androidApp/ui/{screen}/`
8. **Create the ViewModel** — wire use cases, implement action handlers
9. **Create the Content** — pure Compose UI with previews
10. **Create the Screen** — connect ViewModel to Content
11. **Register DI** — add new classes to appropriate Koin modules

### When Implementing a New Collector

1. Read `docs/COLLECTORS.md` for the collector spec
2. Implement the collector in `androidApp/collector/`
3. Extend `BaseCollector` abstract class
4. Define the record data class in `shared/domain/model/record/`
5. Add SQLDelight table if needed
6. Register in `CollectorOrchestrator`
7. Add to Koin `CollectorModule`
8. Add toggle in Settings screen
9. Test collection cycle works with a 1-minute integration test

### When Modifying the Database

1. Update the `.sq` file
2. Create a migration in `shared/data/database/migrations/`
3. Update affected mappers
4. Run shared tests to verify
5. Document changes in `docs/DATABASE.md`

---

## Important Spec Files

| Area of Work | Read This First |
|-------------|----------------|
| Any new feature | `docs/SPEC.md` (section relevant to feature) |
| Architecture decisions | `docs/ARCHITECTURE.md` |
| Database changes | `docs/DATABASE.md` |
| Search / query work | `docs/QUERY_ENGINE.md` |
| New collector | `docs/COLLECTORS.md` |
| Security / encryption | `docs/SECURITY.md` |
| Battery issues | `docs/BATTERY.md` |
| UI / screen design | `docs/UI_SCREENS.md` |
| Code standards | `docs/DEV_SPECS.md` |

---

## Testing Requirements

- **Use cases:** 100% tested with mocked repositories
- **Query engine:** Tested with 50+ real-world query strings
- **Time expression parser:** Tested with edge cases in both English and Hebrew
- **Mappers:** Tested for every mapping direction
- **ViewModels:** Tested for action → state transitions using Turbine
- **Compose previews:** Every content composable has 2+ previews covering different states
- **Collectors:** Integration-tested with real device data where possible

**Test naming:** `fun 'action description — expected result'()`
```kotlin
@Test
fun `submit query with valid text — returns results and clears loading`() { ... }

@Test
fun `submit query with empty text — does nothing`() { ... }
```

---

## Do's and Don'ts

### DO:
- Follow MVI strictly — state in StateFlow, events in SharedFlow, actions through `onAction()`
- Write KDoc on every public API
- Use `Result<T>` for all operations that can fail
- Put business logic in use cases, never in ViewModels
- Use Koin `inject()` in ViewModels, not manual construction
- Keep Compose content functions pure and previewable
- Log collector lifecycle events and errors
- Use string resources for all user-facing text (support Hebrew + English)
- Handle all error states in UI (loading, error, empty, success)

### DON'T:
- Don't put business logic in ViewModels — use cases only
- Don't access repositories directly from ViewModels — go through use cases
- Don't import Android classes in the shared module's domain layer
- Don't use `mutableStateOf` — use `MutableStateFlow` in ViewModels
- Don't use `var` state in ViewModels — always `_state.update { }`
- Don't log personal user data in production
- Don't skip @Preview on content composables
- Don't hardcode strings, dimensions, or colors
- Don't create God-objects — keep classes focused and small
- Don't use `GlobalScope` — always use `viewModelScope` or structured concurrency
- Don't use `!!` — handle nullability properly with `?.`, `?:`, or explicit null checks
- Don't skip error handling — every coroutine launch should have error handling
