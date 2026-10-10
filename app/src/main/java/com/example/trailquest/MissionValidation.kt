package com.example.trailquest

import com.example.trailquest.ui.setup.ExpeditionActivity
import com.example.trailquest.ui.setup.ExpeditionConfig
import com.example.trailquest.ui.setup.ExpeditionDifficulty
import org.json.JSONArray
import org.json.JSONObject

internal const val BENCH_PROMPT =
    "Generate exactly one JSON object with all three fields: \"title\", \"durationMinutes\", and \"missions\".\n\n" +
    "Schema:\n" +
    "{\"title\": \"Walking Mission\", \"durationMinutes\": 30, \"missions\": [\"Walk for 30 minutes\"]}\n\n" +
    "Request: Create one simple 30-minute walking mission. Output ONLY the JSON object starting with { and ending with }."

internal class JsonCompleteException : Exception()

internal data class MissionValidationResult(
    val isValid: Boolean,
    val isFallbackApplied: Boolean,
    val isMalformed: Boolean,
    val statusLabel: String,
    val failureReason: String? = null,
    val finalMission: JSONObject
)

internal fun buildMissionPrompt(config: ExpeditionConfig): String {
    val activity = config.activity.title
    val duration = config.durationMinutes
    val difficulty = config.difficulty.title
    val sampleTasks = when (config.activity) {
        ExpeditionActivity.WALK -> """["Walk briskly along a quiet neighborhood street or park.", "Observe three different flowering plants or tall trees.", "Complete a steady cool-down stroll back home."]"""
        ExpeditionActivity.HIKE -> """["Hike at a steady pace up a trail or uneven ground.", "Spot local wildlife signs or distinctive rock formations.", "Take a deep breath at the turnaround point and head back."]"""
        ExpeditionActivity.RUN -> """["Warm up with light jogging for five minutes.", "Maintain a brisk running rhythm for the main stretch.", "Cool down with slow jogging and stretching."]"""
        ExpeditionActivity.NATURE_EXPLORE -> """["Wander quietly through a green park or wooded area.", "Carefully examine unique leaves, bark textures, and insects.", "Find a quiet spot to pause and listen to outdoor sounds."]"""
    }
    return """
Generate exactly one JSON object with all five fields: "title", "activity", "durationMinutes", "difficulty", and "missions".

Schema:
{"title": "$difficulty $activity Expedition", "activity": "$activity", "durationMinutes": $duration, "difficulty": "$difficulty", "missions": $sampleTasks}

Request: Create one safe, achievable $duration-minute $difficulty $activity outdoor mission.
Provide 2 to 3 concrete outdoor tasks matching $activity. Require no special gear, GPS, camera, or internet. Stay alert to traffic and surroundings. Output ONLY the JSON object starting with { and ending with }.
""".trimIndent()
}

internal fun getDeterministicFallbackMission(config: ExpeditionConfig? = null): JSONObject {
    if (config == null) {
        return JSONObject().apply {
            put("title", "Neighborhood Nature Walk")
            put("durationMinutes", 30)
            put("missions", JSONArray().apply {
                put("Walk at a brisk pace for 15 minutes along a local park or green trail.")
                put("Identify three distinct types of native trees or urban plants.")
                put("Complete a gentle 15-minute return loop while tracking step cadence.")
            })
        }
    }

    val activity = config.activity
    val duration = config.durationMinutes
    val difficulty = config.difficulty

    val title = when (activity) {
        ExpeditionActivity.WALK -> when (duration) {
            15 -> "Brisk Neighborhood Stroll"
            30 -> "Neighborhood Nature Walk"
            else -> "Perimeter Exploration Walk"
        }
        ExpeditionActivity.HIKE -> when (duration) {
            15 -> "Short Trail Loop Hike"
            30 -> "Ridge & Woods Trail Hike"
            else -> "Woodland Explorer Hike"
        }
        ExpeditionActivity.RUN -> when (duration) {
            15 -> "Quick Tempo Run"
            30 -> "Paced Aerobic Run"
            else -> "Endurance Road & Trail Run"
        }
        ExpeditionActivity.NATURE_EXPLORE -> when (duration) {
            15 -> "Mindful Canopy Discovery"
            30 -> "Botanical & Biodiversity Search"
            else -> "Urban Ecosystem Survey"
        }
    }

    val tasks = when (activity) {
        ExpeditionActivity.WALK -> when (duration) {
            15 -> listOf(
                "Walk at a steady, comfortable pace for 7 minutes along a familiar sidewalk or path.",
                "Turn around and complete an active 8-minute return leg while observing neighborhood gardens."
            )
            30 -> listOf(
                "Walk at a brisk pace for 15 minutes along a local park or green trail.",
                "Identify three distinct types of native trees or urban plants along your route.",
                "Complete a gentle 15-minute return loop while tracking step cadence and breathing."
            )
            else -> listOf(
                "Walk for 30 minutes following a wide, safe route through your neighborhood or local park.",
                "Observe local landmarks, bird species, and changing topography during the journey.",
                "Complete a 30-minute return loop maintaining a steady, relaxed walking rhythm."
            )
        }
        ExpeditionActivity.HIKE -> when (duration) {
            15 -> listOf(
                "Hike for 8 minutes along a natural path, focusing on balanced footing and posture.",
                "Ascend a mild incline and pause for a 360-degree environmental scan.",
                "Return along the trail loop with deliberate, steady pacing."
            )
            30 -> listOf(
                "Hike 15 minutes on natural terrain, keeping a mindful and consistent tempo.",
                "Identify two distinct geological formations, rock types, or trail markers.",
                "Hike the 15-minute return path observing changes in elevation and vegetation."
            )
            else -> listOf(
                "Follow a wooded trail or nature reserve path for 30 minutes with steady uphill pacing.",
                "Take note of changes in canopy cover, woodland sounds, and trail markers.",
                "Navigate a 30-minute return segment with careful footing across uneven ground."
            )
        }
        ExpeditionActivity.RUN -> when (duration) {
            15 -> listOf(
                "Jog lightly for 5 minutes as a progressive aerobic warmup.",
                "Run at a steady pace for 7 minutes along a clear, pedestrian-safe route.",
                "Cool down with a 3-minute easy jog and walking recovery."
            )
            30 -> listOf(
                "Warm up with a 5-minute easy run along a paved or flat surface.",
                "Maintain an even, focused running pace for 20 minutes.",
                "Conclude with 5 minutes of gradual deceleration and gentle walking."
            )
            else -> listOf(
                "Begin with a 10-minute gentle warmup jog to establish a smooth rhythm.",
                "Maintain a steady endurance pace for 40 minutes along an open circuit.",
                "Finish with a 10-minute cooldown jog and recovery walk."
            )
        }
        ExpeditionActivity.NATURE_EXPLORE -> when (duration) {
            15 -> listOf(
                "Stroll outdoors for 7 minutes, observing tree canopies and plant textures.",
                "Stop and identify three distinct leaf shapes or bark patterns.",
                "Walk 8 minutes back along a different path while listening for local wildlife sounds."
            )
            30 -> listOf(
                "Explore a local green space or tree-lined area for 15 minutes.",
                "Find and observe five distinct flora species or pollinator habitats.",
                "Spend 15 minutes returning while noting seasonal transitions in surrounding plants."
            )
            else -> listOf(
                "Spend 30 minutes exploring local parks, greenways, or community flora.",
                "Catalog distinct tree varieties, bird calls, and soil or rock formations.",
                "Complete a 30-minute observational loop focusing on natural life in your area."
            )
        }
    }

    return JSONObject().apply {
        put("title", title)
        put("activity", activity.title)
        put("durationMinutes", duration)
        put("difficulty", difficulty.title)
        put("missions", JSONArray().apply {
            tasks.forEach { put(it) }
        })
    }
}

/**
 * Validates mission schema:
 * - non-empty title (String)
 * - actual positive integer durationMinutes (Int > 0)
 * - non-empty missions array (JSONArray containing only non-empty Strings)
 * - If expectedConfig is provided, enforces that duration matches expectedConfig.durationMinutes,
 *   activity matches expectedConfig.activity.title (if present), and difficulty matches (if present).
 *
 * Strict validation: does not accept singular "mission" field.
 * Returns deterministic fallback when raw JSON is null/malformed or schema is incomplete.
 */
internal fun validateAndResolveMission(
    rawJson: JSONObject?,
    expectedConfig: ExpeditionConfig? = null
): MissionValidationResult {
    if (rawJson == null) {
        return MissionValidationResult(
            isValid = false,
            isFallbackApplied = true,
            isMalformed = true,
            statusLabel = "Malformed (Fallback Applied)",
            failureReason = "No valid JSON object parsed from raw generation",
            finalMission = getDeterministicFallbackMission(expectedConfig)
        )
    }

    val missingOrInvalid = mutableListOf<String>()

    // 1. Strict non-empty string title
    val rawTitle = rawJson.opt("title")
    val hasValidTitle = rawTitle is String && rawTitle.trim().isNotEmpty()
    if (!hasValidTitle) {
        missingOrInvalid.add("title (must be non-empty string)")
    }

    // 2. Strict actual positive integer durationMinutes
    val rawDuration = rawJson.opt("durationMinutes")
    val hasValidDuration = when (rawDuration) {
        is Int -> rawDuration > 0
        is Long -> rawDuration > 0L && rawDuration <= Int.MAX_VALUE
        else -> false
    }
    if (!hasValidDuration) {
        missingOrInvalid.add("durationMinutes (must be positive integer)")
    } else if (expectedConfig != null) {
        val durationInt = (rawDuration as Number).toInt()
        if (durationInt != expectedConfig.durationMinutes) {
            missingOrInvalid.add("durationMinutes ($durationInt does not match requested ${expectedConfig.durationMinutes})")
        }
    }

    // 3. Strict non-empty missions array of non-empty strings (reject singular "mission")
    val rawMissions = rawJson.opt("missions")
    var hasValidMissions = false
    if (rawMissions is JSONArray && rawMissions.length() > 0) {
        var allStringsValid = true
        for (i in 0 until rawMissions.length()) {
            val item = rawMissions.opt(i)
            if (item !is String || item.trim().isEmpty()) {
                allStringsValid = false
                break
            }
        }
        hasValidMissions = allStringsValid
    }
    if (!hasValidMissions) {
        missingOrInvalid.add("missions (must be non-empty array of non-empty strings)")
    }

    // 4. If expectedConfig is provided, check optional activity and difficulty fields and obvious contradictions
    if (expectedConfig != null) {
        if (rawJson.has("activity")) {
            val rawAct = rawJson.optString("activity")
            if (!rawAct.equals(expectedConfig.activity.title, ignoreCase = true)) {
                missingOrInvalid.add("activity ($rawAct does not match requested ${expectedConfig.activity.title})")
            }
        }
        if (rawJson.has("difficulty")) {
            val rawDiff = rawJson.optString("difficulty")
            if (!rawDiff.equals(expectedConfig.difficulty.title, ignoreCase = true)) {
                missingOrInvalid.add("difficulty ($rawDiff does not match requested ${expectedConfig.difficulty.title})")
            }
        }

        // Check for task text contradictions with chosen activity
        if (hasValidMissions && rawMissions is JSONArray) {
            val combinedTasks = (0 until rawMissions.length())
                .joinToString(" ") { rawMissions.optString(it) }
                .lowercase()

            val isContradictory = when (expectedConfig.activity) {
                ExpeditionActivity.WALK -> combinedTasks.contains("sprint at full speed") || combinedTasks.contains("run 5k")
                ExpeditionActivity.NATURE_EXPLORE -> combinedTasks.contains("sprint") || combinedTasks.contains("run at maximum pace")
                else -> false
            }
            if (isContradictory) {
                missingOrInvalid.add("missions contain tasks contradictory to ${expectedConfig.activity.title}")
            }
        }
    }

    if (hasValidTitle && hasValidDuration && hasValidMissions && missingOrInvalid.isEmpty()) {
        return MissionValidationResult(
            isValid = true,
            isFallbackApplied = false,
            isMalformed = false,
            statusLabel = "Valid",
            finalMission = rawJson
        )
    }

    return MissionValidationResult(
        isValid = false,
        isFallbackApplied = true,
        isMalformed = false,
        statusLabel = "Schema Incomplete (Fallback Applied)",
        failureReason = "Invalid fields: ${missingOrInvalid.joinToString(", ")}",
        finalMission = getDeterministicFallbackMission(expectedConfig)
    )
}

/**
 * Extracts and parses a JSONObject even if surrounded by markdown code fences,
 * conversational preamble, or trailing commentary.
 */
internal fun extractJson(text: String): JSONObject? {
    var cleaned = text.trim()
    if (cleaned.startsWith("```json")) {
        cleaned = cleaned.removePrefix("```json").trim()
    } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.removePrefix("```").trim()
    }
    if (cleaned.endsWith("```")) {
        cleaned = cleaned.removeSuffix("```").trim()
    }

    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start != -1 && end != -1 && end >= start) {
        val jsonStr = cleaned.substring(start, end + 1)
        return try {
            JSONObject(jsonStr)
        } catch (_: Exception) {
            null
        }
    }
    return null
}

internal fun JSONObject.toMissionPlan(config: ExpeditionConfig, isAiGenerated: Boolean): MissionPlan {
    val title = optString("title").ifBlank { "${config.activity.title} Expedition" }
    val durationMinutes = optInt("durationMinutes", config.durationMinutes)
    val missionsArray = optJSONArray("missions")
    val tasks = if (missionsArray != null && missionsArray.length() > 0) {
        (0 until missionsArray.length()).map { missionsArray.getString(it) }
    } else {
        listOf("Explore your local surroundings safely.")
    }
    return MissionPlan(
        title = title,
        durationMinutes = durationMinutes,
        tasks = tasks,
        activity = config.activity,
        difficulty = config.difficulty,
        isAiGenerated = isAiGenerated
    )
}
