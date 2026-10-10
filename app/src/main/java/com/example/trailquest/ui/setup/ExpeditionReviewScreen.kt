package com.example.trailquest.ui.setup

import android.util.Log
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trailquest.LlmProvider
import com.example.trailquest.MissionPlan
import com.example.trailquest.MissionValidationResult
import com.example.trailquest.ModelInstaller
import com.example.trailquest.R
import com.example.trailquest.buildMissionPrompt
import com.example.trailquest.extractJson
import com.example.trailquest.toMissionPlan
import com.example.trailquest.validateAndResolveMission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GenerationStage(val label: String) {
    PREPARING_MODEL("Preparing offline model..."),
    LOADING_MODEL("Loading on-device model..."),
    GENERATING_MISSION("Generating mission with local AI..."),
    VALIDATING_MISSION("Validating mission...")
}

sealed class GenerationState {
    data object Idle : GenerationState()
    data class Loading(val stage: GenerationStage, val progress: Float = 0f) : GenerationState()
    data class Error(val message: String) : GenerationState()
}

@Composable
fun ExpeditionReviewScreen(
    config: ExpeditionConfig,
    llmProviderFactory: () -> LlmProvider,
    modelInstaller: ModelInstaller,
    onMissionGenerated: (MissionPlan) -> Unit,
    onEdit: () -> Unit,
    onHome: () -> Unit,
    autoGenerate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<GenerationState>(GenerationState.Idle) }
    var isGenerating by remember { mutableStateOf(false) }
    val installerProgress by modelInstaller.progress.collectAsState()

    fun startGeneration() {
        if (isGenerating) return
        isGenerating = true
        state = GenerationState.Loading(GenerationStage.PREPARING_MODEL, installerProgress)

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Preparing offline model
                val modelPath = modelInstaller.installModel("gemma-3-270m-it-q4_k_m.gguf")

                // 2. Loading on-device model
                withContext(Dispatchers.Main) {
                    state = GenerationState.Loading(GenerationStage.LOADING_MODEL)
                }
                val provider = llmProviderFactory()
                provider.loadModel(modelPath)

                // 3. Generating mission with atomic transaction
                withContext(Dispatchers.Main) {
                    state = GenerationState.Loading(GenerationStage.GENERATING_MISSION)
                }
                val prompt = buildMissionPrompt(config)
                var earlyValidatedResult: MissionValidationResult? = null

                val rawResponse = provider.runTransaction(
                    prompt = prompt,
                    threads = 4,
                    onToken = { token, accumulated ->
                        if (token.contains("}")) {
                            val candidate = extractJson(accumulated)
                            if (candidate != null) {
                                val check = validateAndResolveMission(candidate, config)
                                if (check.isValid) {
                                    earlyValidatedResult = check
                                    return@runTransaction true // Stop early on valid JSON
                                }
                            }
                        }
                        false
                    }
                )

                // 4. Validating mission
                withContext(Dispatchers.Main) {
                    state = GenerationState.Loading(GenerationStage.VALIDATING_MISSION)
                }
                val finalResult = earlyValidatedResult ?: run {
                    val parsed = extractJson(rawResponse)
                    validateAndResolveMission(parsed, config)
                }
                Log.i("TrailQuest", "LLM raw response: $rawResponse")
                Log.i("TrailQuest", "Mission validated: isValid=${finalResult.isValid}, isFallbackApplied=${finalResult.isFallbackApplied}, reason=${finalResult.failureReason}")

                val missionPlan = finalResult.finalMission.toMissionPlan(
                    config = config,
                    isAiGenerated = finalResult.isValid && !finalResult.isFallbackApplied
                )

                withContext(Dispatchers.Main) {
                    isGenerating = false
                    state = GenerationState.Idle
                    onMissionGenerated(missionPlan)
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                    state = GenerationState.Idle
                }
                throw e
            } catch (e: Exception) {
                Log.e("TrailQuest/Expedition", "Generation failed", e)
                withContext(Dispatchers.Main) {
                    isGenerating = false
                    state = GenerationState.Error(e.message ?: "Failed to generate mission")
                }
            }
        }
    }

    // One-shot auto-generation if requested (e.g. from Regenerate action on MissionScreen)
    var hasAutoGenerated by rememberSaveable(config) { mutableStateOf(false) }
    LaunchedEffect(autoGenerate) {
        if (autoGenerate && !hasAutoGenerated) {
            hasAutoGenerated = true
            startGeneration()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp),
                enabled = !isGenerating
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Edit expedition setup",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Expedition Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Scrollable summary
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Selected Setup Card
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Chosen Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 1. Activity Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = config.activity.iconResId),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Activity",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = config.activity.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 2. Duration Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                text = "${config.durationMinutes}m",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Target Duration",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${config.durationMinutes} minutes",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 3. Difficulty Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                text = config.difficulty.title.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Difficulty",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = config.difficulty.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating
                    ) {
                        Text("Edit expedition")
                    }
                }
            }

            // Generation State Card
            when (val current = state) {
                is GenerationState.Loading -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = current.stage.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (current.stage == GenerationStage.PREPARING_MODEL && installerProgress > 0f) {
                                LinearProgressIndicator(
                                    progress = { installerProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            Text(
                                text = "Running 100% locally on your device with Gemma 3. Please keep the app open.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is GenerationState.Error -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Generation Failed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { startGeneration() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry")
                                }
                                OutlinedButton(
                                    onClick = onEdit,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Edit Setup")
                                }
                            }
                        }
                    }
                }
                is GenerationState.Idle -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "ON-DEVICE AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "Ready to Generate",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Gemma 3 will generate an outdoor mission tailored to your ${config.durationMinutes}-minute ${config.difficulty.title.lowercase()} ${config.activity.title.lowercase()}. Private, fast, and completely offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Bottom Primary Action
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Button(
                onClick = { startGeneration() },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isGenerating) "Generating mission..." else "Generate my mission",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
