package com.example.trailquest

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
