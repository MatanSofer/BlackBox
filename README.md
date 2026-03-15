# BlackBox — Personal Life Flight Recorder

> *Your life has a black box. Now you can read it.*

BlackBox is a **privacy-first, offline-first personal recorder** for Android. It runs silently in the background, capturing contextual metadata from your device — where you were, what you were doing, how long you slept, which apps you used — and lets you query it all in plain English.

Everything stays on your phone. No accounts. No cloud. No ads.

---

## What it does

| Feature | Description |
|---------|-------------|
| **Passive collection** | Captures location, activity, screen time, app usage, calls, WiFi, sensors — continuously, in the background |
| **Natural language search** | Ask "Where was I last Tuesday?" or "How many steps did I walk this week?" and get an instant answer |
| **Timeline** | Scroll through your day as a chronological feed of events — stays, calls, activity transitions |
| **Interactive map** | Replay your route on an offline OpenStreetMap. Manage named places with a radius geofence |
| **Insights dashboard** | 7-day trend charts for steps, screen time, and sleep. Top places, apps, and contacts for the last 7 or 30 days |
| **Sleep detection** | Automatically detects when you fell asleep and woke up using screen-on patterns, airplane mode signals, and ambient light |
| **AI observations** | Optional on-device or API-powered insights about your patterns |

---

## Screenshots

*Coming soon*

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0+ |
| Multiplatform | Kotlin Multiplatform (KMP) |
| UI | Jetpack Compose + Compose Multiplatform |
| Architecture | Clean Architecture + MVI |
| Database | SQLDelight + SQLCipher (encrypted) |
| DI | Koin Multiplatform |
| Async | Coroutines + StateFlow / SharedFlow |
| Location | Google FusedLocationProvider |
| Activity | Google Activity Recognition API |
| Maps | OSMDroid (OpenStreetMap, fully offline) |
| Background | Foreground Service + WorkManager |
| Security | Android Keystore AES-256 + Biometric lock |

---

## Privacy

- **No network calls** — all data stays on-device (except optional AI queries you explicitly enable)
- **Encrypted database** — AES-256 via SQLCipher + Android Keystore
- **Biometric lock** — app locks after 1 minute in background
- **Phone numbers hashed** — stored as truncated SHA-256, never in plain text
- **Audio: level only** — the microphone measures ambient dB; raw audio is never written to disk
- **Tamper-detection chain** — each daily summary includes a SHA-256 hash chained to the previous day

---

## Architecture overview

```
UI (Compose Multiplatform)
    │
    ▼
ViewModel  ·  MVI — State / Action / Event
    │
    ▼
Use Cases  ·  pure Kotlin, shared module
    │
    ▼
Repository interfaces  ·  shared module
    │
    ▼
Repository implementations  ·  SQLDelight + platform APIs
    │
    ▼
Platform collectors  ·  Android foreground service
```

The `shared/` module contains ~70% of the codebase: all domain models, use cases, repository interfaces, the NLP query engine, and the Compose UI. `androidApp/` is a thin shell providing Android platform implementations (collectors, Keystore, sensors).

---

## Building

```bash
# Clone
git clone https://github.com/MatanSofer/BlackBox.git
cd BlackBox

# Build debug APK
./gradlew :androidApp:assembleDebug

# Install on connected device
./gradlew :androidApp:installDebug

# Run shared module tests
./gradlew :shared:testDebugUnitTest
```

**Requirements:** Android Studio Hedgehog+, JDK 17+, Android SDK 26 (minSdk) → 35 (targetSdk)

---

## Collectors

BlackBox runs 11 passive data collectors:

| Collector | What it captures | Battery cost |
|-----------|-----------------|-------------|
| Location | GPS coordinates every ~5 min | Low |
| Activity | Walking / running / driving transitions | Near zero |
| Screen State | Screen on/off/unlock events | Zero |
| App Usage | Foreground app and session duration | Near zero |
| Call Log | Incoming / outgoing / missed calls + contact name | Near zero |
| WiFi | Connected network + nearby SSIDs | Low |
| Connectivity | Network type, airplane mode, Bluetooth | Near zero |
| Battery | Level, charging state, temperature | Zero |
| Barometer | Atmospheric pressure (floor detection) | Low |
| Light Sensor | Ambient light level (sleep validation) | Low |
| Audio Level | Ambient dB level — never audio content | Medium |

All collectors can be individually toggled in Settings. Battery profiles automatically reduce collection frequency at low charge.

---

## Query engine

The built-in NLP query engine supports English and Hebrew:

```
"Where was I yesterday afternoon?"
  → "Yesterday afternoon (12:00–18:00) you were at the Office."

"How many steps did I walk last week?"
  → "You walked an average of 7,840 steps/day last week."

"When did I get home on Monday?"
  → "You arrived home at 18:32 on Monday."
```

Pipeline: normalise → detect language → parse time expression → classify intent → extract entities → build DB query → generate response. Entirely offline, no LLM required.

---

## Status

Active development. Core collection, search, timeline, map, and insights are functional.
iOS support is planned for a future phase.

---

## License

MIT
