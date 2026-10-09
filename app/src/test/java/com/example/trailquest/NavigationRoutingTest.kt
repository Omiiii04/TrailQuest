package com.example.trailquest

import com.example.trailquest.ui.setup.ExpeditionActivity
import com.example.trailquest.ui.setup.ExpeditionConfig
import com.example.trailquest.ui.setup.ExpeditionDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavigationRoutingTest {

    @Test
    fun routeKeys_instantiateCorrectly() {
        val home = HomeKey
        val setup = ExpeditionSetupKey
        val benchmark = DeveloperBenchmarkKey

        assertEquals(HomeKey, home)
        assertEquals(ExpeditionSetupKey, setup)
        assertEquals(DeveloperBenchmarkKey, benchmark)
    }

    @Test
    fun expeditionReviewKey_holdsAndComparesConfigCorrectly() {
        val config1 = ExpeditionConfig(
            activity = ExpeditionActivity.HIKE,
            durationMinutes = 60,
            difficulty = ExpeditionDifficulty.MODERATE
        )
        val config2 = ExpeditionConfig(
            activity = ExpeditionActivity.HIKE,
            durationMinutes = 60,
            difficulty = ExpeditionDifficulty.MODERATE
        )
        val config3 = ExpeditionConfig(
            activity = ExpeditionActivity.WALK,
            durationMinutes = 30,
            difficulty = ExpeditionDifficulty.EASY
        )

        val key1 = ExpeditionReviewKey(config1)
        val key2 = ExpeditionReviewKey(config2)
        val key3 = ExpeditionReviewKey(config3)

        assertEquals(key1, key2)
        assertNotEquals(key1, key3)
        assertEquals(60, key1.config.durationMinutes)
        assertEquals(ExpeditionActivity.HIKE, key1.config.activity)
    }
}
