# BlackBox — UI Screen Specifications

> ⚠️ **Theme is outdated in this document.** The spec below describes a dark blue + amber theme. The actual implemented theme is cyberpunk: near-black `#050510` background, neon green `#00FF41`, electric cyan `#00D4FF`, magenta `#FF0064`, monospace fonts, sharp corners, scan-line overlay. See `shared/src/commonMain/kotlin/com/blackbox/ui/theme/` for the live implementation.

---

## 1. Design System

### 1.1 Brand Identity

- **App Name:** BlackBox
- **Visual Theme:** Dark-first design (aviation/flight recorder inspired)
- **Primary Color:** Deep blue (#1A237E) — trust, security, depth
- **Accent Color:** Amber (#FFB300) — like a flight recorder's glow
- **Background (Dark):** Near-black (#0D1117)
- **Surface (Dark):** Dark gray (#161B22)
- **Background (Light):** Off-white (#FAFAFA)
- **Surface (Light):** White (#FFFFFF)
- **Success:** Green (#4CAF50)
- **Error:** Red (#F44336)
- **Warning:** Orange (#FF9800)
- **Text Primary:** White (dark) / Near-black (light)
- **Text Secondary:** Gray (#8B949E)

### 1.2 Typography

- **Headlines:** Bold, larger sizes for dates and section titles
- **Body:** Regular weight, good readability
- **Monospace:** For timestamps, coordinates, and technical data
- Use Material 3 type scale: `displaySmall`, `headlineMedium`, `bodyLarge`, `labelMedium`, etc.

### 1.3 Iconography

Use Material Icons (filled style) consistently:
- 📍 Location → `Icons.Filled.LocationOn`
- 🔍 Search → `Icons.Filled.Search`
- 📊 Insights → `Icons.Filled.Insights`
- ⏱️ Timeline → `Icons.Filled.Timeline`
- 🗺️ Map → `Icons.Filled.Map`
- ⚙️ Settings → `Icons.Filled.Settings`
- 🔒 Security → `Icons.Filled.Lock`
- 🔋 Battery → `Icons.Filled.BatteryStd`

### 1.4 Motion & Animation

- Use Material 3 motion tokens
- Screen transitions: shared axis (horizontal for peer screens, vertical for parent/child)
- List items: staggered fade-in on load
- Search results: slide-up with fade
- State changes: crossfade (loading → content → error)
- Keep all animations under 300ms for snappy feel

---

## 2. Navigation Structure

### 2.1 Bottom Navigation

```
┌──────┬──────────┬─────┬──────────┬──────────┐
│ 🔍   │ ⏱️       │ 🗺️  │ 📊       │ ⚙️       │
│Search│ Timeline │ Map │ Insights │ Settings │
└──────┴──────────┴─────┴──────────┴──────────┘
```

- **Search** — Default/home tab. Natural language query interface.
- **Timeline** — Chronological day view of all events.
- **Map** — Location history on a map.
- **Insights** — Patterns and statistical insights.
- **Settings** — Collector toggles, data management, privacy.

### 2.2 Navigation Graph

```kotlin
sealed class Screen(val route: String) {
    data object Search : Screen("search")
    data object Timeline : Screen("timeline")
    data object Map : Screen("map")
    data object Insights : Screen("insights")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")
    data class ProofReport(val startMs: Long, val endMs: Long) : Screen("proof/{startMs}/{endMs}")
    data class DayDetail(val date: String) : Screen("day/{date}")
}
```

---

## 3. Screen Specifications

### 3.1 Search Screen (Home)

**Purpose:** Primary interaction point. Users ask questions in natural language.

**Layout:**
```
┌─────────────────────────────────────┐
│  BlackBox                     [🔔]  │  ← Top bar (minimal, app name only)
├─────────────────────────────────────┤
│                                     │
│  ┌─────────────────────────────┐    │
│  │ 🔍 Ask anything about your  │    │  ← Search bar (prominent, always visible)
│  │   day...                    │    │     Auto-focused when tab is selected
│  └─────────────────────────────┘    │
│                                     │
│  Quick Actions                      │  ← Section label (subtle)
│  ┌────────────┐ ┌────────────┐      │
│  │ ☀️ Today's  │ │ 📍 Where   │     │  ← Horizontal scrollable chips
│  │  Summary   │ │  was I?    │      │     Tapping fills search bar with
│  └────────────┘ └────────────┘      │     a pre-formed query
│  ┌────────────┐ ┌────────────┐      │
│  │ 📊 Weekly  │ │ 🏃 My      │      │
│  │  Report    │ │  Patterns  │      │
│  └────────────┘ └────────────┘      │
│                                     │
│  Recent Queries                     │  ← Section label
│  ┌─────────────────────────────┐    │
│  │ 🕐 Where was I yesterday    │    │  ← Tapping re-executes the query
│  │    afternoon?               │    │
│  ├─────────────────────────────┤    │
│  │ 🕐 How many steps this      │    │
│  │    week?                    │    │
│  ├─────────────────────────────┤    │
│  │ 🕐 What time did I leave    │    │
│  │    work on Monday?          │    │
│  └─────────────────────────────┘    │
│                                     │
│  ─── Recording Status ───           │  ← Subtle footer
│  ● Active · 7 collectors ·          │
│    2.3 MB today                     │
│                                     │
├─────────────────────────────────────┤
│ [🔍] [⏱️] [🗺️] [📊] [⚙️]           │  ← Bottom nav
└─────────────────────────────────────┘
```

**Search Result View** (replaces quick actions / recent queries):
```
┌─────────────────────────────────────┐
│  ┌─────────────────────────────┐    │
│  │ 🔍 Where was I yesterday    │ ✕  │  ← Search bar with query, X to clear
│  │    afternoon?               │    │
│  └─────────────────────────────┘    │
│                                     │
│  ┌─────────────────────────────┐    │
│  │  📍 Answer                   │    │  ← Result card (elevated surface)
│  │                              │    │
│  │  Yesterday afternoon         │    │
│  │  (12:00 - 18:00):           │    │
│  │                              │    │
│  │  You were at the Office      │    │
│  │  from 12:00 to 17:15.       │    │
│  │  Then drove to Dizengoff     │    │
│  │  Center (arrived 17:45).     │    │
│  │                              │    │
│  │  Confidence: 95% (4 sources) │    │
│  │                              │    │
│  │  [▼ View raw data]           │    │  ← Expandable section
│  └─────────────────────────────┘    │
│                                     │
│  You might also ask:                │  ← Suggested follow-ups
│  ┌───────────────────────┐          │
│  │ "How long was I there?" │         │  ← Tappable chips
│  └───────────────────────┘          │
│  ┌───────────────────────┐          │
│  │ "Show me on the map"   │         │
│  └───────────────────────┘          │
└─────────────────────────────────────┘
```

**Contract:**
```kotlin
object SearchContract {
    data class State(
        val query: String = "",
        val isSearchFocused: Boolean = false,
        val isLoading: Boolean = false,
        val result: QueryResult? = null,
        val isRawDataExpanded: Boolean = false,
        val recentQueries: List<RecentQueryItem> = emptyList(),
        val suggestedFollowUps: List<String> = emptyList(),
        val recordingStatus: RecordingStatus = RecordingStatus.Idle,
        val error: UiError? = null,
    )

    sealed interface Action {
        data class QueryChanged(val text: String) : Action
        data object SubmitQuery : Action
        data object ClearQuery : Action
        data class QuickActionClicked(val quickAction: QuickAction) : Action
        data class RecentQueryClicked(val query: String) : Action
        data class SuggestedQueryClicked(val query: String) : Action
        data object ToggleRawData : Action
    }

    sealed interface Event {
        data class NavigateToTimeline(val date: LocalDate) : Event
        data class NavigateToMap(val date: LocalDate) : Event
        data class ShowSnackbar(val message: UiText) : Event
    }
}
```

### 3.2 Timeline Screen

**Purpose:** Visual chronological view of the user's day.

**Layout:**
```
┌─────────────────────────────────────┐
│  ◀  Wednesday, Feb 14  ▶    [Day▾] │  ← Date nav + view mode selector
├─────────────────────────────────────┤
│                                     │
│  Summary                            │  ← Collapsible day summary card
│  ┌─────────────────────────────┐    │
│  │ 🚶 8,421 steps  📱 2h 15m   │    │
│  │ 🏠 7h home  🏢 6h office    │    │
│  │ 🌙 Sleep: 11:30pm - 7:15am  │    │
│  └─────────────────────────────┘    │
│                                     │
│  ── 07:15 ─────────────────────     │  ← Time marker (left-aligned)
│  ┌─────────────────────────────┐    │
│  │ ☀️  Woke up                  │    │  ← Event card
│  │  First screen unlock         │    │
│  └─────────────────────────────┘    │
│  │                                  │  ← Vertical line connector
│  ── 07:30 ─────────────────────     │
│  ┌─────────────────────────────┐    │
│  │ 🏠 At Home                   │    │  ← Location block
│  │  Duration: 1h 15m            │    │
│  │  Screen: 12 min              │    │
│  │  Apps: WhatsApp, News        │    │
│  └─────────────────────────────┘    │
│  │                                  │
│  ── 08:45 ─────────────────────     │
│  ┌─────────────────────────────┐    │
│  │ 🚗 Driving                   │    │  ← Transit block
│  │  Duration: 35 min            │    │
│  │  Distance: 18 km             │    │
│  └─────────────────────────────┘    │
│  │                                  │
│  ── 09:20 ─────────────────────     │
│  ┌─────────────────────────────┐    │
│  │ 🏢 Office                    │    │  ← Long location block
│  │  Duration: 3h 45m            │    │
│  │  WiFi: BankCorp-5G           │    │
│  │  Steps: 1,240                │    │
│  │  Top app: Android Studio     │    │
│  │  Screen: 3h 20min            │    │
│  └─────────────────────────────┘    │
│  ...                                │
│                                     │
├─────────────────────────────────────┤
│ [🔍] [⏱️] [🗺️] [📊] [⚙️]           │
└─────────────────────────────────────┘
```

**Week view:** Horizontal calendar strip at top, showing daily summaries as small cards below each day.

**Month view:** Calendar grid with color-coded days (green = active, gray = low activity, blue = travel).

**Contract:**
```kotlin
object TimelineContract {
    data class State(
        val selectedDate: LocalDate = LocalDate.now(),
        val viewMode: ViewMode = ViewMode.DAY,
        val timelineEntries: List<TimelineEntry> = emptyList(),
        val dailySummary: DailySummary? = null,
        val isLoading: Boolean = false,
        val isSummaryExpanded: Boolean = true,
        val error: UiError? = null,
    ) {
        enum class ViewMode { DAY, WEEK, MONTH }
    }

    sealed interface Action {
        data class DateSelected(val date: LocalDate) : Action
        data class ViewModeChanged(val mode: State.ViewMode) : Action
        data object NextPeriod : Action
        data object PreviousPeriod : Action
        data class EntryClicked(val entry: TimelineEntry) : Action
        data object ToggleSummary : Action
        data object Refresh : Action
    }

    sealed interface Event {
        data class NavigateToMap(val date: LocalDate) : Event
        data class ShowEntryDetail(val entryId: Long) : Event
    }
}
```

### 3.3 Map Screen

**Purpose:** Visualize location history on an offline map.

**Layout:**
```
┌─────────────────────────────────────┐
│  Map · Feb 14            [Day ▾]    │
├─────────────────────────────────────┤
│                                     │
│  ┌─────────────────────────────┐    │
│  │                             │    │
│  │        MAP (OSMDroid)       │    │  ← Full-width map
│  │                             │    │     Shows path with markers
│  │    📍 Home                  │    │     Color-coded by transport:
│  │      ╲                      │    │       Blue = walking
│  │       ╲ 🚗 (red line)      │    │       Red = driving
│  │        ╲                    │    │       Green = cycling
│  │    📍 Office                │    │
│  │      ╲                      │    │
│  │    📍 Restaurant            │    │
│  │                             │    │
│  └─────────────────────────────┘    │
│                                     │
│  ── Visited Today ──────────        │  ← Bottom sheet (draggable)
│  ┌─────────────────────────────┐    │
│  │ 🏠 Home         7h 15m      │    │
│  │ 🏢 Office       6h 40m      │    │
│  │ 🍽️ Aroma        45m         │    │
│  └─────────────────────────────┘    │
│                                     │
├─────────────────────────────────────┤
│ [🔍] [⏱️] [🗺️] [📊] [⚙️]           │
└─────────────────────────────────────┘
```

### Known places management (Places mode)

Each place row shows two action buttons:
- **Pencil icon** (indigo) — opens the Edit Place dialog
- **Trash icon** (gray) — permanently deletes the place

**Add Place dialog** (opened by the + FAB): Name, Category chips, Lat/Lng text fields, Radius slider (50–500m).

**Edit Place dialog** (opened by pencil): Same Name / Category / Radius fields, pre-filled with current values. Coordinates are intentionally not editable — moving a place requires delete + re-create.

### Key files
- `shared/.../domain/usecase/map/GetDayLocationSummaryUseCase.kt`
- `shared/.../ui/map/MapViewModel.kt`
- `shared/.../ui/map/MapContent.kt` — OsmMapView, PlacesList, AddPlaceDialog, EditPlaceDialog
- `shared/.../ui/map/MapContract.kt` — AddPlaceDialogState, EditPlaceDialogState
- `shared/.../domain/usecase/place/UpdatePlaceUseCase.kt`
- `shared/.../domain/model/map/LocationStay.kt`

### 3.4 Insights Screen

**Purpose:** Display patterns, trends, and behavioral insights.

**Data guarantees:** All three weekly charts (Steps, Screen Time, Sleep) always render exactly 7 bars — one per day. Data is sourced from raw collector records rather than pre-computed DailySummary rows, so the charts are never missing bars due to a missed nightly worker run. Sleep bars for nights with no detectable data render at 20% height in muted gray (rather than disappearing); only non-null bars are tappable.

**Ranking period selector:** A compact "7 days / 30 days" chip row appears above the ranking cards (Top places, Top apps, Top contacts). The selected chip is highlighted in indigo. Switching period re-fetches ranking data only — the trend bar charts above are unaffected.

**Top contacts card:** When CALL_LOG data is available, a `TopContactsCard` appears below the top places / top apps row, showing up to 5 contacts ranked by call count for the selected period. Each row shows display name (or "Unknown" for unsaved numbers), call count, total duration, and missed call count. No additional permissions beyond `READ_CALL_LOG` are needed — contact names come from `CallLog.Calls.CACHED_NAME`.

**Layout:**
```
┌─────────────────────────────────────┐
│  Insights                           │
├─────────────────────────────────────┤
│                                     │
│  [This Week] [This Month] [Custom]  │  ← Period selector
│                                     │
│  Sleep                              │  ← Section
│  ┌─────────────────────────────┐    │
│  │ Avg bedtime: 11:42 PM       │    │
│  │ Avg wake: 7:18 AM           │    │
│  │ Avg duration: 7h 36m        │    │
│  │ ┌───────────────────────┐   │    │
│  │ │  📊 Sleep chart (7 days)│  │    │  ← Mini bar chart
│  │ └───────────────────────┘   │    │
│  └─────────────────────────────┘    │
│                                     │
│  Activity                           │
│  ┌─────────────────────────────┐    │
│  │ Avg steps: 7,840/day        │    │
│  │ Most active: Tuesday         │    │
│  │ Least active: Saturday       │    │
│  │ ┌───────────────────────┐   │    │
│  │ │  📊 Steps chart        │   │    │
│  │ └───────────────────────┘   │    │
│  └─────────────────────────────┘    │
│                                     │
│  Screen Time                        │
│  ┌─────────────────────────────┐    │
│  │ Avg: 3h 42m/day              │    │
│  │ Avg pickups: 67/day          │    │
│  │ Top app: WhatsApp (48m)      │    │
│  │ ┌───────────────────────┐   │    │
│  │ │  📊 Screen time chart  │   │    │
│  │ └───────────────────────┘   │    │
│  └─────────────────────────────┘    │
│                                     │
│  💡 Insight                         │
│  ┌─────────────────────────────┐    │
│  │ "On days you walk more than  │    │  ← AI-generated insight card
│  │  8,000 steps, you go to bed  │    │     Accent border, special styling
│  │  20 minutes earlier."        │    │
│  └─────────────────────────────┘    │
│                                     │
├─────────────────────────────────────┤
│ [🔍] [⏱️] [🗺️] [📊] [⚙️]           │
└─────────────────────────────────────┘
```

### 3.5 Settings Screen

**Purpose:** Control what data is collected, manage storage, configure privacy.

**Layout:** As defined in SPEC.md Section 7.5 — collector toggles, known places, data management, battery profile, language selection.

**Key UX details:**
- Each collector toggle shows a brief description of what it does
- Sensitive collectors (Audio Level) show an extra privacy notice
- "Delete Data" actions require confirmation dialog with "type DELETE to confirm"
- Storage stats update in real-time as user views the screen
- Battery profile shows estimated daily impact percentage

### 3.6 Onboarding Screen

**Purpose:** First-launch experience. Minimal, builds trust.

**Flow:** 5 screens (horizontal pager):

**Screen 1 — Welcome:**
- App logo (animated subtle glow)
- "BlackBox" title
- "Your life has a black box. Now you can read it."
- "Everything stays on your phone. No accounts, no cloud."
- [Get Started →]

**Screen 2 — Choose Collectors:**
- "What should BlackBox record?"
- Three preset buttons: [Essential] [Balanced ✓] [Maximum]
- List of collectors with toggles (pre-filled based on preset)
- Each shows one-line description

**Screen 3 — Permissions:**
- Permissions requested one at a time
- Each shows icon + clear explanation
- "Allow" / "Not now" buttons
- Skipped permissions can be granted later in Settings

**Screen 4 — Set Home:**
- "Where is home?"
- Map view with draggable pin
- "This helps BlackBox know when you're home vs away"
- [Set Home] / [Skip for now]

**Screen 5 — Ready:**
- Checkmark animation
- "BlackBox is now recording. Forget about it — it's there when you need it."
- [Open BlackBox]

---

## 4. Shared Components Specification

### 4.1 Recording Status Indicator

Small persistent indicator showing collection status:
```
● Active · 7/10 collectors · 2.3 MB today
○ Paused · tap to resume
⚠ Limited · 3 permissions missing
```

### 4.2 Date Navigator

Horizontal date picker used in Timeline and Map:
```
    ◀   Wednesday, February 14, 2026   ▶
```
- Swipe left/right to change date
- Tap date text to open calendar picker
- Long press to jump to today

### 4.3 Confidence Badge

Shows data reliability:
- `95%` → Green badge
- `80%` → Amber badge  
- `60%` → Orange badge
- `< 60%` → Red badge with tooltip explaining why

### 4.4 Empty State View

Consistent empty state across all screens:
- Centered illustration (subtle, monochrome)
- Title: "No Data Yet" / "Nothing Here"
- Description: Context-specific message
- Optional CTA button

### 4.5 Error View

Consistent error display:
- Error icon
- Title: "Something went wrong"
- Description: Human-readable error message
- [Retry] button when action is retryable

---

## 5. Responsive Design Notes

- **Phone (portrait):** Default layout as shown above
- **Phone (landscape):** Timeline and Map use more horizontal space; search results side-by-side with suggestions
- **Tablet:** Two-pane layout — list on left, detail on right (timeline + event detail, search + results)
- **Foldable:** Adapt based on window size class

Use Compose `WindowSizeClass` for responsive breakpoints:
```kotlin
val windowSizeClass = calculateWindowSizeClass(this)
when (windowSizeClass.widthSizeClass) {
    WindowWidthSizeClass.Compact -> CompactLayout()
    WindowWidthSizeClass.Medium -> MediumLayout()
    WindowWidthSizeClass.Expanded -> ExpandedLayout()
}
```
