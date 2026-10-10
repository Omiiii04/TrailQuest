package com.example.trailquest

import androidx.navigation3.runtime.NavKey
import com.example.trailquest.ui.setup.ExpeditionActivity
import com.example.trailquest.ui.setup.ExpeditionConfig
import com.example.trailquest.ui.setup.ExpeditionDifficulty
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object ExpeditionSetupKey : NavKey

@Serializable
data class ExpeditionReviewKey(
    val config: ExpeditionConfig,
    val autoGenerate: Boolean = false
) : NavKey

@Serializable
data class MissionPlan(
    val title: String,
    val durationMinutes: Int,
    val tasks: List<String>,
    val activity: ExpeditionActivity,
    val difficulty: ExpeditionDifficulty,
    val isAiGenerated: Boolean = true
)

@Serializable
data class MissionKey(
    val mission: MissionPlan,
    val config: ExpeditionConfig
) : NavKey

@Serializable
data object DeveloperBenchmarkKey : NavKey
