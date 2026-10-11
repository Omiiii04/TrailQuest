# TrailQuest 🌿

**AI-powered outdoor mission generator — completely offline, completely private.**

TrailQuest generates personalized outdoor activity missions using a local large language model running entirely on your Android device. No internet connection, no location tracking, no cloud APIs — just you and the trail.

Built for the **Hacktoberfest Open-Source AI Challenge Week 1**.

---

## The Problem

Most outdoor fitness and exploration apps demand internet connectivity, continuous GPS tracking, and cloud accounts. TrailQuest takes the opposite approach: it uses on-device AI inference to create unique, structured outdoor missions without any network or location permissions.

---

## Features

| Feature | Details |
|---|---|
| **Activities** | Walk, Hike, Run, Nature Explore |
| **Durations** | 15, 30, or 60 minutes |
| **Difficulty Levels** | Easy, Moderate, Challenging |
| **AI Model** | Gemma 3 270M IT (`Q4_K_M` quantization) |
| **Inference Engine** | Local, in-process via `llama.cpp` JNI bridge |
| **Validation** | Strict JSON schema validation with activity-specific contradiction detection |
| **Deterministic Fallbacks** | 36 offline fallback missions covering all activity × duration × difficulty combinations |
| **Developer Tools** | Built-in benchmark and verification screen with configurable thread counts |
| **Privacy & Security** | Zero network permissions (`INTERNET` absent), zero location permissions (`ACCESS_FINE_LOCATION` absent) |

---

## Screenshots

Genuine captures from physical device testing on a Samsung Galaxy F34 5G (Android 16, `arm64-v8a`):

| Home Screen | Expedition Setup | Expedition Review |
|:---:|:---:|:---:|
| ![Home Screen](docs/screenshot_home.png) | ![Expedition Setup](docs/screenshot_setup.png) | ![Expedition Review](docs/screenshot_review.png) |

| AI-Generated Mission | Completed Mission | Developer Tools & Benchmark |
|:---:|:---:|:---:|
| ![Generated Mission](docs/screenshot_generated_mission.png) | ![Completed Mission](docs/screenshot_mission_completed.png) | ![Benchmark Screen](docs/screenshot_benchmark_verified.png) |

---

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                      MainActivity                      │
│                  (AndroidViewModel)                    │
├────────────────────────────────────────────────────────┤
│                     MainNavigation                     │
│  ┌──────┐    ┌───────┐    ┌────────┐    ┌──────────┐   │
│  │ Home │ →  │ Setup │ →  │ Review │ →  │ Mission  │   │
│  └──────┘    └───────┘    └────────┘    └──────────┘   │
│      │                         ↑                       │
│      └────── Developer ────────┘                       │
│              Benchmark                                 │
├────────────────────────────────────────────────────────┤
│                 LlmProvider (interface)                │
│            LocalGemmaProvider (Mutex-guarded)          │
│               ↓ runTransaction() / generate()          │
├────────────────────────────────────────────────────────┤
│              InferenceEngine (interface)               │
│           InferenceEngineImpl (JNI bridge)             │
│               ↓ native calls                           │
├────────────────────────────────────────────────────────┤
│              ai_chat.cpp (C++ / JNI)                   │
│               ↓ llama.cpp API                          │
├────────────────────────────────────────────────────────┤
│          llama.cpp (git submodule, v0.6.0+)            │
│               ↓                                        │
│       gemma-3-270m-it-q4_k_m.gguf (253.1 MB)           │
└────────────────────────────────────────────────────────┘
```

### Key Design Decisions
- **Lazy Model Initialization:** The LLM provider and native runtime are not loaded until the user requests mission generation or navigates to Developer Tools. The Home and Expedition Setup screens never touch the model or native libraries.
- **Atomic Inference Transactions:** Mutex-guarded `runTransaction()` ensures context reset, prompt application, and token streaming cannot be interleaved.
- **Early-Stop JSON Parsing:** Generation halts as soon as a valid, complete JSON object matching schema is parsed, avoiding wasted inference cycles.
- **Structured Validation with Fallbacks:** Every model response is strictly validated (correct keys, types, matching activity, duration, and contradiction checks). Any malformed or non-compliant response immediately resolves to an activity-tailored deterministic fallback mission.
- **Robust Native Error Boundary:** JNI thread queries catch `Throwable` to safely tolerate asynchronous native library initialization.

---

## Prerequisites

| Requirement | Details |
|---|---|
| **Operating System** | Windows 10/11 (tested), macOS / Linux |
| **Android Studio** | Ladybug (2024.2.1) or later |
| **JDK** | 17 (Eclipse Adoptium OpenJDK 17 recommended) |
| **Android SDK** | API 36 (`compileSdk`, `targetSdk`) |
| **Android NDK & CMake** | NDK 27+, CMake 3.22.1 |
| **Python** | 3.8+ (for model fetch script) |
| **Git** | With submodule support |
| **Target Device** | Android 7.0+ (API 24), `arm64-v8a` architecture (physical device required for local inference) |

---

## Build Instructions (Windows / PowerShell)

### 1. Clone repository with submodules

```powershell
git clone --recurse-submodules https://github.com/Omiiii04/TrailQuest.git
cd TrailQuest
```

If already cloned without `--recurse-submodules`:

```powershell
git submodule update --init --recursive
```

### 2. Download and verify the model

```powershell
python scripts/fetch_model.py
```

This verifies or downloads [Gemma 3 270M IT Q4_K_M GGUF](https://huggingface.co/gguf-org/gemma-3-270m-it-gguf) pinned to immutable Hugging Face commit `88762a3700deaf5b03184019806c88dc9b5c8876`:
- **Expected SHA-256:** `55aed1673d2f3014a739732c659c4995fc57abf6e4e9c68462fb2ceee820ea2f`
- **Expected Size:** 253,114,880 bytes (253.1 MB / 241.4 MiB)
- **Target Location:** `app/src/main/assets/models/gemma-3-270m-it-q4_k_m.gguf`

### 3. Run unit test suite

```powershell
.\gradlew.bat test
```

Executes all 58 unit tests across 8 test suites.

### 4. Build release APK

```powershell
.\gradlew.bat assembleRelease
```

The signed release APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

### 5. Install on physical device

```powershell
adb install -r "app\build\outputs\apk\release\app-release.apk"
```

---

## Design Constraints & Privacy

| Constraint | Details & Rationale |
|---|---|
| **arm64-v8a Only** | The Gemma 3 270M model and `llama.cpp` JNI library target 64-bit ARM architectures with NEON optimizations. x86_64 emulators are not supported. |
| **Zero Network Permissions** | `AndroidManifest.xml` declares no `android.permission.INTERNET`. All inference occurs 100% locally on device. |
| **Zero Location Permissions** | No `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. Missions are prompt-guided activity challenges, not GPS track-routes. |
| **Bundled Uncompressed Assets** | `androidResources { noCompress += "gguf" }` packages the GGUF model uncompressed for reliable runtime extraction and mapping. |
| **APK Size: 403.7 MB (385.0 MiB)** | Total APK size is 403,727,280 bytes due to bundling the 253.1 MB model for true out-of-the-box offline execution. |

---

## Verified Build & Test Status

Measured during the **Submission Sprint (October 11, 2026)**:

| Check | Result | Verification Method |
|---|---|---|
| **Git Commit** | `3f0942f` | `git log -1` |
| **Model Integrity** | Passed | SHA-256 and byte-size match |
| **Unit Tests** | 58 / 58 passing (0 failures, 0 errors) | `.\gradlew.bat test` |
| **Release Build** | Successful | `.\gradlew.bat assembleRelease` |
| **Release APK Size** | 403.7 MB (385.0 MiB) / 403,727,280 bytes | File system inspection |
| **Signing Scheme** | Scheme v2 Verified (`CN=Omii, O=SPPU, L=India`) | `apksigner verify --verbose` |
| **Manifest Permissions** | Zero network or location permissions | `aapt2 dump badging` |
| **Native ABI** | `arm64-v8a` only | `aapt2 dump badging` |
| **Device Smoke Test** | Passed (Lazy init, generation, interactive UI, benchmark) | Samsung Galaxy F34 5G (SM-E346B, Android 16) |

### Test Suite Breakdown

| Suite | Tests | Covered Scope |
|---|---|---|
| `MissionValidationTest` | 24 | Schema validation, all 36 permutations, contradiction detection, fallback resolution |
| `LocalGemmaProviderTest` | 14 | Atomicity, mutex locks, context reset, error recovery, cleanup |
| `ExpeditionConfigTest` | 7 | Configuration constraints, duration bounds, draft state |
| `MissionGenerationFlowTest` | 3 | Draft flow, guards, auto-generate flag |
| `MissionPromptTest` | 3 | Prompt generation across all activity types |
| `NavigationRoutingTest` | 3 | Route key integrity and equality |
| `LazyModelInitTest` | 2 | Lazy provider lifecycle and deferred instantiation |
| `MainScreenViewModelTest` | 2 | Initial UI state management |

---

## Benchmark Metrics

See [`docs/benchmark-template.md`](docs/benchmark-template.md) for full benchmark documentation.

- **Test Device:** Samsung Galaxy F34 5G (`SM-E346B`), Android 16, Exynos 1280 (`arm64-v8a`), 4 inference threads.
- **Sprint Verification Run:** Generation time 2619 ms, TTFT 1128 ms, Throughput 14.51 tok/s, 38 tokens, 100% valid schema.
- **Historical 20-Iteration Baseline:** TTFT ~976 ms avg, Throughput ~15.50 tok/s avg, 100% mission delivery success rate (19/20 model-generated + 1/20 deterministic fallback).

---

## Known Limitations

- **Small Model Nuances:** Gemma 3 270M IT is a compact edge model. It occasionally produces minor JSON formatting variations; TrailQuest's strict validation and deterministic fallback system guarantee the user always receives a complete mission.
- **Architecture Restriction:** Requires physical 64-bit ARM (`arm64-v8a`) hardware. It will not run on standard x86/x86_64 emulators.
- **Initial APK Download Size:** At 403.7 MB (385.0 MiB), the APK is large because the model is bundled in assets to ensure zero-setup offline capability.
- **Context Scope:** Single-turn single-shot transaction per mission; context is cleared between runs.

---

## License & Attribution

- **Gemma 3** — [Gemma Terms of Use](https://ai.google.dev/gemma/terms) (Google DeepMind). See [`docs/GEMMA_TERMS_OF_USE.md`](docs/GEMMA_TERMS_OF_USE.md).
- **llama.cpp** — [MIT License](https://github.com/ggerganov/llama.cpp/blob/master/LICENSE) (Georgi Gerganov and contributors).
- **GGUF quantization** — [gguf-org/gemma-3-270m-it-gguf](https://huggingface.co/gguf-org/gemma-3-270m-it-gguf) on Hugging Face.
- **AndroidX & Jetpack Compose** — [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
- **Kotlin** — [Apache License 2.0](https://kotlinlang.org/docs/faq.html#is-kotlin-free).
