# BlackBox — Query Engine Documentation

---

## 1. Overview

The query engine transforms natural language questions into structured database queries and formats the results into human-readable responses. It operates entirely on-device with zero network dependency.

**Pipeline:**
```
Raw Query Text
    → Preprocessor (normalize, detect language)
    → Time Expression Parser (extract date/time references)
    → Intent Classifier (determine query type)
    → Entity Extractor (pull out places, activities, apps)
    → Query Builder (construct DB queries)
    → Data Retriever (execute queries)
    → Response Generator (format human-readable answer)
```

---

## 2. Query Preprocessor

### 2.1 Normalization Steps

```kotlin
class QueryPreprocessor {
    fun preprocess(rawQuery: String): PreprocessedQuery {
        val trimmed = rawQuery.trim()
        val language = detectLanguage(trimmed)
        val normalized = when (language) {
            Language.HEBREW -> normalizeHebrew(trimmed)
            Language.ENGLISH -> normalizeEnglish(trimmed)
        }
        return PreprocessedQuery(
            original = rawQuery,
            normalized = normalized,
            language = language,
        )
    }
}
```

**Normalization:**
- Trim whitespace
- Lowercase (English only — Hebrew doesn't have case)
- Remove punctuation except question marks
- Normalize Hebrew final letters (ם→מ, ן→נ, etc.) for matching purposes
- Expand common contractions: "didn't" → "did not", "what's" → "what is"

### 2.2 Language Detection

Simple heuristic — no ML needed:
- If the query contains Hebrew characters (Unicode range \u0590-\u05FF) → Hebrew
- Otherwise → English
- Mixed: if majority Hebrew → Hebrew, otherwise English

---

## 3. Time Expression Parser

This is the most critical NLP component. It extracts time ranges from natural language.

### 3.1 Supported Expressions

#### English Time Expressions

**Relative dates:**
| Expression | Resolves To |
|-----------|-------------|
| "today" | Today 00:00 → 23:59 |
| "yesterday" | Yesterday 00:00 → 23:59 |
| "day before yesterday" | 2 days ago 00:00 → 23:59 |
| "N days ago" / "N day ago" | N days ago 00:00 → 23:59 |
| "this week" | Monday 00:00 → now |
| "last week" | Previous Monday 00:00 → Sunday 23:59 |
| "this month" | 1st of month 00:00 → now |
| "last month" | Previous month 1st → last day 23:59 |
| "this year" | Jan 1 00:00 → now |

**Relative days of week:**
| Expression | Resolves To |
|-----------|-------------|
| "last Monday" / "Monday" (past) | Most recent past Monday 00:00 → 23:59 |
| "last Tuesday" | Most recent past Tuesday |
| "last Friday" | Most recent past Friday |
| "this Monday" | Monday of current week |

**Time of day modifiers:**
| Expression | Time Range |
|-----------|------------|
| "morning" / "this morning" | 06:00 → 12:00 |
| "afternoon" / "this afternoon" | 12:00 → 18:00 |
| "evening" / "this evening" | 18:00 → 22:00 |
| "night" / "tonight" / "last night" | 22:00 → 06:00 (next day) |
| "at Npm" / "at N pm" | N:00 → N:59 |
| "at N" (when context is clear) | N:00 → N:59 |
| "between Npm and Mpm" | N:00 → M:59 |
| "around Npm" | (N-1):00 → (N+1):59 |

**Absolute dates:**
| Expression | Example |
|-----------|---------|
| "January 15" / "January 15th" | Jan 15 of current/most recent year |
| "Jan 15" | Same as above |
| "15/1" / "15.1" | Day/Month (Israeli format) |
| "15/1/2026" / "15.1.2026" | Full date |
| "2026-01-15" | ISO format |

**Duration/ago expressions:**
| Expression | Resolves To |
|-----------|-------------|
| "N hours ago" | Now minus N hours ± 30 min |
| "N minutes ago" | Now minus N minutes ± 5 min |
| "in the last N hours" | (now - N hours) → now |
| "in the last N days" | N days ago → now |
| "past N weeks" | N weeks ago → now |

**Combined expressions:**
| Expression | Resolves To |
|-----------|-------------|
| "yesterday afternoon" | Yesterday 12:00 → 18:00 |
| "last Friday evening" | Last Friday 18:00 → 22:00 |
| "Monday morning" | Last Monday 06:00 → 12:00 |
| "January 15th at 3pm" | Jan 15, 15:00 → 15:59 |
| "2 days ago in the morning" | 2 days ago 06:00 → 12:00 |

#### Hebrew Time Expressions

**Relative dates:**
| Hebrew | English Equivalent |
|--------|-------------------|
| "היום" | today |
| "אתמול" | yesterday |
| "שלשום" | day before yesterday |
| "לפני X ימים" | X days ago |
| "השבוע" | this week |
| "שבוע שעבר" | last week |
| "החודש" | this month |
| "חודש שעבר" | last month |

**Days of week:**
| Hebrew | Day |
|--------|-----|
| "ראשון" / "יום ראשון" | Sunday |
| "שני" / "יום שני" | Monday |
| "שלישי" / "יום שלישי" | Tuesday |
| "רביעי" / "יום רביעי" | Wednesday |
| "חמישי" / "יום חמישי" | Thursday |
| "שישי" / "יום שישי" | Friday |
| "שבת" / "יום שבת" | Saturday |
| "שישי שעבר" | last Friday |
| "שני שעבר" | last Monday |

**Time of day:**
| Hebrew | Time Range |
|--------|-----------|
| "בוקר" / "הבוקר" / "בבוקר" | 06:00 → 12:00 |
| "צהריים" / "בצהריים" | 11:00 → 14:00 |
| "אחר הצהריים" / "אחה״צ" | 12:00 → 18:00 |
| "ערב" / "בערב" / "הערב" | 18:00 → 22:00 |
| "לילה" / "בלילה" | 22:00 → 06:00 |
| "בשעה X" | At hour X |

**Combined:**
| Hebrew | Resolves To |
|--------|-------------|
| "אתמול בערב" | yesterday evening |
| "שישי שעבר בצהריים" | last Friday noon |
| "הבוקר" | this morning |
| "אתמול אחר הצהריים" | yesterday afternoon |

### 3.2 Implementation Strategy

```kotlin
class TimeExpressionParser(
    private val dateTimeProvider: DateTimeProvider, // testable clock
) {
    /**
     * Extracts a time range from the query text.
     * Returns null if no time expression is found (will default to "today").
     */
    fun parse(query: String, language: Language): TimeRange? {
        val normalizedQuery = normalize(query, language)

        // Try patterns from most specific to least specific
        return tryAbsoluteDateTime(normalizedQuery, language)
            ?: tryCombinedRelative(normalizedQuery, language)  // "yesterday afternoon"
            ?: tryRelativeDate(normalizedQuery, language)       // "yesterday", "last week"
            ?: tryTimeOfDay(normalizedQuery, language)          // "this morning" (implies today)
            ?: tryDurationAgo(normalizedQuery, language)        // "3 hours ago"
            ?: null // No time expression found → caller defaults to today
    }
}
```

**Pattern matching approach:** Use regex patterns organized by specificity. Each pattern returns a `TimeRange(startEpochMs, endEpochMs)`.

### 3.3 Default Time Range

If no time expression is detected:
- For LOCATION_QUERY → default to today
- For ACTIVITY_QUERY → default to today
- For TEMPORAL_QUERY → default to today
- For PATTERN_QUERY → default to last 30 days
- For PROOF_QUERY → REQUIRE explicit time (ask user to specify)
- For SUMMARY_QUERY → default to yesterday

---

## 4. Intent Classifier

### 4.1 Intent Types

```kotlin
enum class QueryIntent {
    LOCATION_QUERY,    // Where was I?
    ACTIVITY_QUERY,    // What was I doing?
    TEMPORAL_QUERY,    // When did I...?
    PATTERN_QUERY,     // Do I usually...? / How often...?
    PROOF_QUERY,       // Prove I was... / Evidence that...
    SUMMARY_QUERY,     // Summarize my day / Tell me about...
    DURATION_QUERY,    // How long was I...? / How much time...?
    COUNT_QUERY,       // How many times...? / How often...?
}
```

### 4.2 Keyword-Based Classification

```kotlin
class IntentClassifier {

    fun classify(query: String, language: Language): QueryIntent {
        val keywords = extractKeywords(query, language)
        return classifyByKeywords(keywords)
    }

    private val englishIntentKeywords = mapOf(
        QueryIntent.LOCATION_QUERY to listOf(
            "where", "location", "place", "places", "address",
            "been", "went", "visited", "go", "map"
        ),
        QueryIntent.TEMPORAL_QUERY to listOf(
            "when", "what time", "time did", "time do",
            "arrive", "arrived", "leave", "left", "depart",
            "wake", "woke", "sleep", "slept"
        ),
        QueryIntent.ACTIVITY_QUERY to listOf(
            "what was i doing", "what did i do", "doing",
            "activity", "activities", "apps", "using", "used"
        ),
        QueryIntent.PATTERN_QUERY to listOf(
            "usually", "typically", "average", "pattern",
            "trend", "often", "always", "habit", "routine",
            "correlat", "relationship", "compare", "vs", "versus"
        ),
        QueryIntent.PROOF_QUERY to listOf(
            "prove", "proof", "evidence", "verify", "confirm",
            "show that", "demonstrate"
        ),
        QueryIntent.SUMMARY_QUERY to listOf(
            "summarize", "summary", "tell me about",
            "overview", "recap", "report", "how was"
        ),
        QueryIntent.DURATION_QUERY to listOf(
            "how long", "how much time", "duration",
            "time spent", "hours at", "minutes"
        ),
        QueryIntent.COUNT_QUERY to listOf(
            "how many", "how often", "count", "number of",
            "times did", "frequency"
        ),
    )

    private val hebrewIntentKeywords = mapOf(
        QueryIntent.LOCATION_QUERY to listOf(
            "איפה", "היכן", "מיקום", "מקום", "כתובת", "הייתי", "ביקרתי"
        ),
        QueryIntent.TEMPORAL_QUERY to listOf(
            "מתי", "באיזה שעה", "שעה", "הגעתי", "עזבתי", "יצאתי",
            "קמתי", "הלכתי לישון", "נרדמתי"
        ),
        QueryIntent.ACTIVITY_QUERY to listOf(
            "מה עשיתי", "מה היה", "פעילות", "אפליקציות", "השתמשתי"
        ),
        QueryIntent.PATTERN_QUERY to listOf(
            "בדרך כלל", "ממוצע", "דפוס", "מגמה", "תבנית",
            "כמה פעמים", "השוואה"
        ),
        QueryIntent.PROOF_QUERY to listOf(
            "הוכח", "הוכחה", "ראיה", "אמת", "תוכיח"
        ),
        QueryIntent.SUMMARY_QUERY to listOf(
            "סכם", "סיכום", "ספר לי על", "איך היה", "תגיד לי על"
        ),
        QueryIntent.DURATION_QUERY to listOf(
            "כמה זמן", "משך", "שעות", "דקות"
        ),
        QueryIntent.COUNT_QUERY to listOf(
            "כמה פעמים", "כמה", "תדירות", "מספר"
        ),
    )
}
```

### 4.3 Priority Resolution

If multiple intents match, use this priority:
1. PROOF_QUERY (if "prove" or "evidence" appears, it's always proof)
2. PATTERN_QUERY (if "usually", "average", "trend" appears)
3. TEMPORAL_QUERY (if "when", "what time" appears)
4. LOCATION_QUERY (if "where" appears)
5. DURATION_QUERY (if "how long" appears)
6. COUNT_QUERY (if "how many" appears)
7. SUMMARY_QUERY (if "summarize", "tell me about" appears)
8. ACTIVITY_QUERY (default fallback)

---

## 5. Entity Extractor

Extracts specific entities from the query beyond time and intent.

### 5.1 Entity Types

```kotlin
sealed class QueryEntity {
    // A known place referenced by name
    data class PlaceReference(val name: String) : QueryEntity()
    // An activity type referenced
    data class ActivityReference(val type: ActivityType) : QueryEntity()
    // An app or app category referenced
    data class AppReference(val nameOrCategory: String) : QueryEntity()
    // A person referenced (for future: "when did I meet X?")
    data class PersonReference(val name: String) : QueryEntity()
    // A numeric value (for comparisons: "more than 10000 steps")
    data class NumericValue(val value: Double, val unit: String?) : QueryEntity()
}
```

### 5.2 Place Name Matching

```kotlin
class EntityExtractor(
    private val placeRepository: PlaceRepository,
) {
    /**
     * Matches place names in the query against known places.
     * Uses fuzzy matching to handle variations:
     * "the office" → KnownPlace("Office", category=WORK)
     * "home" → KnownPlace("Home", category=HOME)
     * "the gym" → KnownPlace("Gym", category=GYM)
     * "aroma" → KnownPlace("Aroma TLV", category=RESTAURANT)
     */
    suspend fun extractPlaces(query: String): List<QueryEntity.PlaceReference> {
        val knownPlaces = placeRepository.getAllPlaces()
        // Match against known place names (case-insensitive, partial match)
        // Also match common aliases: "work" → WORK category, "home" → HOME category
    }
}
```

**Keyword → category mapping:**
- "home", "house", "בית" → HOME
- "work", "office", "עבודה", "משרד" → WORK
- "gym", "חדר כושר" → GYM
- "school", "university", "בית ספר", "אוניברסיטה" → SCHOOL

### 5.3 Activity Matching

| Keywords (EN) | Keywords (HE) | ActivityType |
|--------------|---------------|-------------|
| "walking", "walk", "walked" | "הליכה", "הלכתי" | WALKING |
| "running", "run", "ran", "jogging" | "ריצה", "רצתי" | RUNNING |
| "driving", "drove", "car" | "נהיגה", "נהגתי", "רכב" | IN_VEHICLE |
| "cycling", "bike", "bicycle" | "אופניים", "רכיבה" | ON_BICYCLE |
| "sleeping", "sleep", "asleep" | "ישנתי", "שינה" | SLEEPING |
| "sitting", "still", "stationary" | "ישבתי", "נייח" | STILL |

---

## 6. Query Builder

Converts parsed intent + entities + time range into executable database queries.

### 6.1 Query Plans

```kotlin
class QueryBuilder(
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
    private val timelineRepository: TimelineRepository,
    private val placeRepository: PlaceRepository,
) {
    /**
     * Builds a retrieval plan based on the parsed query.
     * Different intents require different data combinations.
     */
    fun buildPlan(parsed: ParsedQuery): QueryPlan {
        return when (parsed.intent) {
            QueryIntent.LOCATION_QUERY -> buildLocationPlan(parsed)
            QueryIntent.ACTIVITY_QUERY -> buildActivityPlan(parsed)
            QueryIntent.TEMPORAL_QUERY -> buildTemporalPlan(parsed)
            QueryIntent.PATTERN_QUERY -> buildPatternPlan(parsed)
            QueryIntent.PROOF_QUERY -> buildProofPlan(parsed)
            QueryIntent.SUMMARY_QUERY -> buildSummaryPlan(parsed)
            QueryIntent.DURATION_QUERY -> buildDurationPlan(parsed)
            QueryIntent.COUNT_QUERY -> buildCountPlan(parsed)
        }
    }
}
```

### 6.2 Plan Details

**LOCATION_QUERY plan:**
1. Get LocationRecords in time range
2. Match locations to KnownPlaces
3. If asking about a specific time → return single location
4. If asking about a range → return location timeline

**ACTIVITY_QUERY plan:**
1. Get Activity records in time range
2. Get AppUsage records in time range
3. Get ScreenState records in time range
4. Get LocationRecords for context
5. Combine into activity narrative

**TEMPORAL_QUERY plan:**
1. Get DerivedEvents matching the event type (ARRIVED, DEPARTED, SLEEP_START, etc.)
2. If no derived event found → scan raw records for transitions
3. Return timestamp of the event

**PATTERN_QUERY plan:**
1. Get DailySummaries for the date range (default 30 days)
2. Run aggregation/correlation on the requested metric
3. Compute statistics (average, min, max, trend)

**PROOF_QUERY plan:**
1. Get ALL collector records for the time range
2. Cross-reference multiple data sources
3. Calculate corroboration score
4. Generate evidence report

**SUMMARY_QUERY plan:**
1. Get DailySummary for the date
2. Get DerivedEvents for narrative
3. If no summary exists → generate on-the-fly from raw records

**DURATION_QUERY plan:**
1. Identify the subject (place, activity, or app)
2. Sum durations from relevant records
3. Return formatted duration

**COUNT_QUERY plan:**
1. Identify the subject (visits, unlocks, steps, etc.)
2. Count occurrences in the time range
3. Return count with context

---

## 7. Response Generator

### 7.1 Response Templates

```kotlin
class ResponseGenerator {

    fun generate(parsed: ParsedQuery, data: QueryData): QueryResponse {
        val template = selectTemplate(parsed.intent, data)
        return fillTemplate(template, parsed, data)
    }
}
```

### 7.2 Template Catalog

**LOCATION_QUERY — Single point in time:**
```
EN: "At {TIME}, you were at {PLACE_NAME} ({ADDRESS}). 
    You arrived at {ARRIVAL_TIME} and left at {DEPARTURE_TIME} ({DURATION}).
    Confidence: {CONFIDENCE}% based on {SOURCE_COUNT} data sources."

HE: "ב{TIME}, היית ב{PLACE_NAME} ({ADDRESS}).
    הגעת ב{ARRIVAL_TIME} ועזבת ב{DEPARTURE_TIME} ({DURATION}).
    רמת ודאות: {CONFIDENCE}% על בסיס {SOURCE_COUNT} מקורות מידע."
```

**LOCATION_QUERY — Time range (multiple places):**
```
EN: "On {DATE}, you visited {PLACE_COUNT} places:
    • {PLACE_1}: {ARRIVAL_1} → {DEPARTURE_1} ({DURATION_1})
    • {PLACE_2}: {ARRIVAL_2} → {DEPARTURE_2} ({DURATION_2})
    ..."

HE: "ב{DATE}, ביקרת ב{PLACE_COUNT} מקומות:
    • {PLACE_1}: {ARRIVAL_1} → {DEPARTURE_1} ({DURATION_1})
    • {PLACE_2}: {ARRIVAL_2} → {DEPARTURE_2} ({DURATION_2})
    ..."
```

**ACTIVITY_QUERY:**
```
EN: "{TIME_DESCRIPTION}:
    You were at {PLACE} from {START} to {END}.
    Activity: {ACTIVITY_TYPE}
    Steps: {STEP_COUNT}
    Screen time: {SCREEN_TIME}
    Top app: {TOP_APP} ({APP_DURATION})"

HE: "{TIME_DESCRIPTION}:
    היית ב{PLACE} מ{START} עד {END}.
    פעילות: {ACTIVITY_TYPE}
    צעדים: {STEP_COUNT}
    זמן מסך: {SCREEN_TIME}
    אפליקציה מובילה: {TOP_APP} ({APP_DURATION})"
```

**TEMPORAL_QUERY:**
```
EN: "You {EVENT_DESCRIPTION} at {TIME} on {DATE}."
    Example: "You arrived at the office at 9:20 AM on Monday."

HE: "{EVENT_DESCRIPTION} ב{TIME} ב{DATE}."
    Example: "הגעת למשרד ב-9:20 ביום שני."
```

**PATTERN_QUERY:**
```
EN: "Over the last {PERIOD}:
    Average: {AVG_VALUE}
    Range: {MIN_VALUE} to {MAX_VALUE}
    Trend: {TREND_DESCRIPTION}
    {INSIGHT_TEXT}"

HE: "ב{PERIOD} האחרונים:
    ממוצע: {AVG_VALUE}
    טווח: מ{MIN_VALUE} עד {MAX_VALUE}
    מגמה: {TREND_DESCRIPTION}
    {INSIGHT_TEXT}"
```

**PROOF_QUERY:**
```
EN: "Evidence for {DATE_RANGE}:
    ✓ GPS: {COORDS} (accuracy: {ACC}m) — {GPS_COUNT} readings
    ✓ WiFi: Connected to '{SSID}' — {WIFI_COUNT} connections
    ✓ Activity: {ACTIVITY} detected — consistent with {PLACE}
    ✓ Screen: {SCREEN_EVENTS} unlock events
    ✗ Audio: No data (collector disabled)
    
    Corroboration: {SCORE}% — {ACTIVE_SOURCES} of {TOTAL_SOURCES} sources confirm"
```

**SUMMARY_QUERY:**
```
EN: "{DATE} Summary:
    🌅 Woke up at {WAKE_TIME}
    📍 Visited: {PLACE_LIST}
    🚶 {STEP_COUNT} steps ({DISTANCE})
    📱 Screen time: {SCREEN_TIME} ({UNLOCK_COUNT} pickups)
    📊 Most used: {TOP_APPS}
    🌙 Bed at {SLEEP_TIME}"
```

**NO_DATA response:**
```
EN: "I don't have enough data for {TIME_DESCRIPTION}. 
    {REASON: 'Recording wasn't active' | 'This date is before recording started' | 'The relevant collectors were disabled'}"

HE: "אין לי מספיק מידע על {TIME_DESCRIPTION}.
    {REASON}"
```

### 7.3 Response Formatting Rules

- Always include the time reference in the response (so user knows what period is covered)
- Show confidence/data quality when it's not 100%
- Offer follow-up suggestions: "Want to see this on the map?" / "Want more detail about the evening?"
- When data is partial (some collectors were off), mention what's missing
- Format times in 12h (AM/PM) for English, 24h for Hebrew
- Format dates as "Monday, February 14" for English, "יום שני, 14 בפברואר" for Hebrew
- Durations: "2h 35min" / "2 שעות ו-35 דקות"

---

## 8. Suggested Follow-Up Queries

After every response, suggest 2-3 relevant follow-up queries:

```kotlin
fun generateSuggestions(parsed: ParsedQuery, result: QueryData): List<String> {
    return when (parsed.intent) {
        QueryIntent.LOCATION_QUERY -> listOf(
            "How long was I there?",
            "Show me on the map",
            "What was I doing there?",
        )
        QueryIntent.ACTIVITY_QUERY -> listOf(
            "Where was I at that time?",
            "How does this compare to last week?",
            "Summarize the full day",
        )
        QueryIntent.TEMPORAL_QUERY -> listOf(
            "What was I doing before that?",
            "Is this my usual time?",
            "Show me the full day timeline",
        )
        QueryIntent.SUMMARY_QUERY -> listOf(
            "How does this compare to a typical day?",
            "Show me on the map",
            "What about the day before?",
        )
        // ... etc
    }
}
```

---

## 9. Testing the Query Engine

### 9.1 Test Cases — Time Expression Parser

The time expression parser must be tested with at least these cases:

```kotlin
// English tests
"yesterday" → yesterday 00:00-23:59
"yesterday afternoon" → yesterday 12:00-18:00
"last Friday" → most recent Friday 00:00-23:59
"last Friday evening" → most recent Friday 18:00-22:00
"this morning" → today 06:00-12:00
"3 hours ago" → (now-3h-30min) to (now-3h+30min)
"January 15th at 3pm" → Jan 15 15:00-15:59
"last week" → prev Monday 00:00 to prev Sunday 23:59
"between 2pm and 5pm yesterday" → yesterday 14:00-17:00
"2 days ago" → 2 days ago 00:00-23:59

// Hebrew tests
"אתמול" → yesterday 00:00-23:59
"אתמול בערב" → yesterday 18:00-22:00
"שישי שעבר" → most recent Friday 00:00-23:59
"הבוקר" → today 06:00-12:00
"לפני 3 ימים" → 3 days ago 00:00-23:59
"שבוע שעבר" → prev Sunday 00:00 to prev Saturday 23:59 (Israeli week)

// Edge cases
"" → null (default to today)
"hello" → null (no time reference)
"always" → null (no specific time)
"last 30 days" → 30 days ago to now
```

### 9.2 Test Cases — Full Pipeline

```kotlin
// Query: "Where was I yesterday afternoon?"
// Expected intent: LOCATION_QUERY
// Expected time range: yesterday 12:00-18:00
// Expected: returns location records for that range

// Query: "What time do I usually wake up?"
// Expected intent: PATTERN_QUERY
// Expected time range: last 30 days
// Expected: aggregates sleep_end_estimate from DailySummary

// Query: "Prove I was at the office on January 15th"
// Expected intent: PROOF_QUERY
// Expected time range: Jan 15 00:00-23:59
// Expected: multi-source evidence report

// Query: "כמה צעדים עשיתי היום?"
// Expected intent: COUNT_QUERY
// Expected time range: today
// Expected: step count from DailySummary or raw Activity records
```
