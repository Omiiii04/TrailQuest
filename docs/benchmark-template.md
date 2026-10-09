# TrailQuest Benchmark Results

## Environment
- **Device Name:** Samsung Galaxy M34 5G (SM-E346B)
- **OS Version:** Android 16
- **Build Mode:** Release (-O3, NDEBUG)
- **Architecture:** arm64-v8a only
- **Model:** Gemma 3 270M IT Q4_K_M GGUF (HF revision `88762a3700deaf5b03184019806c88dc9b5c8876`)
- **Permissions:** Verified Offline (NO INTERNET declared in AndroidManifest.xml)
- **Inference Threads:** 4 threads

## Application Metrics
- **APK Size:** 384.9 MB (403,637,744 bytes)
- **Model File Size:** 241.4 MB (253,114,880 bytes, SHA-256: `55aed1673d2f3014a739732c659c4995fc57abf6e4e9c68462fb2ceee820ea2f`)
- **First-Launch Asset Verification & Load Time:** ~1,100 ms

## Inference Performance (Physical Device - 20 Iterations)
- **Time to First Token (TTFT Avg):** 976 ms
- **Throughput (Tokens per Second Avg):** 15.50 tok/s
- **Average Generation Latency:** 2,438 ms / iteration
- **Average Generated Token Count:** 37.8 tokens
- **Total Generated Tokens:** 756 tokens
- **Total Benchmark Wall Time:** 48,823 ms

## Reliability & Schema Compliance (20 Iterations)
- **Valid Structured Missions (All required fields):** 19 / 20 (95%)
- **Fallback Triggered (Incomplete schema):** 1 / 20 (5% - empty missions array cleanly substituted by deterministic fallback)
- **Invalid / Malformed JSON:** 0 / 20 (0%)

## Physical & Power Impact
- **Battery Drain (20 iterations):** < 1%
- **Thermal Behavior:** Nominal, cool to the touch with in-process JNI multi-threading
- **Network / Connectivity:** Complete offline operation, zero network sockets opened, zero impact on USB tethering
