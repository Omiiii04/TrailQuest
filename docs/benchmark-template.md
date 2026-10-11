# TrailQuest Benchmark & Physical Device Verification

## Environment & Hardware
- **Device Name:** Samsung Galaxy F34 5G (SM-E346B) *(Note: SM-E346B is the Galaxy F34 5G; Galaxy M34 5G uses SM-M346B)*
- **OS Version:** Android 16
- **Build Mode:** Release (-O3, NDEBUG)
- **Architecture:** arm64-v8a only
- **Model:** Gemma 3 270M IT Q4_K_M GGUF (HF revision `88762a3700deaf5b03184019806c88dc9b5c8876`)
- **Permissions:** Verified Offline (zero network permissions, zero location permissions in AndroidManifest.xml)
- **Inference Threads:** 4 threads (in-process JNI via llama.cpp)

---

## Submission Sprint Verification (October 11, 2026)

### Verified Artifact Metrics
- **Release APK Size:** 403.7 MB (385.0 MiB) — exact size: 403,727,280 bytes
- **Model File Size:** 253.1 MB (241.4 MiB) — exact size: 253,114,880 bytes
- **Model Integrity:** SHA-256 `55aed1673d2f3014a739732c659c4995fc57abf6e4e9c68462fb2ceee820ea2f`
- **Signing Scheme:** APK Signature Scheme v2 (Signer: `CN=Omii, O=SPPU, L=India`)
- **ABI:** `arm64-v8a` only
- **SDK Targets:** minSdk 24 (Android 7.0), targetSdk 36, compileSdk 36

### Live Physical Device Smoke Test (Samsung Galaxy F34 5G, SM-E346B)
1. **Lazy Model Initialization:** Verified via logcat. Process starts on Home screen (`MainActivity`) without loading the model, allocating native context, or calling JNI.
2. **On-Device Mission Generation:** Generated a 15-minute Easy Walk expedition locally in ~6.4s wall time:
   - Raw output: strictly formatted JSON (`title`, `activity`, `durationMinutes`, `difficulty`, `missions`).
   - Schema validation: `isValid=true`, `isFallbackApplied=false`, `reason=null`.
   - UI display: rendered mission title, on-device AI badge, and interactive step checklist.
3. **Interactive Task UI:** Checked off mission tasks interactively on device.
4. **Developer Tools Verification Run (1x iteration, 4 threads):**
   - **Time to First Token (TTFT):** 1128 ms
   - **Throughput:** 14.51 tokens/second
   - **Generation Time:** 2619 ms
   - **Generated Tokens:** 38 tokens
   - **Validation Status:** Valid schema (1/1 model generated)
5. **Navigation Responsiveness:** Verified seamless back navigation from Mission to Home and Benchmark to Home.

### Deterministic Fallback Verification (Unit Test Suite)
- **58 unit tests across 8 test suites** pass with 0 failures, 0 errors.
- `MissionValidationTest` (24 tests) deterministically verifies:
  - All 36 permutations (4 activities × 3 durations × 3 difficulties) produce complete, valid fallback missions.
  - Activity-specific contradiction detection (e.g., rejecting sprint tasks in walking missions).
  - Schema error handling for empty arrays, non-string items, missing fields, and unparseable JSON.

---

## Historical Benchmark Data (Baseline Session)

> **Context:** The metrics below were recorded during an initial 20-iteration baseline benchmarking session on an earlier release build artifact (403.6 MB / 384.9 MiB, 403,637,744 bytes) on the same Samsung Galaxy F34 5G (SM-E346B) hardware.

### Application Setup & Storage Metrics (Historical)
- **Release APK Size:** 403.6 MB (384.9 MiB) — 403,637,744 bytes
- **Model File Size:** 253.1 MB (241.4 MiB) — 253,114,880 bytes

#### First-Launch Initialization (Cold Storage Setup)
- **Asset Copy Time (APK to app storage):** ~980 ms
- **Model File Verification Time:** ~15 ms
- **Model Load Time (llama.cpp engine init):** ~105 ms
- **Total First-Launch Setup Time:** ~1,100 ms

#### Subsequent-Launch Behavior (Warm Start)
- **Asset Copy Time:** 0 ms (skipped - existing file matches exact byte count)
- **Model File Verification Time:** < 2 ms
- **Model Load Time (llama.cpp engine init):** ~105 ms
- **Total Subsequent Launch Setup Time:** ~107 ms

### Historical Inference Performance (20 Iterations)
- **Prompt:** "Create one simple 30-minute outdoor mission for a person going for a walk. Return ONLY valid JSON."
- **Time to First Token (TTFT Avg):** 976 ms
- **Throughput (Tokens per Second Avg):** 15.50 tok/s
- **Average Generation Latency:** 2,438 ms / iteration
- **Average Generated Token Count:** 37.8 tokens
- **Total Generated Tokens:** 756 tokens
- **Total Benchmark Wall Time:** 48,823 ms

### Historical Reliability & Fallback Accounting (20 Iterations)
- **Model-Generated Success (Valid Structured Missions):** 19 / 20 (95%)
- **Malformed Model Output (Unparseable JSON):** 0 / 20 (0%)
- **Schema Incomplete (Invalid / missing fields):** 1 / 20 (5% - empty missions array cleanly identified)
- **Deterministic Fallbacks Applied:** 1 / 20 (5% - deterministic fallback mission reliably delivered)
- **End-User Mission Delivery Success Rate:** 20 / 20 (100% of runs delivered a complete, valid mission)

### Physical, Power & Offline Impact
- **Battery Drain (20 iterations):** < 1%
- **Thermal Behavior:** Nominal, cool to the touch with in-process JNI multi-threading
- **Network / Connectivity:** Complete offline operation, zero network sockets opened, zero impact on USB tethering
