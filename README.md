# BlackBox — Your Life Has a Black Box. Now You Can Read It.

BlackBox is a **privacy-first, offline-first personal life flight recorder** for Android, built with Kotlin Multiplatform. It passively captures metadata from your device — where you go, what you do, which apps you use, how you sleep — and lets you query it in plain language.

**Everything stays on-device. No cloud. No accounts. No ads.**

---

## What It Does

| Feature | Description |
|---------|-------------|
| **Passive Capture** | Runs silently in the background, recording location, activity, WiFi, screen time, calls, and more |
| **Natural Language Search** | Ask "Where was I yesterday afternoon?" or "How many steps last week?" |
| **Timeline** | Browse your day as a chronological story — location stays, activities, calls |
| **Insights** | Step trends, sleep patterns, screen time breakdowns, top apps |
| **Map** | Visualise your day's route on an offline OpenStreetMap |
| **Known Places** | Name your locations (Home, Office, Gym) and see them appear everywhere |
| **Encrypted Storage** | All data encrypted at rest with AES-256 (Android Keystore + SQLCipher) |
| **Bilingual** | Full English and Hebrew support |

---

## Quick Start

```bash
# Build
./gradlew :androidApp:assembleDebug

# Install on device
./gradlew :androidApp:installDebug

# Run tests
./gradlew :shared:testDebugUnitTest

# Lint
./gradlew :shared:detekt && ./gradlew :androidApp:detekt
```

**Requirements**: Android 8.0+ (API 26), 50 MB storage.

---

## Architecture in One Picture

```
┌─────────────────────────────────────────────────────────┐
│  Android Device                                         │
│  ┌──────────────┐    ┌────────────────────────────────┐ │
│  │ Sensors &    │───▶│ BlackBoxService (Foreground)   │ │
│  │ System APIs  │    │  CollectorOrchestrator         │ │
│  │  GPS / WiFi  │    │   LocationCollector            │ │
│  │  CallLog     │    │   ActivityCollector            │ │
│  │  Screen      │    │   ScreenStateCollector ...     │ │
│  └──────────────┘    └──────────────┬─────────────────┘ │
│                                     │ CollectedRecord    │
│                          ┌──────────▼───────────┐       │
│                          │ SQLDelight Database   │       │
│                          │ (SQLCipher AES-256)   │       │
│                          └──────────┬───────────┘       │
│                                     │                   │
│    ┌────────────────────────────────▼──────────────┐    │
│    │ Use Cases (business logic)                    │    │
│    │  GetTimelineUseCase                           │    │
│    │  DetectSleepSessionsUseCase                   │    │
│    │  ProcessQueryUseCase (NLP engine)             │    │
│    └────────────────────────────────┬──────────────┘    │
│                                     │                   │
│    ┌────────────────────────────────▼──────────────┐    │
│    │ Compose UI  (MVI — State / Action / Event)    │    │
│    │  Search  Timeline  Map  Insights  Settings    │    │
│    └───────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
Kmpstealthapp/
├── shared/                    # KMP shared module (~70% of code)
│   └── src/commonMain/kotlin/com/blackbox/
│       ├── domain/            # Pure Kotlin — models, use cases, repository interfaces
│       ├── data/              # SQLDelight implementations + mappers
│       └── ui/                # Compose Multiplatform screens and ViewModels
│
└── androidApp/                # Android application shell
    └── src/androidMain/kotlin/com/blackbox/android/
        ├── collector/         # Sensor collectors
        ├── service/           # Foreground service + BootReceiver
        ├── worker/            # WorkManager jobs
        ├── security/          # Keystore + Biometrics
        └── di/                # Koin modules
```

---

## Documentation Map

### Start Here (Reading Order)

| # | Document | What you learn |
|---|----------|---------------|
| 1 | `README.md` | This file — project overview and quick start |
| 2 | `DEVELOPER_TUTORIAL.md` | Full developer onboarding — setup, patterns, how to add features |
| 3 | `FEATURES_GUIDE.md` | Every user-facing feature, what data it uses, how it works |
| 4 | `ARCHITECTURE_DEEP_DIVE.md` | Every architectural decision and why |
| 5 | `DATA_FLOW.md` | Sensor → DB → UI data path with sequence diagrams |
| 6 | `ALGORITHMS.md` | All 12 algorithms explained (sleep, places, NLP, etc.) |
| 7 | `TESTING_GUIDE.md` | How to write and run tests |

### Reference (Look Up When Working on a Specific Area)

| Area | Document |
|------|----------|
| Full product spec | `BLACKBOX_SPEC.md` |
| Coding standards & conventions | `DEV_SPECS.md` |
| Collector details + intervals | `COLLECTORS.md` |
| Database schema | `DATABASE.md` |
| NLP query engine | `QUERY_ENGINE.md` |
| Security model | `SECURITY.md` |
| Battery optimization | `BATTERY.md` |
| UI screen specs | `UI_SCREENS.md` ⚠️ theme section is outdated — see note below |
| Timeline behavior & debugging | `TIMELINE_BEHAVIOR.md` |
| AI agent coding rules | `CLAUDE.md` |

> **UI_SCREENS.md theme note:** The spec describes a dark blue + amber theme. The actual implemented theme is a cyberpunk aesthetic: near-black background (`#050510`), neon green (`#00FF41`), electric cyan (`#00D4FF`), magenta accent (`#FF0064`), and monospace typography throughout. See `shared/src/commonMain/kotlin/com/blackbox/ui/theme/` for the live implementation.
