package com.example.trailquest

import androidx.navigation3.runtime.NavKey
import com.example.trailquest.ui.setup.ExpeditionConfig
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object ExpeditionSetupKey : NavKey

@Serializable
data class ExpeditionReviewKey(val config: ExpeditionConfig) : NavKey

@Serializable
data object DeveloperBenchmarkKey : NavKey
