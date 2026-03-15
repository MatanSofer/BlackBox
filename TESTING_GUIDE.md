# TESTING_GUIDE.md — Testing Strategy, Patterns, and Examples

Everything you need to write effective tests for BlackBox.

---

## Test Philosophy

BlackBox follows a pragmatic testing pyramid:

```
         ┌─────┐
         │ E2E │  (manual, device testing)
        ╱       ╲
       ╱  Integration ╲  (WorkManager, Service — minimal)
      ╱─────────────────╲
     ╱    Unit Tests      ╲  (80%+ of test effort)
    ╱──────────────────────╲
```

**Priority:** Use cases > Query engine > ViewModels > Mappers > Repositories

Unit tests run on the JVM — fast, no emulator needed. The shared module tests run as `commonTest` which compiles to JVM for fast execution.

---

## Test Setup

### Dependencies (already configured in `build.gradle.kts`)

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit5.api)
            implementation(libs.mockk)
            implementation(libs.robolectric)
        }
    }
}
```

### Running Tests

```bash
# Run shared (common) tests — fast, JVM only
./gradlew :shared:testDebugUnitTest

# Run Android unit tests
./gradlew :androidApp:testDebugUnitTest

# Run with coverage report
./gradlew :shared:testDebugUnitTest jacocoTestReport

# Run a single test class
./gradlew :shared:testDebugUnitTest --tests "*.GetTimelineUseCaseTest"

# Run with verbose output
./gradlew :shared:testDebugUnitTest --info
```

---

## Fake Implementations (Preferred over Mocks)

BlackBox uses **fake** repository implementations instead of MockK mocks for use case testing. Fakes are more readable, more maintainable, and behave more like real code.

### FakeRecordRepository

```kotlin
// shared/src/commonTest/kotlin/com/blackbox/fakes/FakeRecordRepository.kt

class FakeRecordRepository : RecordRepository {

    private val records = mutableListOf<CollectedRecord>()
    var nextId = 1L

    fun addRecord(record: CollectedRecord) = records.add(record)

    fun clear() = records.clear()

    override suspend fun saveRecord(record: CollectedRecord): Long {
        records.add(record)
        return nextId++
    }

    override suspend fun getRecordsByTypeInRange(
        type: CollectorType,
        start: Long,
        end: Long,
    ): List<CollectedRecord> {
        return records
            .filter { it.collectorType == type && it.timestamp in start..end }
    }

    override suspend fun getLatestRecord(type: CollectorType): CollectedRecord? {
        return records.filter { it.collectorType == type }.maxByOrNull { it.timestamp }
    }

    override suspend fun deleteRecordsBefore(timestamp: Long) {
        records.removeAll { it.timestamp < timestamp }
    }

    override fun observeRecordsByType(type: CollectorType): Flow<List<CollectedRecord>> {
        return flowOf(records.filter { it.collectorType == type })
    }
}
```

### FakeLocationRepository

```kotlin
class FakeLocationRepository : LocationRepository {

    private val locations = mutableListOf<LocationRecord>()

    fun addLocation(record: LocationRecord) = locations.add(record)

    override suspend fun getLocationsInRange(start: Long, end: Long): List<LocationRecord> {
        return locations.filter { it.timestamp in start..end }
    }

    override suspend fun getLastKnownLocation(): LocationRecord? =
        locations.maxByOrNull { it.timestamp }

    override suspend fun getLocationAt(timestamp: Long, toleranceMs: Long): LocationRecord? =
        locations.minByOrNull { kotlin.math.abs(it.timestamp - timestamp) }
            ?.takeIf { kotlin.math.abs(it.timestamp - timestamp) <= toleranceMs }

    override fun observeLocations(start: Long, end: Long): Flow<List<LocationRecord>> =
        flowOf(locations.filter { it.timestamp in start..end })

    override suspend fun saveLocation(record: LocationRecord) = locations.add(record).let {}

    override suspend fun deleteLocationsBefore(timestamp: Long) {
        locations.removeAll { it.timestamp < timestamp }
    }
}
```

### FakePlaceRepository

```kotlin
class FakePlaceRepository : PlaceRepository {

    private val places = mutableListOf<KnownPlace>()

    fun addPlace(place: KnownPlace) = places.add(place)

    override suspend fun getAllPlaces(): List<KnownPlace> = places.toList()

    override suspend fun findNearestPlace(lat: Double, lon: Double): KnownPlace? {
        return places.minByOrNull { place ->
            haversineDistance(lat, lon, place.latitude, place.longitude)
        }?.takeIf { place ->
            haversineDistance(lat, lon, place.latitude, place.longitude) <= place.radiusMeters
        }
    }

    override suspend fun findPlaceByWifiBssid(bssid: String): KnownPlace? {
        return places.firstOrNull { it.wifiFingerprint.contains(bssid) }
    }

    override suspend fun savePlace(place: KnownPlace): Long {
        places.add(place)
        return places.size.toLong()
    }

    override suspend fun deletePlace(id: Long) {
        places.removeAll { it.id == id }
    }

    override fun observePlaces(): Flow<List<KnownPlace>> = flowOf(places.toList())
}
```

---

## Use Case Tests

### GetTimelineUseCase

```kotlin
// shared/src/commonTest/kotlin/com/blackbox/domain/usecase/timeline/GetTimelineUseCaseTest.kt

class GetTimelineUseCaseTest {

    private val fakeLocationRepo = FakeLocationRepository()
    private val fakeRecordRepo = FakeRecordRepository()
    private val fakePlaceRepo = FakePlaceRepository()

    private val useCase = GetTimelineUseCase(
        locationRepository = fakeLocationRepo,
        recordRepository = fakeRecordRepo,
        placeRepository = fakePlaceRepo,
    )

    @BeforeEach
    fun setup() {
        fakeLocationRepo.clear()
        fakeRecordRepo.clear()
        fakePlaceRepo.clear()
    }

    @Test
    fun `returns empty list when no data for date range`() = runTest {
        val result = useCase(0L, 1000L)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `groups location points into a single stay when points are close together`() = runTest {
        // Arrange — 5 location points at same place over 30 minutes
        val home = LatLon(51.5, -0.1)
        val t = System.currentTimeMillis()
        repeat(5) { i ->
            fakeLocationRepo.addLocation(
                LocationRecord(
                    timestamp = t + i * 6 * 60_000L,
                    latitude = home.lat + (i * 0.00001),  // tiny variation
                    longitude = home.lon,
                    accuracy = 10f,
                )
            )
        }
        fakePlaceRepo.addPlace(
            KnownPlace(id = 1, name = "Home", latitude = home.lat, longitude = home.lon, radiusMeters = 100.0)
        )

        // Act
        val result = useCase(t, t + 60 * 60_000L)

        // Assert
        val entries = result.getOrThrow()
        val stays = entries.filter { it.type == TimelineEntryType.LOCATION_STAY }
        assertEquals(1, stays.size)
        assertEquals("Home", stays[0].title)
    }

    @Test
    fun `includes call log entries sorted by timestamp`() = runTest {
        val t = 1_000_000L
        fakeRecordRepo.addRecord(
            CollectedRecord(
                timestamp = t + 1000,
                collectorType = CollectorType.CALL_LOG,
                data = RecordData.CallLog(
                    CallLogData(
                        number = "+1234567890",
                        callType = CallType.INCOMING,
                        durationSeconds = 200,
                        timestamp = t + 1000,
                    )
                )
            )
        )

        val result = useCase(t, t + 10_000L)
        val entries = result.getOrThrow()

        val callEntries = entries.filter { it.iconType?.startsWith("call_") == true }
        assertEquals(1, callEntries.size)
        assertTrue(callEntries[0].title.contains("Incoming call"))
        assertTrue(callEntries[0].title.contains("3m 20s"))
    }

    @Test
    fun `uses wifi fingerprint when gps place match fails`() = runTest {
        val t = 1_000_000L
        val wifiRecord = CollectedRecord(
            timestamp = t + 500,
            collectorType = CollectorType.WIFI,
            data = RecordData.Wifi(
                WifiData(connectedSsid = "Office_WiFi", connectedBssid = "AA:BB:CC:DD:EE:FF")
            )
        )
        fakeRecordRepo.addRecord(wifiRecord)
        fakePlaceRepo.addPlace(
            KnownPlace(
                id = 2,
                name = "Office",
                latitude = 99.0,  // far away from default location
                longitude = 99.0,
                radiusMeters = 100.0,
                wifiFingerprint = listOf("AA:BB:CC:DD:EE:FF"),
            )
        )
        // Add a location that won't GPS-match any known place
        fakeLocationRepo.addLocation(
            LocationRecord(timestamp = t, latitude = 0.0, longitude = 0.0, accuracy = 50f)
        )

        val result = useCase(t, t + 30 * 60_000L)
        val stays = result.getOrThrow().filter { it.type == TimelineEntryType.LOCATION_STAY }

        assertEquals("Office", stays.firstOrNull()?.title)
    }
}
```

### ProcessQueryUseCase

```kotlin
class ProcessQueryUseCaseTest {

    private val fakeLocationRepo = FakeLocationRepository()
    private val fakeRecordRepo = FakeRecordRepository()
    private val fakePlaceRepo = FakePlaceRepository()
    private val fakeTimelineRepo = FakeTimelineRepository()

    private val queryEngine = QueryEngine(
        timeParser = TimeExpressionParser(),
        intentClassifier = IntentClassifier(),
        entityExtractor = EntityExtractor(),
        queryBuilder = QueryBuilder(),
        responseGenerator = ResponseGenerator(),
    )

    private val useCase = ProcessQueryUseCase(
        queryEngine = queryEngine,
        recordRepository = fakeRecordRepo,
        locationRepository = fakeLocationRepo,
        timelineRepository = fakeTimelineRepo,
    )

    @Test
    fun `location query returns human-readable response`() = runTest {
        fakePlaceRepo.addPlace(
            KnownPlace(id = 1, name = "Home", latitude = 51.5, longitude = -0.1, radiusMeters = 100.0)
        )
        fakeLocationRepo.addLocation(
            LocationRecord(timestamp = yesterdayNoon(), latitude = 51.5001, longitude = -0.1001, accuracy = 15f)
        )

        val result = useCase("where was I yesterday?")

        assertTrue(result.isSuccess)
        val response = result.getOrThrow().response
        assertTrue(response.contains("Home") || response.contains("yesterday"), "Response: $response")
    }

    @Test
    fun `empty query returns failure`() = runTest {
        val result = useCase("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `step count query returns numeric answer`() = runTest {
        val t = todayStart()
        repeat(3) { i ->
            fakeRecordRepo.addRecord(
                CollectedRecord(
                    timestamp = t + i * 3_600_000L,
                    collectorType = CollectorType.ACTIVITY,
                    data = RecordData.Activity(
                        ActivityData(activityType = ActivityType.WALKING, stepCount = 1500)
                    )
                )
            )
        }

        val result = useCase("how many steps today?")
        assertTrue(result.isSuccess)
    }
}
```

---

## Query Engine Tests

### TimeExpressionParser — Comprehensive Table Tests

```kotlin
class TimeExpressionParserTest {

    private val parser = TimeExpressionParser()

    @ParameterizedTest(name = "EN: \"{0}\" → {1}")
    @MethodSource("englishQueries")
    fun `english time expressions parse correctly`(input: String, expectedType: String) {
        val range = parser.parse(input, Language.ENGLISH)
        assertNotNull(range, "Expected non-null for: $input")
        assertEquals(expectedType, range!!.type.name)
    }

    companion object {
        @JvmStatic
        fun englishQueries() = listOf(
            Arguments.of("where was I yesterday?", "YESTERDAY"),
            Arguments.of("what did I do today?", "TODAY"),
            Arguments.of("show me last week", "LAST_WEEK"),
            Arguments.of("where was I last monday?", "SPECIFIC_DAY"),
            Arguments.of("what happened 3 days ago?", "RELATIVE"),
            Arguments.of("in the last 2 hours", "RELATIVE"),
            Arguments.of("past 7 days", "RELATIVE"),
            Arguments.of("last 30 minutes", "RELATIVE"),
            Arguments.of("three days ago", "RELATIVE"),
            Arguments.of("a week ago", "RELATIVE"),
            Arguments.of("yesterday morning", "YESTERDAY"),
            Arguments.of("last night", "YESTERDAY"),
            Arguments.of("this morning", "TODAY"),
            Arguments.of("yesterday afternoon", "YESTERDAY"),
        )
    }

    @Test
    fun `returns null for unrecognized time expression`() {
        val range = parser.parse("random text with no time", Language.ENGLISH)
        assertNull(range)
    }

    @Test
    fun `yesterday morning maps to 06:00-12:00`() {
        val range = parser.parse("yesterday morning", Language.ENGLISH)!!
        val cal = Calendar.getInstance()
        cal.timeInMillis = range.start
        assertEquals(6, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `relative 2 hours ago maps to approximately 2 hours before now`() {
        val beforeParse = System.currentTimeMillis()
        val range = parser.parse("2 hours ago", Language.ENGLISH)!!
        val expectedStart = beforeParse - 2 * 3_600_000L
        // Allow 5 second tolerance
        assertTrue(kotlin.math.abs(range.start - expectedStart) < 5_000)
    }
}
```

### IntentClassifier Tests

```kotlin
class IntentClassifierTest {

    private val classifier = IntentClassifier()

    @ParameterizedTest
    @MethodSource("intentQueries")
    fun `classifies intent correctly`(query: String, expected: QueryIntent) {
        assertEquals(expected, classifier.classify(query, Language.ENGLISH))
    }

    companion object {
        @JvmStatic
        fun intentQueries() = listOf(
            Arguments.of("where was I yesterday?", QueryIntent.LOCATION),
            Arguments.of("what apps did I use?", QueryIntent.APP_USAGE),
            Arguments.of("how many steps today?", QueryIntent.STEPS),
            Arguments.of("show me my screen time", QueryIntent.SCREEN_TIME),
            Arguments.of("what time did I wake up?", QueryIntent.SLEEP),
            Arguments.of("who called me?", QueryIntent.CALLS),
            Arguments.of("what did I do this morning?", QueryIntent.ACTIVITY),
            Arguments.of("where do I spend most time?", QueryIntent.LOCATION),
            Arguments.of("how long was I at the office?", QueryIntent.LOCATION),
        )
    }
}
```

---

## ViewModel Tests

### TimelineViewModel Tests

```kotlin
class TimelineViewModelTest {

    private val fakeGetTimelineUseCase = FakeGetTimelineUseCase()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()  // sets Main dispatcher to TestCoroutineDispatcher

    private lateinit var viewModel: TimelineViewModel

    @BeforeEach
    fun setup() {
        viewModel = TimelineViewModel(fakeGetTimelineUseCase)
    }

    @Test
    fun `initial state has today selected and is not loading`() = runTest {
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(LocalDate.today(), state.selectedDate)
    }

    @Test
    fun `date selected — transitions through loading to loaded`() = runTest {
        fakeGetTimelineUseCase.result = Result.success(
            listOf(
                TimelineEntry(
                    startTimestamp = 1000L,
                    type = TimelineEntryType.LOCATION_STAY,
                    title = "Home",
                )
            )
        )

        viewModel.state.test {
            awaitItem()  // initial

            viewModel.onAction(TimelineContract.Action.DateSelected(LocalDate.today()))

            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(1, loaded.entries.size)
            assertEquals("Home", loaded.entries[0].title)
        }
    }

    @Test
    fun `date selected — use case failure shows error state`() = runTest {
        fakeGetTimelineUseCase.result = Result.failure(RuntimeException("DB error"))

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(TimelineContract.Action.DateSelected(LocalDate.today()))
            awaitItem()  // loading
            val errorState = awaitItem()
            assertFalse(errorState.isLoading)
            assertNotNull(errorState.error)
        }
    }

    @Test
    fun `refresh clicked — reloads current date`() = runTest {
        var callCount = 0
        fakeGetTimelineUseCase.onInvoke = { callCount++ }

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(TimelineContract.Action.DateSelected(LocalDate.today()))
            awaitItem(); awaitItem()  // loading + loaded

            viewModel.onAction(TimelineContract.Action.RefreshClicked)
            awaitItem(); awaitItem()  // loading + loaded again

            assertEquals(2, callCount)
        }
    }
}
```

### SearchViewModel Tests

```kotlin
class SearchViewModelTest {

    private val fakeProcessQuery = FakeProcessQueryUseCase()
    private val fakeGetRecentQueries = FakeGetRecentQueriesUseCase()

    private lateinit var viewModel: SearchViewModel

    @BeforeEach
    fun setup() {
        viewModel = SearchViewModel(fakeProcessQuery, fakeGetRecentQueries)
    }

    @Test
    fun `submit empty query — does nothing`() = runTest {
        viewModel.state.test {
            val initial = awaitItem()
            viewModel.onAction(SearchContract.Action.QueryChanged(""))
            viewModel.onAction(SearchContract.Action.SubmitQuery)
            // no new state emitted
            expectNoEvents()
        }
    }

    @Test
    fun `submit valid query — shows loading then result`() = runTest {
        fakeProcessQuery.result = Result.success(QueryResult.mock())
        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SearchContract.Action.QueryChanged("where was I?"))
            awaitItem()  // query text updated
            viewModel.onAction(SearchContract.Action.SubmitQuery)
            val loading = awaitItem()
            assertTrue(loading.isLoading)
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertNotNull(loaded.result)
        }
    }

    @Test
    fun `query failure — emits snackbar event`() = runTest {
        fakeProcessQuery.result = Result.failure(RuntimeException("parse error"))
        val events = mutableListOf<SearchContract.Event>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onAction(SearchContract.Action.QueryChanged("test"))
        viewModel.onAction(SearchContract.Action.SubmitQuery)
        advanceUntilIdle()

        assertTrue(events.any { it is SearchContract.Event.ShowSnackbar })
        job.cancel()
    }

    @Test
    fun `clear results — resets result to null`() = runTest {
        fakeProcessQuery.result = Result.success(QueryResult.mock())
        viewModel.onAction(SearchContract.Action.QueryChanged("test"))
        viewModel.onAction(SearchContract.Action.SubmitQuery)
        advanceUntilIdle()

        viewModel.onAction(SearchContract.Action.ClearResults)

        assertNull(viewModel.state.value.result)
        assertTrue(viewModel.state.value.query.isEmpty())
    }
}
```

---

## Mapper Tests

```kotlin
class RecordMapperTest {

    private val mapper = RecordMapper()

    @Test
    fun `location record round-trips through mapper`() {
        val original = CollectedRecord(
            timestamp = 1_700_000_000_000L,
            collectorType = CollectorType.LOCATION,
            data = RecordData.Location(
                LocationData(
                    latitude = 51.5074,
                    longitude = -0.1278,
                    accuracy = 12.5f,
                    altitude = 45.0,
                    speed = 1.2f,
                    bearing = 270f,
                )
            )
        )

        val dbRow = mapper.toDb(original)
        val restored = mapper.toDomain(dbRow)

        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.collectorType, restored.collectorType)
        val originalData = original.data as RecordData.Location
        val restoredData = restored.data as RecordData.Location
        assertEquals(originalData.locationData.latitude, restoredData.locationData.latitude, 0.00001)
        assertEquals(originalData.locationData.longitude, restoredData.locationData.longitude, 0.00001)
    }

    @Test
    fun `wifi record round-trips with nested networks list`() {
        val original = CollectedRecord(
            timestamp = 1_700_000_000_000L,
            collectorType = CollectorType.WIFI,
            data = RecordData.Wifi(
                WifiData(
                    connectedSsid = "Home_WiFi",
                    connectedBssid = "AA:BB:CC:DD:EE:FF",
                    nearbyNetworks = listOf(
                        NearbyNetwork("Neighbor", "11:22:33:44:55:66", -70),
                        NearbyNetwork("Other", "77:88:99:AA:BB:CC", -85),
                    ),
                    signalStrength = -55,
                )
            )
        )

        val restored = mapper.toDomain(mapper.toDb(original))
        val restoredData = restored.data as RecordData.Wifi
        assertEquals(2, restoredData.wifiData.nearbyNetworks.size)
        assertEquals("AA:BB:CC:DD:EE:FF", restoredData.wifiData.connectedBssid)
    }

    @Test
    fun `call log record round-trips with all call types`() {
        CallType.entries.forEach { callType ->
            val original = CollectedRecord(
                timestamp = 1_700_000_000_000L,
                collectorType = CollectorType.CALL_LOG,
                data = RecordData.CallLog(
                    CallLogData(
                        number = "+1234567890",
                        callType = callType,
                        durationSeconds = 180,
                        timestamp = 1_700_000_000_000L,
                    )
                )
            )
            val restored = mapper.toDomain(mapper.toDb(original))
            val restoredCall = (restored.data as RecordData.CallLog).callLogData
            assertEquals(callType, restoredCall.callType, "Failed for callType: $callType")
        }
    }
}
```

---

## Fake Use Cases (for ViewModel tests)

```kotlin
class FakeGetTimelineUseCase : GetTimelineUseCase {
    var result: Result<List<TimelineEntry>> = Result.success(emptyList())
    var onInvoke: (() -> Unit)? = null

    override suspend fun invoke(start: Long, end: Long): Result<List<TimelineEntry>> {
        onInvoke?.invoke()
        return result
    }
}

class FakeProcessQueryUseCase : ProcessQueryUseCase {
    var result: Result<QueryResult> = Result.success(QueryResult.mock())

    override suspend fun invoke(query: String): Result<QueryResult> = result
}
```

---

## Test Naming Convention

```kotlin
@Test
fun `action description — expected result`()

// Examples:
fun `submit query with valid text — returns results and clears loading`()
fun `submit query with empty text — does nothing`()
fun `date selected — transitions through loading to loaded`()
fun `date selected — use case failure shows error state`()
fun `location points within 100m — grouped into single stay`()
fun `location points further than 1km apart — split into two stays`()
fun `yesterday morning expression — maps to 06:00-12:00 window`()
fun `hebrew yesterday expression — parses correctly`()
```

---

## MainDispatcherRule (required for ViewModel tests)

```kotlin
// shared/src/commonTest/kotlin/com/blackbox/util/MainDispatcherRule.kt

class MainDispatcherRule(
    private val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }
}
```

---

## Test Data Builders

```kotlin
// shared/src/commonTest/kotlin/com/blackbox/builders/TestData.kt

object TestData {

    fun locationRecord(
        timestamp: Long = System.currentTimeMillis(),
        lat: Double = 51.5074,
        lon: Double = -0.1278,
        accuracy: Float = 15f,
    ) = LocationRecord(
        timestamp = timestamp,
        latitude = lat,
        longitude = lon,
        accuracy = accuracy,
        altitude = null,
        speed = null,
        bearing = null,
    )

    fun knownPlace(
        id: Long = 1L,
        name: String = "Home",
        lat: Double = 51.5074,
        lon: Double = -0.1278,
        radius: Double = 100.0,
        wifiFingerprint: List<String> = emptyList(),
    ) = KnownPlace(
        id = id,
        name = name,
        latitude = lat,
        longitude = lon,
        radiusMeters = radius,
        category = PlaceCategory.HOME,
        wifiFingerprint = wifiFingerprint,
    )

    fun callRecord(
        timestamp: Long = System.currentTimeMillis(),
        callType: CallType = CallType.INCOMING,
        durationSeconds: Int = 60,
    ) = CollectedRecord(
        timestamp = timestamp,
        collectorType = CollectorType.CALL_LOG,
        data = RecordData.CallLog(
            CallLogData(
                number = "+447700900123",
                callType = callType,
                durationSeconds = durationSeconds,
                timestamp = timestamp,
            )
        )
    )
}
```

---

## What NOT to Test

- **Private functions** — test them indirectly through public API
- **Trivial getters/setters** — `state.value.isLoading` doesn't need a test
- **Framework code** — don't test that `StateFlow.update` works; Kotlin guarantees it
- **Compose rendering** — use `@Preview` for visual verification, not automated tests
- **SQLDelight generated code** — it's generated and tested by SQLDelight team

---

## Coverage Targets

| Layer | Target | Rationale |
|-------|--------|-----------|
| Use cases | 100% | Core business logic, pure Kotlin, easy to test |
| Query engine | 90%+ | Complex parsing, many edge cases |
| Mappers | 100% | Pure transformation, must be correct |
| ViewModels | 80%+ | State transitions are critical |
| Repositories | 60%+ | Mostly delegates to SQLDelight |
| Collectors | Manual | Requires real device hardware |

---

## Debugging Failing Tests

### Coroutine timing issues

```kotlin
// Problem: test completes before coroutines finish
@Test
fun `loads data on start`() = runTest {
    viewModel.state.test {
        awaitItem()  // always await at least the initial item
        // then trigger action
        viewModel.onAction(...)
        awaitItem()  // wait for next emission
    }
}

// Problem: background coroutines not completing
@Test
fun `use case completes`() = runTest {
    useCase("query")
    advanceUntilIdle()  // runs all pending coroutines to completion
    assertEquals(expectedState, viewModel.state.value)
}
```

### Turbine tips

```kotlin
viewModel.state.test {
    val state1 = awaitItem()    // waits for next emission
    expectNoEvents()             // asserts no more emissions in next 100ms
    cancelAndIgnoreRemainingEvents()  // cleanup without assertion
}
```

### Fake assertions

```kotlin
// Verify a repository method was called with correct arguments
class FakeLocationRepository : LocationRepository {
    val getLocationsInRangeCalls = mutableListOf<Pair<Long, Long>>()

    override suspend fun getLocationsInRange(start: Long, end: Long): List<LocationRecord> {
        getLocationsInRangeCalls.add(start to end)
        return emptyList()
    }
}

// In test:
assertEquals(1, fakeLocationRepo.getLocationsInRangeCalls.size)
assertEquals(expectedStart, fakeLocationRepo.getLocationsInRangeCalls[0].first)
```
