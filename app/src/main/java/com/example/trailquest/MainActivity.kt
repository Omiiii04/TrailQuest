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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.system.measureTimeMillis

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

@Composable
fun TrailQuestApp(llmProvider: LlmProvider, modelInstaller: ModelInstaller) {
    var modelReady by remember { mutableStateOf(false) }
    var modelPath by remember { mutableStateOf("") }
    val progress by modelInstaller.progress.collectAsState()
    val status by modelInstaller.status.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            modelPath = modelInstaller.installModel("gemma-3-270m-q4_k_m.gguf")
            llmProvider.loadModel(modelPath)
            llmProvider.setSystemPrompt("You are a helpful AI guide for an outdoor activity app. Provide exactly one simple outdoor mission formatted as a JSON object with 'title', 'durationMinutes', and 'missions' fields. Return ONLY valid JSON.")
            modelReady = true
        } catch (e: Exception) {
            Log.e("TrailQuest", "Failed to load model", e)
        }
    }

    if (!modelReady) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Preparing TrailQuest AI", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = status)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "\${(progress * 100).toInt()}%")
        }
    } else {
        DemoScreen(llmProvider)
    }
}

@Composable
fun DemoScreen(llmProvider: LlmProvider) {
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("TRAILQUEST AI", style = MaterialTheme.typography.headlineMedium)
        Text("Model: Gemma 3 270M Q4_K_M")
        Text("Runtime: llama.cpp")
        Text("Inference: LOCAL")
        Text("Network: DISABLED")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (isRunning) return@Button
                isRunning = true
                output = "Running benchmark (20 iterations)...\\n"
                
                scope.launch(Dispatchers.IO) {
                    var successCount = 0
                    var failCount = 0
                    var fallbackCount = 0
                    val totalGenTime = measureTimeMillis {
                        for (i in 1..20) {
                            var currentText = ""
                            val iterTime = measureTimeMillis {
                                try {
                                    llmProvider.generate("Create one simple 30-minute outdoor mission for a person going for a walk. Return ONLY valid JSON.").collect { token ->
                                        currentText += token
                                    }
                                    
                                    // Parse JSON
                                    try {
                                        val json = JSONObject(currentText)
                                        if (json.has("title") && json.has("missions")) {
                                            successCount++
                                        } else {
                                            fallbackCount++
                                        }
                                    } catch (e: Exception) {
                                        failCount++
                                    }
                                } catch (e: Exception) {
                                    failCount++
                                }
                            }
                            val safeOutput = currentText.take(100)
                            withContext(Dispatchers.Main) {
                                output += "Iter \$i: \$iterTime ms - Success: \${successCount}, Fail: \${failCount}, Fallback: \${fallbackCount}\\nSample: \$safeOutput\\n"
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        output += "\\nTotal Time: \$totalGenTime ms\\n"
                        isRunning = false
                    }
                }
            },
            enabled = !isRunning
        ) {
            Text(if (isRunning) "Running..." else "Run Benchmark (20x)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(output)
    }
}
