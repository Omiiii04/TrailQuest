package com.example.trailquest

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

class MainActivity : ComponentActivity() {
    private lateinit var llmProvider: LlmProvider
    private lateinit var modelInstaller: ModelInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        llmProvider = LocalGemmaProvider(this)
        modelInstaller = ModelInstaller(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TrailQuestApp(llmProvider, modelInstaller)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        llmProvider.cleanup()
    }
}

private const val BENCH_PROMPT =
    "Generate exactly one JSON object with all three fields: \"title\", \"durationMinutes\", and \"missions\".\n\n" +
    "Schema:\n" +
    "{\"title\": \"Walking Mission\", \"durationMinutes\": 30, \"missions\": [\"Walk for 30 minutes\"]}\n\n" +
    "Request: Create one simple 30-minute walking mission. Output ONLY the JSON object starting with { and ending with }."

private class JsonCompleteException : Exception()

internal data class MissionValidationResult(
    val isValid: Boolean,
    val isFallbackApplied: Boolean,
    val isMalformed: Boolean,
    val statusLabel: String,
    val failureReason: String? = null,
    val finalMission: JSONObject
)

internal fun getDeterministicFallbackMission(): JSONObject {
    return JSONObject().apply {
        put("title", "Neighborhood Nature Walk")
        put("durationMinutes", 30)
        put("missions", JSONArray().apply {
            put("Walk at a brisk pace for 15 minutes along a local park or green trail.")
            put("Identify three distinct types of native trees or urban plants.")
            put("Complete a gentle 15-minute return loop while tracking step cadence.")
        })
    }
}

/**
 * Validates complete mission schema:
 * - non-empty title (String)
 * - actual positive integer durationMinutes (Int > 0)
 * - non-empty missions array (JSONArray containing only non-empty Strings)
 *
 * Strict validation: does not accept singular "mission" field.
 * Returns deterministic fallback when raw JSON is null/malformed or schema is incomplete.
 */
internal fun validateAndResolveMission(rawJson: JSONObject?): MissionValidationResult {
    if (rawJson == null) {
        return MissionValidationResult(
            isValid = false,
            isFallbackApplied = true,
            isMalformed = true,
            statusLabel = "Malformed (Fallback Applied)",
            failureReason = "No valid JSON object parsed from raw generation",
            finalMission = getDeterministicFallbackMission()
        )
    }

    val missingOrInvalid = mutableListOf<String>()

    // 1. Strict non-empty string title
    val rawTitle = rawJson.opt("title")
    val hasValidTitle = rawTitle is String && rawTitle.trim().isNotEmpty()
    if (!hasValidTitle) {
        missingOrInvalid.add("title (must be non-empty string)")
    }

    // 2. Strict actual positive integer durationMinutes
    val rawDuration = rawJson.opt("durationMinutes")
    val hasValidDuration = when (rawDuration) {
        is Int -> rawDuration > 0
        is Long -> rawDuration > 0L && rawDuration <= Int.MAX_VALUE
        else -> false
    }
    if (!hasValidDuration) {
        missingOrInvalid.add("durationMinutes (must be positive integer)")
    }

    // 3. Strict non-empty missions array of non-empty strings (reject singular "mission")
    val rawMissions = rawJson.opt("missions")
    var hasValidMissions = false
    if (rawMissions is JSONArray && rawMissions.length() > 0) {
        var allStringsValid = true
        for (i in 0 until rawMissions.length()) {
            val item = rawMissions.opt(i)
            if (item !is String || item.trim().isEmpty()) {
                allStringsValid = false
                break
            }
        }
        hasValidMissions = allStringsValid
    }
    if (!hasValidMissions) {
        missingOrInvalid.add("missions (must be non-empty array of non-empty strings)")
    }

    if (hasValidTitle && hasValidDuration && hasValidMissions) {
        return MissionValidationResult(
            isValid = true,
            isFallbackApplied = false,
            isMalformed = false,
            statusLabel = "Valid",
            finalMission = rawJson
        )
    }

    return MissionValidationResult(
        isValid = false,
        isFallbackApplied = true,
        isMalformed = false,
        statusLabel = "Schema Incomplete (Fallback Applied)",
        failureReason = "Invalid fields: ${missingOrInvalid.joinToString(", ")}",
        finalMission = getDeterministicFallbackMission()
    )
}

/**
 * Extracts and parses a JSONObject even if surrounded by markdown code fences,
 * conversational preamble, or trailing commentary.
 */
internal fun extractJson(text: String): JSONObject? {
    var cleaned = text.trim()
    if (cleaned.startsWith("```json")) {
        cleaned = cleaned.removePrefix("```json").trim()
    } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.removePrefix("```").trim()
    }
    if (cleaned.endsWith("```")) {
        cleaned = cleaned.removeSuffix("```").trim()
    }

    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start != -1 && end != -1 && end >= start) {
        val jsonStr = cleaned.substring(start, end + 1)
        return try {
            JSONObject(jsonStr)
        } catch (_: Exception) {
            null
        }
    }
    return null
}

@Composable
fun TrailQuestApp(llmProvider: LlmProvider, modelInstaller: ModelInstaller) {
    var isReady by remember { mutableStateOf(false) }
    var modelPath by remember { mutableStateOf("") }
    val progress by modelInstaller.progress.collectAsState()
    val status by modelInstaller.status.collectAsState()

    LaunchedEffect(Unit) {
        try {
            modelPath = modelInstaller.installModel("gemma-3-270m-it-q4_k_m.gguf")
            llmProvider.loadModel(modelPath)
            isReady = true
        } catch (e: Exception) {
            Log.e("TrailQuest", "Failed to initialize model", e)
        }
    }

    if (!isReady) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = status, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${(progress * 100).toInt()}%")
        }
    } else {
        DemoScreen(llmProvider)
    }
}

@Composable
fun DemoScreen(llmProvider: LlmProvider) {
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var benchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val recommendedThreads = remember { llmProvider.getRecommendedThreads().coerceIn(1, 8) }
    var selectedThreads by remember { mutableIntStateOf(4) }

    fun runIterations(totalIterations: Int) {
        if (isRunning) return
        isRunning = true
        output = "Running ${if (totalIterations == 1) "single test" else "benchmark (20 iterations)"} with $selectedThreads threads...\n\n"

        benchJob = scope.launch(Dispatchers.IO) {
            llmProvider.setThreads(selectedThreads)
            var validCount = 0
            var malformedCount = 0
            var schemaFallbackCount = 0
            var completedIters = 0

            var totalTokens = 0L
            var totalGenMs = 0L
            var totalFirstTokenMs = 0L

            val wallStart = System.currentTimeMillis()

            try {
                for (i in 1..totalIterations) {
                    if (!coroutineContext.isActive) break

                    llmProvider.resetContext()

                    var currentText = ""
                    var tokenCount = 0
                    var firstTokenMs = -1L
                    var validatedResult: MissionValidationResult? = null

                    val iterStart = System.currentTimeMillis()
                    try {
                        llmProvider.generate(BENCH_PROMPT).collect { token ->
                            if (firstTokenMs < 0) {
                                firstTokenMs = System.currentTimeMillis() - iterStart
                            }
                            currentText += token
                            tokenCount++

                            // Early-stop check: if closing brace emitted and full schema is valid
                            if (token.contains("}")) {
                                val candidate = extractJson(currentText)
                                if (candidate != null) {
                                    val check = validateAndResolveMission(candidate)
                                    if (check.isValid) {
                                        validatedResult = check
                                        throw JsonCompleteException()
                                    }
                                }
                            }
                        }
                    } catch (_: JsonCompleteException) {
                        // Completed valid JSON early
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }

                    if (validatedResult == null) {
                        val parsed = extractJson(currentText)
                        validatedResult = validateAndResolveMission(parsed)
                    }

                    val res = validatedResult!!
                    if (res.isValid) {
                        validCount++
                    } else if (res.isMalformed) {
                        malformedCount++
                    } else {
                        schemaFallbackCount++
                    }

                    val iterMs = System.currentTimeMillis() - iterStart
                    val tokPerSec = if (iterMs > 0) tokenCount * 1000.0 / iterMs else 0.0
                    val ftMs = if (firstTokenMs >= 0) firstTokenMs else -1L

                    totalTokens += tokenCount
                    totalGenMs += iterMs
                    if (ftMs >= 0) totalFirstTokenMs += ftMs
                    completedIters++

                    Log.d("TrailQuest/Raw", "Iter $i raw output:\n$currentText")
                    Log.d("TrailQuest/Schema", "Iter $i status: ${res.statusLabel} | final json: ${res.finalMission}")

                    val statusSymbol = if (res.isValid) "✓ (Valid)" else if (res.isMalformed) "✗ (Malformed -> Fallback)" else "~ (Schema -> Fallback)"
                    val missionTitle = res.finalMission.optString("title", "No Title")
                    val iterLine = "Iter $i: ${iterMs}ms | tokens=$tokenCount | t/s=${"%.1f".format(tokPerSec)} | TTFT=${ftMs}ms | $statusSymbol [\"$missionTitle\"]\n"
                    Log.i("TrailQuest/Bench", iterLine.trim())

                    if (totalIterations == 1) {
                        val singleDetail = buildString {
                            append(iterLine)
                            append("\n[Raw Response]:\n$currentText\n\n")
                            append("[Parsed / Resolved Mission JSON]:\n${res.finalMission.toString(2)}\n")
                        }
                        withContext(Dispatchers.Main) {
                            output += singleDetail
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            output += iterLine
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i("TrailQuest/Bench", "Benchmark cancelled by user")
            }

            val wallMs = System.currentTimeMillis() - wallStart
            val avgTps = if (totalGenMs > 0) totalTokens * 1000.0 / totalGenMs else 0.0
            val avgFirstMs = if (completedIters > 0) totalFirstTokenMs / completedIters else 0L
            val avgTokens = if (completedIters > 0) totalTokens.toDouble() / completedIters else 0.0
            val avgGenMs = if (completedIters > 0) totalGenMs / completedIters else 0L
            val totalFallbacks = malformedCount + schemaFallbackCount

            val summary = buildString {
                append("\n========================================\n")
                append("--- Benchmark Results ($completedIters / $totalIterations completed, ${selectedThreads}T) ---\n")
                append("========================================\n")
                append("Valid missions (model)   : $validCount / $totalIterations\n")
                append("Malformed output (JSON)  : $malformedCount / $totalIterations\n")
                append("Schema incomplete (JSON) : $schemaFallbackCount / $totalIterations\n")
                append("Deterministic fallbacks  : $totalFallbacks / $totalIterations\n")
                append("Avg generation time      : ${avgGenMs} ms\n")
                append("Avg first-token          : ${avgFirstMs} ms\n")
                append("Avg throughput           : ${"%.2f".format(avgTps)} tok/s\n")
                append("Avg token count          : ${"%.1f".format(avgTokens)} tokens\n")
                append("Total tokens             : $totalTokens\n")
                append("Total wall time          : ${wallMs} ms\n")
                append("========================================\n")
            }
            Log.i("TrailQuest/Bench", summary)

            withContext(Dispatchers.Main) {
                output += summary
                isRunning = false
                benchJob = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("TRAILQUEST AI", style = MaterialTheme.typography.headlineMedium)
        Text("Model: Gemma 3 270M IT Q4_K_M")
        Text("Runtime: llama.cpp (arm64-v8a)")
        Text("Inference: In-Process JNI")
        Text("Network: Offline Verified (NO INTERNET)")

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Inference Threads (active: $selectedThreads, recommended: $recommendedThreads):",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(2, 4, 6, 8).forEach { t ->
                Button(
                    onClick = {
                        selectedThreads = t
                        llmProvider.setThreads(t)
                    },
                    colors = if (selectedThreads == t) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("${t}T")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { runIterations(1) },
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Verify (1x)")
            }

            Button(
                onClick = { runIterations(20) },
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Benchmark (20x)")
            }

            if (isRunning) {
                Button(
                    onClick = { benchJob?.cancel() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(0.6f)
                ) {
                    Text("Stop")
                }
            } else if (output.isNotEmpty()) {
                OutlinedButton(
                    onClick = { output = "" },
                    modifier = Modifier.weight(0.6f)
                ) {
                    Text("Clear")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(output)
    }
}
