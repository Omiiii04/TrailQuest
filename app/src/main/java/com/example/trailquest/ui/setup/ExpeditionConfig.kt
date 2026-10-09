package com.example.trailquest.ui.setup

import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.trailquest.R
import kotlinx.serialization.Serializable

val SUPPORTED_DURATIONS: List<Int> = listOf(15, 30, 60)

@Serializable
enum class ExpeditionActivity(
    val title: String,
    val description: String,
    @get:DrawableRes val iconResId: Int
) {
    WALK(
        title = "Walk",
        description = "Gentle exploration on paved paths or sidewalks",
        iconResId = R.drawable.ic_walk
    ),
    HIKE(
        title = "Hike",
        description = "Trails, uneven terrain, and natural elevation",
        iconResId = R.drawable.ic_hike
    ),
    RUN(
        title = "Run",
        description = "Cardio-focused routes with energetic pacing",
        iconResId = R.drawable.ic_run
    ),
    NATURE_EXPLORE(
        title = "Nature Explore",
        description = "Mindful discovery observing trees, plants, and terrain",
        iconResId = R.drawable.ic_nature
    )
}

@Serializable
enum class ExpeditionDifficulty(
    val title: String,
    val description: String
) {
    EASY(
        title = "Easy",
        description = "Relaxed pace with minimal incline"
    ),
    MODERATE(
        title = "Moderate",
        description = "Steady pace with varied terrain"
    ),
    CHALLENGING(
        title = "Challenging",
        description = "Brisk pace and demanding elevation"
    )
}

@Serializable
data class ExpeditionConfig(
    val activity: ExpeditionActivity = ExpeditionActivity.WALK,
    val durationMinutes: Int = 30,
    val difficulty: ExpeditionDifficulty = ExpeditionDifficulty.EASY
) {
    init {
        require(durationMinutes in SUPPORTED_DURATIONS) {
            "Invalid duration $durationMinutes. Supported durations are: $SUPPORTED_DURATIONS"
        }
    }
}

class ExpeditionDraftState(
    initialActivity: ExpeditionActivity = ExpeditionActivity.WALK,
    initialDuration: Int = 30,
    initialDifficulty: ExpeditionDifficulty = ExpeditionDifficulty.EASY
) {
    var selectedActivity: ExpeditionActivity by mutableStateOf(initialActivity)
    var selectedDurationMinutes: Int by mutableIntStateOf(initialDuration)
    var selectedDifficulty: ExpeditionDifficulty by mutableStateOf(initialDifficulty)

    val isValid: Boolean
        get() = selectedDurationMinutes in SUPPORTED_DURATIONS

    fun buildConfig(): ExpeditionConfig {
        require(isValid) { "Cannot build ExpeditionConfig with invalid duration $selectedDurationMinutes" }
        return ExpeditionConfig(
            activity = selectedActivity,
            durationMinutes = selectedDurationMinutes,
            difficulty = selectedDifficulty
        )
    }

    fun applyConfig(config: ExpeditionConfig) {
        selectedActivity = config.activity
        selectedDurationMinutes = config.durationMinutes
        selectedDifficulty = config.difficulty
    }
}
