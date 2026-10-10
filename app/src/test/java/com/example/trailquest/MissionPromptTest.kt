package com.example.trailquest

import com.example.trailquest.ui.setup.ExpeditionActivity
import com.example.trailquest.ui.setup.ExpeditionConfig
import com.example.trailquest.ui.setup.ExpeditionDifficulty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionPromptTest {

    @Test
    fun eachSupportedActivity_isExplicitlyRepresentedInPrompt() {
        for (activity in ExpeditionActivity.entries) {
            val config = ExpeditionConfig(activity = activity, durationMinutes = 30, difficulty = ExpeditionDifficulty.MODERATE)
            val prompt = buildMissionPrompt(config)

            assertTrue(
                "Prompt must contain activity title '${activity.title}'",
                prompt.contains(activity.title)
            )
        }
    }

    @Test
    fun durationAndDifficulty_arePassedWithoutSubstitution() {
        val testCases = listOf(
            ExpeditionConfig(ExpeditionActivity.WALK, 15, ExpeditionDifficulty.EASY),
            ExpeditionConfig(ExpeditionActivity.HIKE, 30, ExpeditionDifficulty.MODERATE),
            ExpeditionConfig(ExpeditionActivity.RUN, 60, ExpeditionDifficulty.CHALLENGING),
            ExpeditionConfig(ExpeditionActivity.NATURE_EXPLORE, 15, ExpeditionDifficulty.MODERATE)
        )

        for (config in testCases) {
            val prompt = buildMissionPrompt(config)

            assertTrue("Prompt must contain duration ${config.durationMinutes}", prompt.contains("${config.durationMinutes}"))
            assertTrue("Prompt must contain difficulty '${config.difficulty.title}'", prompt.contains(config.difficulty.title))
            assertTrue("Prompt must contain activity '${config.activity.title}'", prompt.contains(config.activity.title))
            assertTrue("Prompt must contain five-field JSON schema", prompt.contains("\"activity\"") && prompt.contains("\"difficulty\""))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidConfiguration_isRejectedBeforeInference() {
        ExpeditionConfig(durationMinutes = 45)
    }
}
