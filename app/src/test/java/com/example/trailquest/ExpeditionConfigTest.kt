package com.example.trailquest

import com.example.trailquest.ui.setup.ExpeditionActivity
import com.example.trailquest.ui.setup.ExpeditionConfig
import com.example.trailquest.ui.setup.ExpeditionDifficulty
import com.example.trailquest.ui.setup.ExpeditionDraftState
import com.example.trailquest.ui.setup.SUPPORTED_DURATIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpeditionConfigTest {

    @Test
    fun defaultConfiguration_hasExpectedValues() {
        val config = ExpeditionConfig()
        assertEquals(ExpeditionActivity.WALK, config.activity)
        assertEquals(30, config.durationMinutes)
        assertEquals(ExpeditionDifficulty.EASY, config.difficulty)
    }

    @Test
    fun supportedDurations_areStrictlyEnforced() {
        assertEquals(listOf(15, 30, 60), SUPPORTED_DURATIONS)

        // All supported durations must construct successfully
        SUPPORTED_DURATIONS.forEach { duration ->
            val config = ExpeditionConfig(
                activity = ExpeditionActivity.HIKE,
                durationMinutes = duration,
                difficulty = ExpeditionDifficulty.MODERATE
            )
            assertEquals(duration, config.durationMinutes)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedDuration_zero_throwsException() {
        ExpeditionConfig(durationMinutes = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedDuration_fortyFive_throwsException() {
        ExpeditionConfig(durationMinutes = 45)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedDuration_negative_throwsException() {
        ExpeditionConfig(durationMinutes = -15)
    }

    @Test
    fun expeditionDraftState_tracksAndBuildsImmutableConfig() {
        val draft = ExpeditionDraftState()

        assertEquals(ExpeditionActivity.WALK, draft.selectedActivity)
        assertEquals(30, draft.selectedDurationMinutes)
        assertEquals(ExpeditionDifficulty.EASY, draft.selectedDifficulty)
        assertTrue(draft.isValid)

        // Update selections
        draft.selectedActivity = ExpeditionActivity.RUN
        draft.selectedDurationMinutes = 60
        draft.selectedDifficulty = ExpeditionDifficulty.CHALLENGING

        assertEquals(ExpeditionActivity.RUN, draft.selectedActivity)
        assertEquals(60, draft.selectedDurationMinutes)
        assertEquals(ExpeditionDifficulty.CHALLENGING, draft.selectedDifficulty)

        val builtConfig = draft.buildConfig()
        assertEquals(ExpeditionActivity.RUN, builtConfig.activity)
        assertEquals(60, builtConfig.durationMinutes)
        assertEquals(ExpeditionDifficulty.CHALLENGING, builtConfig.difficulty)

        // Test editing/reloading into draft
        val newConfig = ExpeditionConfig(
            activity = ExpeditionActivity.NATURE_EXPLORE,
            durationMinutes = 15,
            difficulty = ExpeditionDifficulty.MODERATE
        )
        draft.applyConfig(newConfig)
        assertEquals(ExpeditionActivity.NATURE_EXPLORE, draft.selectedActivity)
        assertEquals(15, draft.selectedDurationMinutes)
        assertEquals(ExpeditionDifficulty.MODERATE, draft.selectedDifficulty)
    }

    @Test
    fun expeditionDraftState_invalidDurationFlagsInvalid() {
        val draft = ExpeditionDraftState()
        draft.selectedDurationMinutes = 45
        assertFalse(draft.isValid)
    }
}
