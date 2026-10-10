package com.example.trailquest.ui.benchmark

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trailquest.BENCH_PROMPT
import com.example.trailquest.JsonCompleteException
import com.example.trailquest.LlmProvider
import com.example.trailquest.MissionValidationResult
import com.example.trailquest.ModelInstaller
import com.example.trailquest.R
import com.example.trailquest.extractJson
import com.example.trailquest.validateAndResolveMission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Composable
fun BenchmarkScreen(
    llmProviderFactory: () -> LlmProvider,
    modelInstaller: ModelInstaller,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isReady by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }
    var llmProvider by remember { mutableStateOf<LlmProvider?>(null) }
    val progress by modelInstaller.progress.collectAsState()
    val status by modelInstaller.status.collectAsState()

    LaunchedEffect(Unit) {
        try {
            val provider = llmProviderFactory()
            llmProvider = provider
            val modelPath = modelInstaller.installModel("gemma-3-270m-it-q4_k_m.gguf")
            provider.loadModel(modelPath)
            isReady = true
        } catch (e: Exception) {
            Log.e("TrailQuest", "Failed to initialize model in BenchmarkScreen", e)
            initError = e.message ?: "Failed to initialize model"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Return to Home",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Developer Tools & Benchmark",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (initError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Initialization Error",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = initError!!)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    initError = null
                    isReady = false
                }) {
                    Text("Retry")
                }
            }
        } else if (!isReady) {
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
            llmProvider?.let { provider ->
                DemoScreen(provider, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DemoScreen(
    llmProvider: LlmProvider,
    modifier: Modifier = Modifier
) {
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

                    var currentText = ""
                    var tokenCount = 0
                    var firstTokenMs = -1L
                    var validatedResult: MissionValidationResult? = null

                    val iterStart = System.currentTimeMillis()
                    try {
                        currentText = llmProvider.runTransaction(
                            prompt = BENCH_PROMPT,
                            threads = selectedThreads,
                            onToken = { token, accumulated ->
                                if (firstTokenMs < 0) {
                                    firstTokenMs = System.currentTimeMillis() - iterStart
                                }
                                tokenCount++

                                // Early-stop check: if closing brace emitted and full schema is valid
                                if (token.contains("}")) {
                                    val candidate = extractJson(accumulated)
                                    if (candidate != null) {
                                        val check = validateAndResolveMission(candidate)
                                        if (check.isValid) {
                                            validatedResult = check
                                            return@runTransaction true // Stop early
                                        }
                                    }
                                }
                                false
                            }
                        )
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }

                    val res = validatedResult ?: run {
                        val parsed = extractJson(currentText)
                        validateAndResolveMission(parsed)
                    }
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
        modifier = modifier
            .fillMaxWidth()
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
