package com.example.trailquest

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionValidationTest {

    @Test
    fun validMissionJson_passesValidation() {
        val json = JSONObject().apply {
            put("title", "Forest Ridge Trail Hike")
            put("durationMinutes", 45)
            put("missions", JSONArray().apply {
                put("Hike 2 kilometers along the pine ridge.")
                put("Locate and photograph two distinct birds or trees.")
            })
        }

        val result = validateAndResolveMission(json)

        assertTrue(result.isValid)
        assertFalse(result.isFallbackApplied)
        assertFalse(result.isMalformed)
        assertEquals("Valid", result.statusLabel)
        assertNull(result.failureReason)
        assertEquals("Forest Ridge Trail Hike", result.finalMission.getString("title"))
        assertEquals(45, result.finalMission.getInt("durationMinutes"))
        assertEquals(2, result.finalMission.getJSONArray("missions").length())
    }

    @Test
    fun wrongFieldTypes_durationMinutesNotPositiveInt_triggersFallback() {
        // String durationMinutes is not an actual integer
        val jsonStringDuration = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", "30")
            put("missions", JSONArray().apply { put("Walk 1 mile") })
        }
        val resString = validateAndResolveMission(jsonStringDuration)
        assertFalse("String duration must be rejected", resString.isValid)
        assertTrue(resString.isFallbackApplied)
        assertFalse(resString.isMalformed)
        assertEquals("Schema Incomplete (Fallback Applied)", resString.statusLabel)
        assertEquals("Neighborhood Nature Walk", resString.finalMission.getString("title"))

        // Float/Double durationMinutes
        val jsonDoubleDuration = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", 30.5)
            put("missions", JSONArray().apply { put("Walk 1 mile") })
        }
        val resDouble = validateAndResolveMission(jsonDoubleDuration)
        assertFalse("Double duration must be rejected", resDouble.isValid)
        assertTrue(resDouble.isFallbackApplied)

        // Zero durationMinutes
        val jsonZeroDuration = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", 0)
            put("missions", JSONArray().apply { put("Walk 1 mile") })
        }
        val resZero = validateAndResolveMission(jsonZeroDuration)
        assertFalse("Zero duration must be rejected", resZero.isValid)
        assertTrue(resZero.isFallbackApplied)

        // Negative durationMinutes
        val jsonNegativeDuration = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", -15)
            put("missions", JSONArray().apply { put("Walk 1 mile") })
        }
        val resNegative = validateAndResolveMission(jsonNegativeDuration)
        assertFalse("Negative duration must be rejected", resNegative.isValid)
        assertTrue(resNegative.isFallbackApplied)
    }

    @Test
    fun wrongFieldTypes_titleNotNonEmptyString_triggersFallback() {
        // Missing title
        val jsonNoTitle = JSONObject().apply {
            put("durationMinutes", 30)
            put("missions", JSONArray().apply { put("Walk") })
        }
        val resNoTitle = validateAndResolveMission(jsonNoTitle)
        assertFalse(resNoTitle.isValid)
        assertTrue(resNoTitle.isFallbackApplied)

        // Empty string title
        val jsonEmptyTitle = JSONObject().apply {
            put("title", "")
            put("durationMinutes", 30)
            put("missions", JSONArray().apply { put("Walk") })
        }
        val resEmpty = validateAndResolveMission(jsonEmptyTitle)
        assertFalse(resEmpty.isValid)
        assertTrue(resEmpty.isFallbackApplied)

        // Whitespace-only title
        val jsonBlankTitle = JSONObject().apply {
            put("title", "    ")
            put("durationMinutes", 30)
            put("missions", JSONArray().apply { put("Walk") })
        }
        val resBlank = validateAndResolveMission(jsonBlankTitle)
        assertFalse(resBlank.isValid)
        assertTrue(resBlank.isFallbackApplied)

        // Number as title
        val jsonNumberTitle = JSONObject().apply {
            put("title", 12345)
            put("durationMinutes", 30)
            put("missions", JSONArray().apply { put("Walk") })
        }
        val resNumber = validateAndResolveMission(jsonNumberTitle)
        assertFalse(resNumber.isValid)
        assertTrue(resNumber.isFallbackApplied)
    }

    @Test
    fun emptyOrInvalidMissionsArray_triggersFallback() {
        // Empty missions array
        val jsonEmptyMissions = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", 30)
            put("missions", JSONArray())
        }
        val resEmpty = validateAndResolveMission(jsonEmptyMissions)
        assertFalse(resEmpty.isValid)
        assertTrue(resEmpty.isFallbackApplied)

        // Missions array with empty string element
        val jsonBlankItem = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", 30)
            put("missions", JSONArray().apply { put("   ") })
        }
        val resBlank = validateAndResolveMission(jsonBlankItem)
        assertFalse(resBlank.isValid)
        assertTrue(resBlank.isFallbackApplied)

        // Missing missions array entirely
        val jsonMissingMissions = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", 30)
        }
        val resMissing = validateAndResolveMission(jsonMissingMissions)
        assertFalse(resMissing.isValid)
        assertTrue(resMissing.isFallbackApplied)
    }

    @Test
    fun singularMissionField_strictlyRejected() {
        // Singular "mission" field must NOT be treated as valid
        val jsonSingular = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", 30)
            put("mission", JSONArray().apply { put("Walk for 30 minutes") })
        }

        val result = validateAndResolveMission(jsonSingular)

        assertFalse("Singular 'mission' key must be rejected", result.isValid)
        assertTrue(result.isFallbackApplied)
        assertFalse(result.isMalformed)
        assertEquals("Schema Incomplete (Fallback Applied)", result.statusLabel)
        assertEquals("Neighborhood Nature Walk", result.finalMission.getString("title"))
    }

    @Test
    fun malformedJson_reportedAccuratelyWithFallbackApplied() {
        val malformedRawText = "{\"title\": \"Broken walk\", \"durationMinutes\": "
        val parsedJson = extractJson(malformedRawText)

        assertNull("Malformed JSON should fail extraction", parsedJson)

        val result = validateAndResolveMission(parsedJson)

        assertFalse(result.isValid)
        assertTrue(result.isFallbackApplied)
        assertTrue(result.isMalformed)
        assertEquals("Malformed (Fallback Applied)", result.statusLabel)
        assertEquals("No valid JSON object parsed from raw generation", result.failureReason)
        assertEquals("Neighborhood Nature Walk", result.finalMission.getString("title"))
        assertEquals(30, result.finalMission.getInt("durationMinutes"))
    }

    @Test
    fun extractJson_parsesMarkdownCodeFencesAndSurroundingText() {
        val markdownText = """
            Here is your requested trail mission:
            ```json
            {
              "title": "Riverside Jog",
              "durationMinutes": 25,
              "missions": ["Jog 3 km along the river path"]
            }
            ```
            Have a wonderful run!
        """.trimIndent()

        val parsed = extractJson(markdownText)
        assertNotNull(parsed)

        val result = validateAndResolveMission(parsed)
        assertTrue(result.isValid)
        assertFalse(result.isFallbackApplied)
        assertFalse(result.isMalformed)
        assertEquals("Riverside Jog", result.finalMission.getString("title"))
        assertEquals(25, result.finalMission.getInt("durationMinutes"))
    }

    @Test
    fun deterministicFallbackMission_isSelfValidating() {
        val fallback = getDeterministicFallbackMission()
        val result = validateAndResolveMission(fallback)

        assertTrue("Fallback mission must itself satisfy all schema requirements", result.isValid)
        assertFalse(result.isFallbackApplied)
        assertFalse(result.isMalformed)
        assertEquals("Neighborhood Nature Walk", result.finalMission.getString("title"))
        assertEquals(30, result.finalMission.getInt("durationMinutes"))
        assertEquals(3, result.finalMission.getJSONArray("missions").length())
    }

    @Test
    fun durationMismatch_withExpectedConfig_isRejectedAndTriggersFallback() {
        val jsonMismatched = JSONObject().apply {
            put("title", "Park Walk")
            put("durationMinutes", 15) // Does not match expected 30
            put("missions", JSONArray().apply { put("Walk for 15 minutes") })
        }
        val expectedConfig = com.example.trailquest.ui.setup.ExpeditionConfig(
            activity = com.example.trailquest.ui.setup.ExpeditionActivity.WALK,
            durationMinutes = 30,
            difficulty = com.example.trailquest.ui.setup.ExpeditionDifficulty.EASY
        )

        val result = validateAndResolveMission(jsonMismatched, expectedConfig)

        assertFalse("Duration mismatch must be rejected", result.isValid)
        assertTrue(result.isFallbackApplied)
        assertTrue(result.failureReason!!.contains("durationMinutes"))
        // Fallback mission must match the requested 30 minutes
        assertEquals(30, result.finalMission.getInt("durationMinutes"))
    }

    @Test
    fun activityMismatch_withExpectedConfig_isRejectedAndTriggersFallback() {
        val jsonWrongActivity = JSONObject().apply {
            put("title", "Road Run")
            put("activity", "Run")
            put("durationMinutes", 30)
            put("missions", JSONArray().apply { put("Run 5k") })
        }
        val expectedConfig = com.example.trailquest.ui.setup.ExpeditionConfig(
            activity = com.example.trailquest.ui.setup.ExpeditionActivity.HIKE,
            durationMinutes = 30,
            difficulty = com.example.trailquest.ui.setup.ExpeditionDifficulty.MODERATE
        )

        val result = validateAndResolveMission(jsonWrongActivity, expectedConfig)

        assertFalse("Activity mismatch must be rejected", result.isValid)
        assertTrue(result.isFallbackApplied)
        assertEquals("Hike", result.finalMission.getString("activity"))
    }

    @Test
    fun emptyModelResponse_handledWithFallback() {
        val emptyText = ""
        val parsed = extractJson(emptyText)
        assertNull(parsed)

        val expectedConfig = com.example.trailquest.ui.setup.ExpeditionConfig(
            activity = com.example.trailquest.ui.setup.ExpeditionActivity.NATURE_EXPLORE,
            durationMinutes = 15,
            difficulty = com.example.trailquest.ui.setup.ExpeditionDifficulty.EASY
        )

        val result = validateAndResolveMission(parsed, expectedConfig)

        assertFalse(result.isValid)
        assertTrue(result.isFallbackApplied)
        assertTrue(result.isMalformed)
        assertEquals("Nature Explore", result.finalMission.getString("activity"))
        assertEquals(15, result.finalMission.getInt("durationMinutes"))
    }

    @Test
    fun allThirtySixConfigurations_produceValidTailoredFallbacks() {
        for (activity in com.example.trailquest.ui.setup.ExpeditionActivity.entries) {
            for (duration in com.example.trailquest.ui.setup.SUPPORTED_DURATIONS) {
                for (difficulty in com.example.trailquest.ui.setup.ExpeditionDifficulty.entries) {
                    val config = com.example.trailquest.ui.setup.ExpeditionConfig(activity, duration, difficulty)
                    val fallbackJson = getDeterministicFallbackMission(config)

                    // Must pass strict validation for this specific configuration
                    val result = validateAndResolveMission(fallbackJson, config)
                    assertTrue("Fallback for $config must be valid", result.isValid)
                    assertFalse(result.isFallbackApplied)
                    assertEquals(config.durationMinutes, result.finalMission.getInt("durationMinutes"))
                    assertEquals(config.activity.title, result.finalMission.getString("activity"))
                    assertEquals(config.difficulty.title, result.finalMission.getString("difficulty"))
                    assertTrue(result.finalMission.getString("title").isNotBlank())

                    val missions = result.finalMission.getJSONArray("missions")
                    assertTrue("Missions array for $config must have at least 2 items", missions.length() >= 2)
                    for (i in 0 until missions.length()) {
                        assertTrue("Task $i must be non-empty", missions.getString(i).isNotBlank())
                    }
                }
            }
        }
    }

    @Test
    fun isAiGenerated_isStrictlyFalseWhenFallbackApplied() {
        val config = com.example.trailquest.ui.setup.ExpeditionConfig(
            activity = com.example.trailquest.ui.setup.ExpeditionActivity.WALK,
            durationMinutes = 30
        )
        // Simulate malformed result
        val fallbackResult = validateAndResolveMission(null, config)
        val plan = fallbackResult.finalMission.toMissionPlan(
            config = config,
            isAiGenerated = fallbackResult.isValid && !fallbackResult.isFallbackApplied
        )

        assertFalse("Fallback mission must NEVER be labeled as AI-generated", plan.isAiGenerated)
    }

    @Test
    fun missingActivity_inExpeditionMode_triggersFallback() {
        val jsonNoActivity = JSONObject().apply {
            put("title", "Walk Mission")
            put("durationMinutes", 30)
            put("difficulty", "Moderate")
            put("missions", JSONArray().apply {
                put("Walk at a brisk pace.")
                put("Return home.")
            })
        }
        val config = com.example.trailquest.ui.setup.ExpeditionConfig(
            activity = com.example.trailquest.ui.setup.ExpeditionActivity.WALK,
            durationMinutes = 30,
            difficulty = com.example.trailquest.ui.setup.ExpeditionDifficulty.MODERATE
        )
        val result = validateAndResolveMission(jsonNoActivity, config)
        assertFalse("Missing activity must fail expedition validation", result.isValid)
        assertTrue(result.isFallbackApplied)
        assertEquals("Walk", result.finalMission.getString("activity"))
        assertEquals("Moderate", result.finalMission.getString("difficulty"))
    }

    @Test
    fun missingDifficulty_inExpeditionMode_triggersFallback() {
        val jsonNoDifficulty = JSONObject().apply {
            put("title", "Walk Mission")
            put("activity", "Walk")
            put("durationMinutes", 30)
            put("missions", JSONArray().apply {
                put("Walk at a brisk pace.")
                put("Return home.")
            })
        }
        val config = com.example.trailquest.ui.setup.ExpeditionConfig(
            activity = com.example.trailquest.ui.setup.ExpeditionActivity.WALK,
            durationMinutes = 30,
            difficulty = com.example.trailquest.ui.setup.ExpeditionDifficulty.MODERATE
        )
        val result = validateAndResolveMission(jsonNoDifficulty, config)
        assertFalse("Missing difficulty must fail expedition validation", result.isValid)
        assertTrue(result.isFallbackApplied)
        assertEquals("Walk", result.finalMission.getString("activity"))
        assertEquals("Moderate", result.finalMission.getString("difficulty"))
    }

    @Test
    fun emptyOrNonStringActivityAndDifficulty_triggersFallback() {
        val config = com.example.trailquest.ui.setup.ExpeditionConfig(
            activity = com.example.trailquest.ui.setup.ExpeditionActivity.HIKE,
            durationMinutes = 15,
            difficulty = com.example.trailquest.ui.setup.ExpeditionDifficulty.EASY
        )

        // Non-string activity
        val jsonNumberActivity = JSONObject().apply {
            put("title", "Hike Mission")
            put("activity", 123)
            put("durationMinutes", 15)
            put("difficulty", "Easy")
            put("missions", JSONArray().apply { put("Step 1"); put("Step 2") })
        }
        val res1 = validateAndResolveMission(jsonNumberActivity, config)
        assertFalse("Number activity must fail", res1.isValid)
        assertTrue(res1.isFallbackApplied)

        // Empty string activity
        val jsonEmptyActivity = JSONObject().apply {
            put("title", "Hike Mission")
            put("activity", "   ")
            put("durationMinutes", 15)
            put("difficulty", "Easy")
            put("missions", JSONArray().apply { put("Step 1"); put("Step 2") })
        }
        val res2 = validateAndResolveMission(jsonEmptyActivity, config)
        assertFalse("Blank activity must fail", res2.isValid)
        assertTrue(res2.isFallbackApplied)

        // Non-string difficulty
        val jsonBooleanDifficulty = JSONObject().apply {
            put("title", "Hike Mission")
            put("activity", "Hike")
            put("durationMinutes", 15)
            put("difficulty", true)
            put("missions", JSONArray().apply { put("Step 1"); put("Step 2") })
        }
        val res3 = validateAndResolveMission(jsonBooleanDifficulty, config)
        assertFalse("Boolean difficulty must fail", res3.isValid)
        assertTrue(res3.isFallbackApplied)

        // Empty string difficulty
        val jsonEmptyDifficulty = JSONObject().apply {
            put("title", "Hike Mission")
            put("activity", "Hike")
            put("durationMinutes", 15)
            put("difficulty", "")
            put("missions", JSONArray().apply { put("Step 1"); put("Step 2") })
        }
        val res4 = validateAndResolveMission(jsonEmptyDifficulty, config)
        assertFalse("Blank difficulty must fail", res4.isValid)
        assertTrue(res4.isFallbackApplied)
    }

    @Test
    fun caseInsensitiveActivityAndDifficulty_isAccepted() {
        val jsonCase = JSONObject().apply {
            put("title", "Hike Mission")
            put("activity", "hike")
            put("durationMinutes", 30)
            put("difficulty", "MODERATE")
            put("missions", JSONArray().apply {
                put("Follow a mountain trail for 15 minutes.")
                put("Return along the same path.")
            })
        }
        val config = com.example.trailquest.ui.setup.ExpeditionConfig(
            activity = com.example.trailquest.ui.setup.ExpeditionActivity.HIKE,
            durationMinutes = 30,
            difficulty = com.example.trailquest.ui.setup.ExpeditionDifficulty.MODERATE
        )
        val result = validateAndResolveMission(jsonCase, config)
        assertTrue("Case-insensitive activity and difficulty must be accepted", result.isValid)
        assertFalse(result.isFallbackApplied)
    }

    @Test
    fun benchmarkMode_withoutExpectedConfig_acceptsThreeFieldJson() {
        val benchJson = JSONObject().apply {
            put("title", "Walking Mission")
            put("durationMinutes", 30)
            put("missions", JSONArray().apply {
                put("Walk for 30 minutes")
            })
        }
        // When expectedConfig == null, activity and difficulty are not required, single task is accepted
        val result = validateAndResolveMission(benchJson, null)
        assertTrue("Benchmark JSON must pass when expectedConfig is null", result.isValid)
        assertFalse(result.isFallbackApplied)
        assertEquals("Walking Mission", result.finalMission.getString("title"))
        assertEquals(30, result.finalMission.getInt("durationMinutes"))
    }

    @Test
    fun differentDifficulties_produceDifferentTaskContent_forAllThirtySixConfigurations() {
        for (activity in com.example.trailquest.ui.setup.ExpeditionActivity.entries) {
            for (duration in com.example.trailquest.ui.setup.SUPPORTED_DURATIONS) {
                val easy = getDeterministicFallbackMission(com.example.trailquest.ui.setup.ExpeditionConfig(activity, duration, com.example.trailquest.ui.setup.ExpeditionDifficulty.EASY))
                val moderate = getDeterministicFallbackMission(com.example.trailquest.ui.setup.ExpeditionConfig(activity, duration, com.example.trailquest.ui.setup.ExpeditionDifficulty.MODERATE))
                val challenging = getDeterministicFallbackMission(com.example.trailquest.ui.setup.ExpeditionConfig(activity, duration, com.example.trailquest.ui.setup.ExpeditionDifficulty.CHALLENGING))

                val easyTasks = (0 until easy.getJSONArray("missions").length()).map { easy.getJSONArray("missions").getString(it) }
                val modTasks = (0 until moderate.getJSONArray("missions").length()).map { moderate.getJSONArray("missions").getString(it) }
                val hardTasks = (0 until challenging.getJSONArray("missions").length()).map { challenging.getJSONArray("missions").getString(it) }

                assertNotEquals(
                    "Easy and Moderate tasks must differ for $activity at $duration min",
                    easyTasks, modTasks
                )
                assertNotEquals(
                    "Moderate and Challenging tasks must differ for $activity at $duration min",
                    modTasks, hardTasks
                )
                assertNotEquals(
                    "Easy and Challenging tasks must differ for $activity at $duration min",
                    easyTasks, hardTasks
                )
            }
        }
    }
}
