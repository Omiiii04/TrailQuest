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
    "You are a helpful AI guide for an outdoor activity app. Provide exactly one simple outdoor mission formatted as a JSON object with 'title', 'durationMinutes', and 'missions' fields. Return ONLY valid JSON.\n\nCreate one simple 30-minute outdoor mission for a person going for a walk."

private class JsonCompleteException : Exception()

/**
 * Extracts and parses a JSONObject even if surrounded by markdown code fences,
 * conversational preamble, or trailing commentary.
 */
private fun extractJson(text: String): JSONObject? {
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
            modelPath = modelInstaller.installModel("gemma-3-270m-q4_k_m.gguf")
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
    var selectedThreads by remember { mutableIntStateOf(recommendedThreads) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("TRAILQUEST AI", style = MaterialTheme.typography.headlineMedium)
        Text("Model: Gemma 3 270M Q4_K_M")
        Text("Runtime: llama.cpp (arm64-v8a)")
        Text("Inference: LOCAL")
        Text("Network: DISABLED")

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
                onClick = {
                    if (isRunning) return@Button
                    isRunning = true
                    output = "Running benchmark (20 iterations) with $selectedThreads threads...\n"

                    benchJob = scope.launch(Dispatchers.IO) {
                        llmProvider.setThreads(selectedThreads)
                        val totalIterations = 20
                        var successCount = 0
                        var failCount = 0
                        var fallbackCount = 0
                        var completedIters = 0

                        // Aggregate metrics
                        var totalTokens = 0L
                        var totalGenMs = 0L
                        var totalFirstTokenMs = 0L

                        val wallStart = System.currentTimeMillis()

                        try {
                            for (i in 1..totalIterations) {
                                if (!coroutineContext.isActive) break

                                // Reset context & KV cache before every iteration
                                llmProvider.resetContext()

                                var currentText = ""
                                var tokenCount = 0
                                var firstTokenMs = -1L
                                var jsonOk = false
                                var parsedJson: JSONObject? = null

                                val iterStart = System.currentTimeMillis()
                                try {
                                    llmProvider.generate(BENCH_PROMPT).collect { token ->
                                        if (firstTokenMs < 0) {
                                            firstTokenMs = System.currentTimeMillis() - iterStart
                                        }
                                        currentText += token
                                        tokenCount++

                                        // Early stop: if a closing brace was emitted and valid mission JSON is complete, finish early
                                        if (token.contains("}")) {
                                            val candidate = extractJson(currentText)
                                            if (candidate != null && candidate.has("title") && (candidate.has("missions") || candidate.has("mission"))) {
                                                parsedJson = candidate
                                                throw JsonCompleteException()
                                            }
                                        }
                                    }
                                } catch (_: JsonCompleteException) {
                                    // Successfully completed valid JSON early
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                }

                                if (parsedJson == null) {
                                    parsedJson = extractJson(currentText)
                                }

                                Log.d("TrailQuest/Raw", "Iter $i raw: $currentText")

                                if (parsedJson != null) {
                                    val hasTitle = parsedJson!!.has("title")
                                    val hasMissions = parsedJson!!.has("missions") || parsedJson!!.has("mission")
                                    if (hasTitle && hasMissions) {
                                        successCount++
                                        jsonOk = true
                                    } else {
                                        fallbackCount++
                                    }
                                } else {
                                    failCount++
                                }

                                val iterMs = System.currentTimeMillis() - iterStart
                                val tokPerSec = if (iterMs > 0) tokenCount * 1000.0 / iterMs else 0.0
                                val ftMs = if (firstTokenMs >= 0) firstTokenMs else -1L

                                totalTokens += tokenCount
                                totalGenMs += iterMs
                                if (ftMs >= 0) totalFirstTokenMs += ftMs
                                completedIters++

                                val jsonStatus = if (jsonOk) "✓" else if (parsedJson != null) "~" else "✗"
                                val titleSnippet = parsedJson?.optString("title")?.take(30)
                                    ?: currentText.take(30).replace("\n", " ")
                                val iterLine = "Iter $i: ${iterMs}ms | tokens=$tokenCount | t/s=${"%.1f".format(tokPerSec)} | first=${ftMs}ms $jsonStatus [\"$titleSnippet\"]\n"
                                Log.d("TrailQuest/Bench", iterLine.trim())

                                withContext(Dispatchers.Main) {
                                    output += iterLine
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            Log.i("TrailQuest/Bench", "Benchmark cancelled by user")
                        }

                        val wallMs = System.currentTimeMillis() - wallStart
                        val avgTps = if (totalGenMs > 0) totalTokens * 1000.0 / totalGenMs else 0.0
                        val avgFirstMs = if (completedIters > 0) totalFirstTokenMs / completedIters else 0L

                        val summary = buildString {
                            append("\n--- Summary ($completedIters / $totalIterations completed, ${selectedThreads}T) ---\n")
                            append("Total wall time : ${wallMs}ms\n")
                            if (completedIters > 0) {
                                append("Avg gen time    : ${totalGenMs / completedIters}ms/iter\n")
                                append("Avg first-token : ${avgFirstMs}ms\n")
                            }
                            append("Total tokens    : $totalTokens\n")
                            append("Avg t/s         : ${"%.2f".format(avgTps)}\n")
                            append("Success ✓       : $successCount | Partial: $fallbackCount | Fail: $failCount\n")
                        }
                        Log.i("TrailQuest/Bench", summary)

                        withContext(Dispatchers.Main) {
                            output += summary
                            isRunning = false
                            benchJob = null
                        }
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isRunning) "Running..." else "Run Benchmark (20x)")
            }

            if (isRunning) {
                Button(
                    onClick = {
                        benchJob?.cancel()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(0.5f)
                ) {
                    Text("Stop")
                }
            } else if (output.isNotEmpty()) {
                OutlinedButton(
                    onClick = { output = "" },
                    modifier = Modifier.weight(0.5f)
                ) {
                    Text("Clear")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(output)
    }
}
