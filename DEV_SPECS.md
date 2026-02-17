# BlackBox — Development Specifications & Coding Standards

---

## 1. Architecture Overview

### 1.1 Clean Architecture Layers

BlackBox follows **Clean Architecture** with strict dependency rules. The project is organized into four distinct layers, each with clear responsibilities and boundaries.

```
┌─────────────────────────────────────────────────┐
│                  UI LAYER                        │
│  Jetpack Compose + MVI ViewModels               │
│  Knows about: Domain                            │
├─────────────────────────────────────────────────┤
│               DOMAIN LAYER                       │
│  Use Cases + Models + Repository Interfaces      │
│  Knows about: Nothing (pure Kotlin)             │
├─────────────────────────────────────────────────┤
│                DATA LAYER                        │
│  Repository Implementations + SQLDelight + Maps  │
│  Knows about: Domain                            │
├─────────────────────────────────────────────────┤
│              PLATFORM LAYER                      │
│  Android Services + Collectors + Sensors + Crypto│
│  Knows about: Domain, Data                      │
└─────────────────────────────────────────────────┘
```

**The Dependency Rule:** Dependencies point INWARD only. The domain layer is the innermost layer and has zero knowledge of the outer layers. It never imports Android, Compose, SQLDelight, or any framework class.

### 1.2 Layer Responsibilities

**Domain Layer** (`shared/domain/`)
- Business logic lives here exclusively
- Contains: models (data classes, value objects, enums), use cases, repository interfaces, query engine logic
- 100% pure Kotlin — no platform dependencies
- Testable with zero mocking of platform APIs

**Data Layer** (`shared/data/`)
- Implements repository interfaces defined in domain
- Contains: SQLDelight queries, database configuration, entity mappers
- Translates between database entities and domain models
- Handles data caching, retention, and cleanup logic

**Platform Layer** (`androidApp/collector/`, `androidApp/service/`, `androidApp/security/`)
- Android-specific implementations
- Contains: sensor access, location services, foreground service, encryption, permissions
- Implements `expect/actual` contracts from the shared module
- As thin as possible — delegates to domain layer immediately

**UI Layer** (`androidApp/ui/`)
- Jetpack Compose screens following MVI pattern
- Contains: screens, ViewModels, UI state contracts, composable components, theme
- Delegates all business logic to use cases
- Handles only UI concerns: rendering, animation, navigation, user input

### 1.3 Module Dependency Graph

```
androidApp ──depends on──▶ shared

shared/platform ──depends on──▶ shared/domain
shared/data ──depends on──▶ shared/domain
shared/domain ──depends on──▶ nothing (pure Kotlin)

androidApp/ui ──uses──▶ shared/domain (models, use cases)
androidApp/collector ──uses──▶ shared/domain (models, repositories)
androidApp/di ──wires──▶ everything
```

---

## 2. MVI (Model-View-Intent) Pattern

### 2.1 Core Concepts

MVI enforces a **unidirectional data flow**:

```
UI ──(Action)──▶ ViewModel ──(State)──▶ UI
                     │
                     ├──(Event)──▶ UI (one-time effects)
                     │
                     └──(UseCase)──▶ Domain
```

**State** — A single immutable data class representing the entire UI state. The ViewModel exposes this as `StateFlow<State>`. Compose observes it and renders accordingly. When state changes, Compose recomposes only the affected parts.

**Action** — A sealed interface representing every possible user interaction. The UI dispatches actions to the ViewModel's single `onAction(action)` entry point. Actions are named descriptively and carry only the data needed.

**Event** — A sealed interface for one-time effects that should NOT be replayed on configuration change. Exposed as `SharedFlow<Event>`. Used for navigation, snackbars, toasts, scroll commands. Events are consumed exactly once.

### 2.2 Contract File Structure

Every screen defines its MVI contract in a dedicated file:

```kotlin
/**
 * MVI contract for [ScreenName].
 * Defines the complete set of states, user actions, and one-time events.
 */
object TimelineContract {

    /**
     * Represents the complete UI state of the Timeline screen.
     * All fields have sensible defaults for initial state.
     */
    data class State(
        val selectedDate: LocalDate = LocalDate.now(),
        val viewMode: ViewMode = ViewMode.DAY,
        val timelineEntries: List<TimelineEntry> = emptyList(),
        val isLoading: Boolean = false,
        val error: UiError? = null,
        val dailySummary: DailySummary? = null,
    ) {
        /**
         * Derived property — avoids duplication in state.
         * Compose reads this for conditional rendering.
         */
        val isEmpty: Boolean get() = timelineEntries.isEmpty() && !isLoading

        enum class ViewMode { DAY, WEEK, MONTH }
    }

    sealed interface Action {
        data class DateSelected(val date: LocalDate) : Action
        data class ViewModeChanged(val mode: State.ViewMode) : Action
        data object NextDay : Action
        data object PreviousDay : Action
        data class EntryClicked(val entry: TimelineEntry) : Action
        data object Refresh : Action
    }

    sealed interface Event {
        data class ShowSnackbar(val message: UiText) : Event
        data class NavigateToDetail(val entryId: Long) : Event
    }
}
```

### 2.3 ViewModel Implementation Rules

```kotlin
class TimelineViewModel(
    private val getTimelineUseCase: GetTimelineUseCase,
    private val getDailySummaryUseCase: GetDailySummaryUseCase,
) : ViewModel() {

    // ── State: Single source of truth ──
    private val _state = MutableStateFlow(TimelineContract.State())
    val state: StateFlow<TimelineContract.State> = _state.asStateFlow()

    // ── Events: One-time effects ──
    private val _events = MutableSharedFlow<TimelineContract.Event>()
    val events: SharedFlow<TimelineContract.Event> = _events.asSharedFlow()

    init {
        loadTimeline()
    }

    // ── Single action entry point ──
    fun onAction(action: TimelineContract.Action) {
        when (action) {
            is TimelineContract.Action.DateSelected -> handleDateSelected(action.date)
            is TimelineContract.Action.ViewModeChanged -> handleViewModeChanged(action.mode)
            is TimelineContract.Action.NextDay -> handleNextDay()
            is TimelineContract.Action.PreviousDay -> handlePreviousDay()
            is TimelineContract.Action.EntryClicked -> handleEntryClicked(action.entry)
            is TimelineContract.Action.Refresh -> loadTimeline()
        }
    }

    // ── Private handlers ──

    private fun handleDateSelected(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
        loadTimeline()
    }

    private fun loadTimeline() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            getTimelineUseCase(
                date = _state.value.selectedDate,
                mode = _state.value.viewMode,
            ).onSuccess { entries ->
                _state.update { it.copy(
                    isLoading = false,
                    timelineEntries = entries,
                ) }
            }.onFailure { error ->
                _state.update { it.copy(
                    isLoading = false,
                    error = UiError.Inline(error.toUiText()),
                ) }
            }

            // Load summary in parallel
            getDailySummaryUseCase(_state.value.selectedDate)
                .onSuccess { summary ->
                    _state.update { it.copy(dailySummary = summary) }
                }
        }
    }
}
```

**ViewModel rules:**
- `_state` is ALWAYS `MutableStateFlow` — never `mutableStateOf`
- State updates use `_state.update { it.copy(...) }` — never direct assignment
- `_events` is ALWAYS `MutableSharedFlow` — never `Channel`
- All coroutines use `viewModelScope.launch` — never `GlobalScope`
- Every `launch` block has error handling (either via `Result` or try-catch)
- No Android imports in ViewModel (except lifecycle-viewmodel)
- Business logic is delegated to use cases — ViewModel only orchestrates

### 2.4 Screen Root vs Content Split

**Screen Root** (`XxxScreen.kt`) — Connects ViewModel to UI:

```kotlin
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = koinViewModel(),
    onNavigateToDetail: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Collect one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TimelineContract.Event.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message.asString(context))
                }
                is TimelineContract.Event.NavigateToDetail -> {
                    onNavigateToDetail(event.entryId)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        TimelineContent(
            state = state,
            onAction = viewModel::onAction,
            modifier = Modifier.padding(padding),
        )
    }
}
```

**Content** (`XxxContent.kt`) — Pure, previewable UI:

```kotlin
@Composable
fun TimelineContent(
    state: TimelineContract.State,
    onAction: (TimelineContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pure UI rendering based on state
    // Calls onAction() for user interactions
    // NO ViewModel, NO side effects, NO context-dependent logic
}
```

**This split is MANDATORY for every screen.** It enables:
- `@Preview` on all content composables
- Testing UI logic without a ViewModel
- Clear separation between wiring and rendering

---

## 3. Compose UI Standards

### 3.1 Preview Requirements

Every content composable MUST have `@Preview` functions covering:
1. **Default/empty state** — what the user sees before any data loads
2. **Populated state** — realistic data showing the feature working
3. **Loading state** — if the screen has loading indicators
4. **Error state** — if the screen can show errors

```kotlin
@Preview(showBackground = true, name = "Default")
@Composable
private fun TimelineContentDefaultPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true, name = "With Data")
@Composable
private fun TimelineContentPopulatedPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(
                timelineEntries = PreviewData.timelineEntries,
                dailySummary = PreviewData.dailySummary,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun TimelineContentLoadingPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(isLoading = true),
            onAction = {},
        )
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun TimelineContentErrorPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(
                error = UiError.Inline(UiText.Direct("Failed to load timeline")),
            ),
            onAction = {},
        )
    }
}
```

**Preview data:** Create a `PreviewData` object with realistic mock data for each screen. Keep it in a `preview/` package within the UI module.

### 3.2 Composable Function Conventions

```kotlin
/**
 * Displays a single timeline entry with icon, time, and description.
 *
 * @param entry The timeline entry to display
 * @param onClick Called when the user taps this entry
 * @param modifier Modifier for external layout customization
 */
@Composable
fun TimelineEntryCard(
    entry: TimelineEntry,       // Required params first
    onClick: () -> Unit,         // Callbacks second
    modifier: Modifier = Modifier, // Modifier always last with default
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        // ...
    }
}
```

**Rules:**
- `Modifier` is always the LAST parameter with `Modifier` as default
- Required parameters come first, optional parameters and callbacks after
- Content composables accept `onAction: (Action) -> Unit`, not individual callbacks
- Sub-components can accept specific callbacks for clarity
- Never use hardcoded `dp`, `sp`, or color values — use theme/dimens

### 3.3 Material 3 Theme

```kotlin
// ui/theme/BlackBoxTheme.kt

@Composable
fun BlackBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColorScheme(
        primary = BlackBoxColors.Primary,
        onPrimary = BlackBoxColors.OnPrimary,
        primaryContainer = BlackBoxColors.PrimaryContainer,
        secondary = BlackBoxColors.Secondary,
        background = BlackBoxColors.DarkBackground,
        surface = BlackBoxColors.DarkSurface,
        error = BlackBoxColors.Error,
    ) else lightColorScheme(
        primary = BlackBoxColors.Primary,
        onPrimary = BlackBoxColors.OnPrimary,
        primaryContainer = BlackBoxColors.PrimaryContainer,
        secondary = BlackBoxColors.Secondary,
        background = BlackBoxColors.LightBackground,
        surface = BlackBoxColors.LightSurface,
        error = BlackBoxColors.Error,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BlackBoxTypography,
        shapes = BlackBoxShapes,
        content = content,
    )
}
```

### 3.4 Dimensions System

```kotlin
// ui/theme/Dimens.kt

/**
 * Centralized dimension values for consistent spacing throughout the app.
 * All values follow the 4dp grid system per Material Design guidelines.
 */
object Dimens {
    // Spacing
    val SpacingXxs = 2.dp
    val SpacingXs = 4.dp
    val SpacingSm = 8.dp
    val SpacingMd = 12.dp
    val SpacingLg = 16.dp
    val SpacingXl = 24.dp
    val SpacingXxl = 32.dp
    val SpacingXxxl = 48.dp

    // Content padding
    val ScreenPaddingHorizontal = 16.dp
    val ScreenPaddingVertical = 16.dp
    val CardPadding = 16.dp
    val ListItemPadding = 12.dp

    // Component sizes
    val IconSizeSm = 16.dp
    val IconSizeMd = 24.dp
    val IconSizeLg = 32.dp
    val IconSizeXl = 48.dp

    val ButtonHeight = 48.dp
    val SearchBarHeight = 56.dp
    val BottomBarHeight = 80.dp
    val TopBarHeight = 64.dp

    // Corner radius
    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusXl = 24.dp
    val RadiusFull = 50.dp

    // Elevation
    val ElevationNone = 0.dp
    val ElevationSm = 1.dp
    val ElevationMd = 4.dp
    val ElevationLg = 8.dp
}
```

**Usage:** `Modifier.padding(Dimens.SpacingLg)` — never `Modifier.padding(16.dp)`

### 3.5 UI State Handling Pattern

Every screen must handle all four states:

```kotlin
@Composable
fun TimelineContent(
    state: TimelineContract.State,
    onAction: (TimelineContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.timelineEntries.isEmpty() -> {
                // Full-screen loading — only when no data yet
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.error != null && state.timelineEntries.isEmpty() -> {
                // Full-screen error — only when no data to show
                ErrorView(
                    error = state.error,
                    onRetry = { onAction(TimelineContract.Action.Refresh) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.isEmpty -> {
                // Empty state — no data for this date
                EmptyStateView(
                    icon = Icons.Outlined.Timeline,
                    title = stringResource(R.string.timeline_empty_title),
                    message = stringResource(R.string.timeline_empty_message),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                // Success — show content
                TimelineList(
                    entries = state.timelineEntries,
                    summary = state.dailySummary,
                    onAction = onAction,
                )

                // Overlay loading indicator if refreshing with existing data
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}
```

### 3.6 Common UI Components

Build these reusable components early and use them consistently:

| Component | File | Usage |
|-----------|------|-------|
| `LoadingIndicator` | `common/LoadingIndicator.kt` | Centered circular progress |
| `ErrorView` | `common/ErrorView.kt` | Full-screen error with retry button |
| `EmptyStateView` | `common/EmptyStateView.kt` | Icon + title + message for empty screens |
| `BlackBoxTopBar` | `common/BlackBoxTopBar.kt` | Consistent top app bar |
| `BlackBoxBottomBar` | `common/BlackBoxBottomBar.kt` | Bottom navigation bar |
| `StatusChip` | `common/StatusChip.kt` | Recording status indicator |
| `DateNavigator` | `common/DateNavigator.kt` | Previous/date/next horizontal nav |
| `SectionHeader` | `common/SectionHeader.kt` | Section divider with title |
| `ConfidenceBadge` | `common/ConfidenceBadge.kt` | Data confidence percentage badge |

### 3.7 Accessibility Standards

- All interactive elements have `contentDescription`
- Minimum touch target size: 48dp × 48dp
- Text contrast ratio: minimum 4.5:1 (AA standard)
- Support TalkBack navigation
- Semantic grouping with `Modifier.semantics { }`
- No information conveyed by color alone — always pair with icon or text

---

## 4. Data Flow Patterns

### 4.1 Complete Action Flow Example

```
User taps "Submit" button
        │
        ▼
SearchContent: onAction(SearchContract.Action.SubmitQuery)
        │
        ▼
SearchScreen: viewModel::onAction receives it
        │
        ▼
SearchViewModel.onAction(Action.SubmitQuery)
        │
        ▼
SearchViewModel.handleSubmitQuery()
    1. _state.update { it.copy(isLoading = true) }
    2. processQueryUseCase(query)
        │
        ▼
ProcessQueryUseCase.invoke(query)
    1. queryEngine.parse(query) → ParsedQuery
    2. recordRepository.getRecords(timeRange) → List<Record>
    3. queryEngine.generateResponse(parsed, data) → ResponseText
    4. return Result.success(QueryResult(...))
        │
        ▼
SearchViewModel receives Result
    .onSuccess → _state.update { it.copy(isLoading = false, result = result) }
    .onFailure → _state.update { it.copy(isLoading = false, error = ...) }
                 _events.emit(Event.ShowSnackbar(...))
        │
        ▼
SearchContent recomposes with new state
    Shows QueryResultCard with the answer
```

### 4.2 Event Flow (One-Time Effects)

```kotlin
// ViewModel emits event:
_events.emit(SearchContract.Event.NavigateToTimeline(date))

// Screen root collects event:
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is Event.NavigateToTimeline -> onNavigateToTimeline(event.date)
            is Event.ShowSnackbar -> snackbarHostState.showSnackbar(...)
        }
    }
}

// NavHost handles navigation:
composable("search") {
    SearchScreen(
        onNavigateToTimeline = { date ->
            navController.navigate("timeline/${date}")
        }
    )
}
```

**Event vs State decision:**
- If it should persist across configuration changes → **State**
- If it should happen once and be forgotten → **Event**
- Navigation → Event
- Snackbar/Toast → Event
- Dialog showing → State (so it survives rotation)
- Loading indicator → State
- Error message → State (inline) or Event (snackbar)

---

## 5. Use Case Standards

### 5.1 Structure

```kotlin
/**
 * [Brief description of what this use case does].
 *
 * [Longer description of the business logic, steps involved,
 * and any important behavior details.]
 *
 * @property dependency1 [What this dependency provides]
 * @property dependency2 [What this dependency provides]
 */
class VerbNounUseCase(
    private val dependency1: Type1,
    private val dependency2: Type2,
    private val logger: BlackBoxLogger,
) {

    /**
     * [Description of the operation]
     *
     * @param param1 [Description]
     * @return [Result] containing [ReturnType] on success, or:
     * - [BlackBoxError.X] when [condition]
     * - [BlackBoxError.Y] when [condition]
     */
    suspend operator fun invoke(param1: Type): Result<ReturnType> {
        logger.d(TAG, "Executing with param1=$param1")
        return runCatching {
            // Business logic here
        }.onFailure { error ->
            logger.e(TAG, "Failed: ${error.message}", error)
        }
    }

    companion object {
        private const val TAG = "VerbNounUseCase"
    }
}
```

### 5.2 Use Case Rules

1. **Single public function:** Only `operator fun invoke()`. No other public methods.
2. **Returns `Result<T>`:** Always wrap the return in Kotlin's `Result` type.
3. **Constructor injection:** All dependencies via constructor. No property injection.
4. **No Android imports:** Use cases live in the shared module's domain layer.
5. **Logging:** Log invocation and outcome (success/failure), never log personal data.
6. **Naming:** `VerbNounUseCase` — e.g., `GetTimelineUseCase`, `ProcessQueryUseCase`, `SaveRecordUseCase`, `DetectKnownPlacesUseCase`.
7. **Focused:** Each use case does ONE thing. If logic branches significantly, create separate use cases.

### 5.3 Use Case Catalog

| Use Case | Package | Description |
|----------|---------|-------------|
| `ProcessQueryUseCase` | `query/` | Parse NL query → retrieve data → generate response |
| `ParseTimeExpressionUseCase` | `query/` | Extract time ranges from natural language text |
| `GetTimelineUseCase` | `timeline/` | Build timeline entries for a date/range |
| `GenerateDailySummaryUseCase` | `timeline/` | Aggregate raw records into a daily summary |
| `SaveRecordUseCase` | `record/` | Validate and persist a collected record |
| `GetRecordsInRangeUseCase` | `record/` | Retrieve raw records for a time range by type |
| `DeleteRecordsInRangeUseCase` | `record/` | Delete records in a date range (user-initiated) |
| `DetectKnownPlacesUseCase` | `place/` | Cluster locations to identify frequent places |
| `GetKnownPlacesUseCase` | `place/` | Retrieve all known places |
| `UpdateKnownPlaceUseCase` | `place/` | Rename or recategorize a known place |
| `GetPatternsUseCase` | `insight/` | Analyze data for behavioral patterns |
| `GetInsightsUseCase` | `insight/` | Generate human-readable insights from patterns |
| `GenerateProofReportUseCase` | `proof/` | Create evidence report for a time range |
| `GetCollectorSettingsUseCase` | `settings/` | Retrieve all collector enable/disable states |
| `UpdateCollectorSettingUseCase` | `settings/` | Toggle a collector on/off |
| `GetStorageStatsUseCase` | `settings/` | Calculate database size and record counts |
| `ExportDataUseCase` | `settings/` | Export user data as encrypted JSON |
| `CleanupOldRecordsUseCase` | `settings/` | Delete records older than retention period |

---

## 6. Dependency Injection (Koin)

### 6.1 Module Organization

```kotlin
// di/AppModule.kt — top-level wiring
val appModule = module {
    includes(
        databaseModule,
        repositoryModule,
        useCaseModule,
        collectorModule,
        viewModelModule,
    )
}

// di/DatabaseModule.kt
val databaseModule = module {
    single { DatabaseDriverFactory(get()).create() }
    single { BlackBoxDatabase(get()) }
}

// di/RepositoryModule.kt
val repositoryModule = module {
    single<RecordRepository> { RecordRepositoryImpl(get(), get(), get()) }
    single<LocationRepository> { LocationRepositoryImpl(get(), get(), get()) }
    single<TimelineRepository> { TimelineRepositoryImpl(get(), get(), get()) }
    single<PlaceRepository> { PlaceRepositoryImpl(get(), get(), get()) }
    single<InsightRepository> { InsightRepositoryImpl(get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
}

// di/UseCaseModule.kt
val useCaseModule = module {
    factory { ProcessQueryUseCase(get(), get(), get(), get()) }
    factory { GetTimelineUseCase(get(), get()) }
    factory { GenerateDailySummaryUseCase(get(), get(), get()) }
    factory { SaveRecordUseCase(get(), get()) }
    factory { DetectKnownPlacesUseCase(get(), get(), get()) }
    factory { GetPatternsUseCase(get(), get()) }
    factory { GenerateProofReportUseCase(get(), get(), get(), get()) }
    factory { GetCollectorSettingsUseCase(get()) }
    factory { UpdateCollectorSettingUseCase(get(), get()) }
    // ... all other use cases
}

// di/ViewModelModule.kt
val viewModelModule = module {
    viewModel { SearchViewModel(get(), get()) }
    viewModel { TimelineViewModel(get(), get()) }
    viewModel { MapViewModel(get(), get()) }
    viewModel { InsightsViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { OnboardingViewModel(get(), get()) }
}

// di/CollectorModule.kt
val collectorModule = module {
    single { CollectorOrchestrator(get(), get(), get()) }
    factory { LocationCollector(get(), get(), get()) }
    factory { ActivityCollector(get(), get()) }
    factory { ScreenStateCollector(get(), get()) }
    factory { WifiCollector(get(), get()) }
    factory { AppUsageCollector(get(), get()) }
    factory { BatteryCollector(get(), get()) }
    factory { ConnectivityCollector(get(), get()) }
    // ... all other collectors
}
```

### 6.2 Koin Rules

- **Repositories:** `single` — one instance shared across the app
- **Use cases:** `factory` — new instance per injection (stateless, cheap)
- **ViewModels:** `viewModel` — lifecycle-scoped
- **Collectors:** `factory` or `single` depending on whether they hold state
- **Database:** `single` — one database instance
- **Logger:** `single` — one logger instance

### 6.3 ViewModel Injection in Compose

```kotlin
// Always use koinViewModel() in the Screen root, never in Content
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = koinViewModel(), // ← here
    onNavigateToTimeline: (LocalDate) -> Unit,
) { ... }
```

---

## 7. Logging Standards

### 7.1 Logger Interface

```kotlin
interface BlackBoxLogger {
    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

// Android implementation
class AndroidLogger : BlackBoxLogger {
    override fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("BB_$tag", message)
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e("BB_$tag", message, throwable) // errors always logged
    }
}
```

### 7.2 What to Log

| Component | Log Level | What to Log |
|-----------|-----------|-------------|
| Collector | `i` | Start/stop, collection cycle count |
| Collector | `d` | Individual collection result (DEBUG only) |
| Collector | `e` | Collection failures with error details |
| UseCase | `d` | Invocation with param summary (no personal data) |
| UseCase | `d` | Result: success/failure |
| UseCase | `e` | Failures with exception |
| Repository | `d` | Query execution time (for perf monitoring) |
| Repository | `e` | Database errors |
| ViewModel | `d` | Action received (action type only) |
| ViewModel | `e` | Unhandled errors |
| Service | `i` | Service lifecycle (create, start, stop, destroy) |
| Service | `w` | Battery profile changes |

### 7.3 What NEVER to Log

- GPS coordinates
- WiFi SSIDs or BSSIDs
- App package names from usage
- Bluetooth device names
- Any data that could identify the user or their behavior
- Query text (contains personal questions)

**In DEBUG builds only:** Verbose data logging is acceptable for development using `if (BuildConfig.DEBUG)` guards.

---

## 8. String Resources & Localization

### 8.1 Rules

- **Every user-facing string** goes in `strings.xml` — no hardcoded strings in Compose
- Support two languages: English (default) and Hebrew
- String resource names: `snake_case` with screen prefix

```xml
<!-- English: values/strings.xml -->
<string name="search_hint">Ask anything about your day</string>
<string name="search_loading">Searching your black box…</string>
<string name="search_no_results">No data found for this query</string>
<string name="timeline_empty_title">No Activity</string>
<string name="timeline_empty_message">No data recorded for this date</string>
<string name="settings_collector_location">Location</string>
<string name="settings_collector_location_desc">Track where you are throughout the day</string>

<!-- Hebrew: values-iw/strings.xml -->
<string name="search_hint">שאל כל שאלה על היום שלך</string>
<string name="search_loading">מחפש בקופסה השחורה שלך…</string>
```

### 8.2 UiText Wrapper

For strings that originate in ViewModels (which can't access `Context`):

```kotlin
/**
 * Wrapper for UI text that can be either a string resource or a direct string.
 * Allows ViewModels to specify text without Android Context dependency.
 */
sealed class UiText {
    data class Resource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText()
    data class Direct(val text: String) : UiText()

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(resId, *args.toTypedArray())
        is Direct -> text
    }

    fun asString(context: Context): String = when (this) {
        is Resource -> context.getString(resId, *args.toTypedArray())
        is Direct -> text
    }
}
```

---

## 9. Testing Standards

### 9.1 Test Structure

```
shared/src/commonTest/
├── domain/
│   ├── usecase/
│   │   ├── ProcessQueryUseCaseTest.kt
│   │   ├── GetTimelineUseCaseTest.kt
│   │   └── ...
│   ├── query/
│   │   ├── TimeExpressionParserTest.kt
│   │   ├── IntentClassifierTest.kt
│   │   └── QueryEngineTest.kt
│   └── model/
│       └── ...
├── data/
│   ├── repository/
│   │   └── RecordRepositoryImplTest.kt
│   └── mapper/
│       └── RecordMapperTest.kt

androidApp/src/test/
├── ui/
│   ├── search/SearchViewModelTest.kt
│   ├── timeline/TimelineViewModelTest.kt
│   └── ...
├── collector/
│   └── LocationCollectorTest.kt
└── testutil/
    ├── FakeRecordRepository.kt
    ├── FakeLocationRepository.kt
    ├── TestData.kt
    └── MainDispatcherRule.kt
```

### 9.2 Test Naming

```kotlin
@Test
fun `parse query with yesterday afternoon — returns correct time range`() { ... }

@Test
fun `submit query — shows loading then displays result`() { ... }

@Test
fun `submit empty query — does nothing and state unchanged`() { ... }

@Test
fun `location collector with low battery — reduces polling interval`() { ... }
```

Format: `` `scenario description — expected outcome` ``

### 9.3 ViewModel Testing with Turbine

```kotlin
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeProcessQueryUseCase = FakeProcessQueryUseCase()
    private val fakeGetRecentQueriesUseCase = FakeGetRecentQueriesUseCase()

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        viewModel = SearchViewModel(
            processQueryUseCase = fakeProcessQueryUseCase,
            getRecentQueriesUseCase = fakeGetRecentQueriesUseCase,
        )
    }

    @Test
    fun `submit query with valid text — shows loading then result`() = runTest {
        val expectedResult = TestData.queryResult()
        fakeProcessQueryUseCase.result = Result.success(expectedResult)

        viewModel.state.test {
            // Initial state
            val initial = awaitItem()
            assertThat(initial.isLoading).isFalse()

            // Type query
            viewModel.onAction(SearchContract.Action.QueryChanged("Where was I yesterday?"))
            val withQuery = awaitItem()
            assertThat(withQuery.query).isEqualTo("Where was I yesterday?")

            // Submit
            viewModel.onAction(SearchContract.Action.SubmitQuery)
            val loading = awaitItem()
            assertThat(loading.isLoading).isTrue()

            // Result
            val result = awaitItem()
            assertThat(result.isLoading).isFalse()
            assertThat(result.result).isEqualTo(expectedResult)
        }
    }

    @Test
    fun `submit query failure — shows error and emits snackbar event`() = runTest {
        fakeProcessQueryUseCase.result = Result.failure(Exception("DB error"))

        viewModel.events.test {
            viewModel.onAction(SearchContract.Action.QueryChanged("test"))
            viewModel.onAction(SearchContract.Action.SubmitQuery)

            val event = awaitItem()
            assertThat(event).isInstanceOf(SearchContract.Event.ShowSnackbar::class.java)
        }
    }
}
```

### 9.4 Use Case Testing

```kotlin
class ProcessQueryUseCaseTest {

    private val fakeQueryEngine = FakeQueryEngine()
    private val fakeRecordRepository = FakeRecordRepository()
    private val fakeLocationRepository = FakeLocationRepository()

    private val useCase = ProcessQueryUseCase(
        queryEngine = fakeQueryEngine,
        recordRepository = fakeRecordRepository,
        locationRepository = fakeLocationRepository,
        timelineRepository = FakeTimelineRepository(),
    )

    @Test
    fun `valid location query — returns location result`() = runTest {
        // Given
        fakeQueryEngine.parsedQuery = ParsedQuery(
            intent = QueryIntent.LOCATION_QUERY,
            timeRange = TimeRange.yesterday(),
        )
        fakeLocationRepository.locations = listOf(TestData.locationRecord())

        // When
        val result = useCase("Where was I yesterday?")

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().data).isNotEmpty()
    }
}
```

---

## 10. Git Conventions

### 10.1 Branch Naming

```
feature/search-screen
feature/location-collector
feature/query-engine-time-parser
fix/battery-drain-on-stationary
refactor/collector-base-class
docs/query-engine-spec
```

### 10.2 Commit Messages

```
feat(search): implement natural language query parsing

Add RuleBasedQueryParser with support for English and Hebrew
time expressions. Handles relative dates (yesterday, last week),
absolute dates (January 15), and time-of-day modifiers
(morning, afternoon, evening).

Closes #12
```

Format: `type(scope): short description`

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `style`

### 10.3 PR Requirements

- Descriptive title matching commit convention
- Description explains WHAT and WHY
- All tests pass
- New code has KDoc
- New UI has previews
- No personal data in logs
- Detekt passes with zero issues

---

## 11. Performance Guidelines

### 11.1 Database

- Index all columns used in WHERE clauses (already defined in schema)
- Use `withContext(Dispatchers.IO)` for all DB operations
- Paginate large result sets (max 500 records per query)
- Use SQLDelight's `Flow` queries for reactive UI updates
- Run aggregation queries (daily summaries) in WorkManager, not on UI thread

### 11.2 Compose

- Use `remember` and `derivedStateOf` for expensive computations
- Use `LazyColumn` for lists — never `Column` with `forEach`
- Avoid recomposition by using stable types in state (data classes, primitives)
- Use `key()` in `LazyColumn` items for efficient diffing
- Profile with Layout Inspector if UI feels sluggish

### 11.3 Collections & Background

- Batch sensor readings — don't write to DB on every single reading
- Use `PowerManager.WakeLock` sparingly and always release
- Prefer event-driven collection (BroadcastReceiver) over polling where possible
- Group alarms with `setInexactRepeating` for Android battery optimization

---

## 12. Security Checklist

- [ ] Database encrypted with SQLCipher
- [ ] Encryption key stored in Android Keystore (hardware-backed)
- [ ] App requires biometric/PIN to open
- [ ] No data exported via ContentProvider
- [ ] All components `android:exported="false"`
- [ ] No personal data in logs (production builds)
- [ ] Export files encrypted with user-chosen password
- [ ] ProGuard/R8 enabled for release builds
- [ ] No sensitive data in `SharedPreferences` (use EncryptedSharedPreferences)
- [ ] Hash chain integrity for daily summaries
