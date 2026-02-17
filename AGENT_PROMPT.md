# BlackBox — Claude Code Agent Prompt

## Paste this into Claude Code to start development

---

You are building **BlackBox**, a privacy-first, offline-first personal life flight recorder app using Kotlin Multiplatform (KMP). The Android app is the POC.

## IMPORTANT: Read Documentation First

Before writing ANY code, read these documentation files in order:

1. `CLAUDE.md` — Project structure, architecture rules, MVI pattern, coding conventions
2. `docs/SPEC.md` — Full product specification (features, data types, screens, roadmap)
3. `docs/DEV_SPECS.md` — Development standards (Clean Architecture, MVI, Compose rules, testing)

For specific areas of work, also read:
- `docs/DATABASE.md` — SQLDelight schema, queries, JSON structures
- `docs/COLLECTORS.md` — Each collector's implementation details
- `docs/QUERY_ENGINE.md` — NLP parser, intent classification, response templates
- `docs/SECURITY.md` — Encryption, Keystore, hash chain
- `docs/BATTERY.md` — Battery optimization strategies
- `docs/UI_SCREENS.md` — Screen wireframes, design system, component specs

## Development Approach

Work in **small, incremental steps**. After each step:
1. Make sure the code compiles
2. Briefly explain what was done and what's next
3. Wait for my confirmation before proceeding to the next step

Do NOT try to build everything at once. Follow the step sequence below exactly.

## Step Sequence

### PHASE 1: Project Setup (Steps 1-5)

**Step 1 — Create KMP Project Structure**
- Initialize a new KMP project with Gradle (Kotlin DSL)
- Set up version catalog (`libs.versions.toml`) with all dependencies
- Configure shared module (commonMain, androidMain, iosMain stubs)
- Configure androidApp module
- Ensure the project compiles with an empty MainActivity
- Dependencies to include: Jetpack Compose + Material 3, SQLDelight, Koin (multiplatform), Kotlin Coroutines, Kotlin DateTime, Kotlin Serialization, Google Play Services Location, Google Activity Recognition, OSMDroid
- Set `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`

**Step 2 — Set Up Theme and Base UI**
- Create Material 3 theme (`BlackBoxTheme`) with the color scheme from `docs/UI_SCREENS.md`
- Create `Color.kt`, `Typography.kt`, `Shape.kt`, `Dimens.kt`
- Create `MainActivity` with Compose setup
- Create bottom navigation bar with 5 tabs (Search, Timeline, Map, Insights, Settings)
- Create `NavHost` with empty placeholder screens for each tab
- Each placeholder screen should show the screen name centered
- Create shared UI components: `LoadingIndicator`, `ErrorView`, `EmptyStateView`
- Verify the app runs with working bottom navigation

**Step 3 — Set Up Domain Layer Models**
- Create all domain model data classes in `shared/domain/model/`:
  - `record/`: `CollectedRecord`, `CollectorType` enum, collector-specific data classes (`LocationData`, `ActivityData`, `WifiData`, `AppUsageData`, `ScreenStateData`, `AudioLevelData`, `BatteryData`, `ConnectivityData`, `BarometerData`, `LightData`)
  - `query/`: `ParsedQuery`, `QueryIntent` enum, `QueryResult`, `TimeRange`, `QueryEntity`
  - `timeline/`: `TimelineEntry`, `DailySummary`, `DerivedEvent`, `EventType` enum
  - `place/`: `KnownPlace`, `PlaceCategory` enum
  - `settings/`: `CollectorSetting`, `CollectionProfile` enum
- Create all repository interfaces in `shared/domain/repository/`
- Create `BlackBoxLogger` interface
- All classes must have KDoc documentation

**Step 4 — Set Up SQLDelight Database**
- Create the SQLDelight `.sq` schema file with ALL tables from `docs/DATABASE.md`
- Create ALL queries from `docs/DATABASE.md`
- Set up `DatabaseDriverFactory` with expect/actual pattern
- Android actual: basic SQLite driver (SQLCipher integration comes later)
- Create mapper classes: DB entities ↔ domain models
- Create repository implementations for all repositories
- Verify database creates successfully on app launch

**Step 5 — Set Up Dependency Injection**
- Create all Koin modules: `databaseModule`, `repositoryModule`, `useCaseModule`, `collectorModule`, `viewModelModule`
- Initialize Koin in `BlackBoxApp` Application class
- Create `AndroidLogger` implementation
- Verify DI resolves all dependencies without crashes

### PHASE 2: Data Collection (Steps 6-11)

**Step 6 — Create Collector Infrastructure**
- Create `DataCollector` interface in shared module
- Create `BaseCollector` abstract class in Android module
- Create `CollectorOrchestrator` that manages all collectors
- Create `RecordBatcher` for batching DB writes
- Create `BlackBoxService` foreground service with notification
- Create `BootReceiver` for restart on boot
- Create `SaveRecordUseCase`
- Verify the service starts and shows a notification

**Step 7 — Implement Location Collector**
- Implement `LocationCollector` using FusedLocationProviderClient
- Includes battery-adaptive intervals
- Saves `CollectedRecord` with LOCATION type + `LocationRecord` denormalized entry
- Handle permissions properly
- Test: verify location records appear in the database after 1 minute

**Step 8 — Implement Screen State Collector**
- Implement `ScreenStateCollector` using BroadcastReceiver
- Event-driven (screen on/off/unlock)
- Zero battery impact
- Test: lock/unlock phone, verify records in database

**Step 9 — Implement Activity Collector**
- Implement `ActivityCollector` using Activity Recognition API
- Register for activity transitions
- Include step counter from TYPE_STEP_COUNTER sensor
- Test: walk around, verify WALKING activity records

**Step 10 — Implement App Usage Collector**
- Implement `AppUsageCollector` using UsageStatsManager
- Handle the special permission (guide user to Settings)
- Create `AppInfoCache` for caching package name → display name mapping
- Test: use several apps, verify usage records

**Step 11 — Implement Remaining Collectors**
- Implement `WifiCollector` — periodic WiFi scan
- Implement `BatteryCollector` — BroadcastReceiver for battery changes
- Implement `ConnectivityCollector` — network and Bluetooth state
- Implement `BarometerCollector` — periodic pressure reading (disabled by default)
- Implement `LightCollector` — periodic light reading (disabled by default)
- Implement `AudioLevelCollector` — periodic dB measurement (disabled by default)
- Test: verify all collectors write records to database

### PHASE 3: Search & Query Engine (Steps 12-16)

**Step 12 — Implement Time Expression Parser**
- Create `TimeExpressionParser` in shared module
- Support all English expressions from `docs/QUERY_ENGINE.md`
- Support all Hebrew expressions from `docs/QUERY_ENGINE.md`
- Create `DateTimeProvider` interface (testable clock)
- Write unit tests for at least 30 time expressions (mixed EN/HE)

**Step 13 — Implement Intent Classifier and Entity Extractor**
- Create `IntentClassifier` with keyword-based classification
- Support all 8 intent types
- Create `EntityExtractor` for place names, activities, apps
- Write unit tests for at least 20 query classifications

**Step 14 — Implement Query Builder and Data Retriever**
- Create `QueryBuilder` that converts parsed queries to DB queries
- Implement query plans for each intent type
- Create `ProcessQueryUseCase` that orchestrates the full pipeline
- Test with real data: ask 5 different queries, verify correct data retrieval

**Step 15 — Implement Response Generator**
- Create `ResponseGenerator` with templates for all intent types
- Support both English and Hebrew response templates
- Include suggested follow-up queries
- Test: full pipeline from raw query text → formatted response

**Step 16 — Build Search Screen UI**
- Implement `SearchContract` (State, Action, Event)
- Implement `SearchViewModel`
- Build `SearchContent` with: search bar, quick action chips, recent queries list, result card, suggested follow-ups, raw data expandable section, recording status indicator
- Add `@Preview` functions for all states
- Wire to NavHost
- Test: type a query, see a formatted result

### PHASE 4: Timeline & Map (Steps 17-20)

**Step 17 — Implement Timeline Data Layer**
- Create `GetTimelineUseCase` — builds timeline entries from raw records
- Create `GenerateDailySummaryUseCase` — aggregates a day's data
- Create `DailySummaryWorker` — runs end-of-day summary generation via WorkManager
- Create `DetectKnownPlacesUseCase` — cluster location points into places
- Test: generate timeline for a day with real data

**Step 18 — Build Timeline Screen UI**
- Implement `TimelineContract`, `TimelineViewModel`
- Build `TimelineContent` with: date navigator, day summary card, vertical timeline with event cards, time markers, connecting lines
- Support day view (week and month views can be stubs for now)
- Add `@Preview` functions
- Test: view today's timeline with real data

**Step 19 — Build Map Screen**
- Integrate OSMDroid for offline map rendering
- Implement `MapContract`, `MapViewModel`
- Build `MapContent` with: full map view, location markers, path polylines colored by transport mode, bottom sheet with visited places list
- Add date navigation to switch between days
- Test: view today's locations on map

**Step 20 — Implement Derived Events**
- Create arrival/departure detection algorithm (location transitions between known places)
- Create sleep detection (screen off + still + dark + home for extended period)
- Create `DerivedEvent` records for all detected events
- Update timeline to use derived events for richer display
- Test: verify arrival/departure events appear correctly

### PHASE 5: Insights & Settings (Steps 21-24)

**Step 21 — Build Insights Screen**
- Create `GetPatternsUseCase` — aggregates data for pattern detection
- Create `GetInsightsUseCase` — generates human-readable insights
- Implement `InsightsContract`, `InsightsViewModel`
- Build `InsightsContent` with: period selector, sleep section, activity section, screen time section, insight cards
- Add mini charts using Compose Canvas (simple bar charts)
- Test: view weekly insights with real data

**Step 22 — Build Settings Screen**
- Implement `SettingsContract`, `SettingsViewModel`
- Build `SettingsContent` with: collector toggles (with descriptions and privacy notes), known places management, data retention selector, storage stats, export button, delete data with confirmation, battery profile selector, language selector
- Wire collector toggles to actually enable/disable collectors via the orchestrator
- Test: toggle a collector off, verify it stops collecting

**Step 23 — Build Onboarding Flow**
- Implement `OnboardingContract`, `OnboardingViewModel`
- Build 5-screen horizontal pager: welcome, collector selection, permissions, set home location, ready
- Store "onboarding completed" flag in DataStore
- Show onboarding only on first launch
- Test: clear app data, verify onboarding appears

**Step 24 — Implement Proof Report Generator**
- Create `GenerateProofReportUseCase` — collects evidence from all sources for a time range
- Calculate corroboration score (1 source = 60%, 2 = 80%, 3+ = 95%)
- Generate formatted proof report with all evidence
- Add proof query support to search screen
- Test: ask "prove I was at the office yesterday", verify comprehensive report

### PHASE 6: Security & Polish (Steps 25-28)

**Step 25 — Implement Database Encryption**
- Integrate SQLCipher dependency
- Update `DatabaseDriverFactory` to use encrypted driver
- Implement `AndroidKeyManager` with Android Keystore
- Generate and store master key in hardware-backed Keystore
- Verify database is encrypted (try to read .db file directly — should be unreadable)

**Step 26 — Implement App Lock**
- Add BiometricPrompt authentication on app open
- Implement auto-lock after 1 minute in background
- Add lock/unlock animation
- Test: put app in background for 2 minutes, verify it requires re-authentication

**Step 27 — Implement Hash Chain**
- Add day_hash and previous_day_hash to DailySummary generation
- Create hash chain verification function
- Add "Data Integrity" section to Settings showing chain status
- Test: verify 7 days of hash chain is valid

**Step 28 — Final Polish**
- Add data export functionality (JSON format with password encryption)
- Add data cleanup worker (retention period enforcement)
- Add recording status widget for notification
- Polish all animations and transitions
- Verify all @Preview functions render correctly
- Run full end-to-end test: record for a day, query 10 different questions, view timeline, view map, check insights
- Final code cleanup: remove TODOs, verify all KDoc, run detekt

---

## Code Quality Rules (Apply to EVERY Step)

1. **KDoc on every public class and function** — no exceptions
2. **Logging** — every component logs lifecycle events and errors via `BlackBoxLogger`
3. **Error handling** — every coroutine has proper error handling, use `Result<T>`
4. **Clean Architecture** — domain layer has ZERO Android imports
5. **MVI pattern** — all ViewModels use StateFlow for state, SharedFlow for events, sealed interface Actions
6. **Compose previews** — every Content composable has 2+ @Preview functions
7. **Compose roots** — every screen has Screen (wiring) + Content (pure UI) split
8. **String resources** — all user-facing strings in `strings.xml` (English + Hebrew)
9. **Dimens** — no hardcoded dp/sp values, use `Dimens` object
10. **Theme** — all colors from `BlackBoxTheme`, never hardcoded

## Starting

Begin with **Step 1**. Read the documentation files first, then create the project structure. After completing each step, tell me what you did and what's next. Wait for my go-ahead before proceeding.

Let's build this! 🚀
