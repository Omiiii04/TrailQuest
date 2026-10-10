package com.example.trailquest

import com.example.trailquest.ui.setup.ExpeditionActivity
import com.example.trailquest.ui.setup.ExpeditionConfig
import com.example.trailquest.ui.setup.ExpeditionDifficulty
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal const val BENCH_PROMPT =
    "Generate exactly one JSON object with all three fields: \"title\", \"durationMinutes\", and \"missions\".\n\n" +
    "Schema:\n" +
    "{\"title\": \"Walking Mission\", \"durationMinutes\": 30, \"missions\": [\"Walk for 30 minutes\"]}\n\n" +
    "Request: Create one simple 30-minute walking mission. Output ONLY the JSON object starting with { and ending with }."

internal class JsonCompleteException : CancellationException("JSON generation complete")

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

/**
 * Generates an offline deterministic fallback mission.
 * Exhaustively supports all 36 combinations of:
 * - 4 Activities (Walk, Hike, Run, Nature Explore)
 * - 3 Durations (15, 30, 60 minutes)
 * - 3 Difficulties (Easy, Moderate, Challenging)
 *
 * Ensures difficulty choices produce meaningfully different task requirements
 * (e.g., relaxed pace vs brisk tempo vs power/interval challenge).
 */
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

    val (title, tasks) = when (activity) {
        ExpeditionActivity.WALK -> when (duration) {
            15 -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Gentle Neighborhood Stroll",
                    listOf(
                        "Walk at a relaxed, unhurried pace for 7 minutes along flat sidewalks or paths.",
                        "Turn around and take a slow 8-minute return walk, noticing calm sights in your surroundings."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Brisk Neighborhood Walk",
                    listOf(
                        "Walk at a brisk, purposeful pace for 7 minutes, keeping a steady breathing rhythm.",
                        "Complete an active 8-minute return loop, scanning for three distinct tree or architectural features."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Power Cadence Walk",
                    listOf(
                        "Maintain an energetic, fast-paced stride for 8 minutes with active arm swing.",
                        "Choose a slight incline or staircase if available, sustaining high walking cadence for the remaining 7 minutes."
                    )
                )
            }
            30 -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Relaxed Community Walk",
                    listOf(
                        "Stroll at an easy, conversational pace for 15 minutes along a quiet street or park pathway.",
                        "Observe five different flowering plants or garden beds along your path.",
                        "Complete a comfortable 15-minute return loop with light shoulder and calf rolls."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Steady Tempo Walk",
                    listOf(
                        "Maintain a sustained, brisk walking tempo for 14 minutes along your route.",
                        "Identify three distinct tree species and track your step cadence over a 2-minute interval.",
                        "Finish with a 14-minute return stretch and a 2-minute gentle cooldown stroll."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Vigorous Interval Walk",
                    listOf(
                        "Warm up with 5 minutes of brisk walking, then alternate 2-minute fast walking bursts with 1-minute steady pace for 15 minutes.",
                        "Seek out mild street grades or park hills to elevate heart rate while maintaining upright posture.",
                        "Conclude with an 8-minute brisk return and a 2-minute relaxed recovery stroll."
                    )
                )
            }
            else -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Leisurely Long Walk",
                    listOf(
                        "Follow a flat, scenic loop for 25 minutes at an easy, restorative pace.",
                        "Pause for a mindful 5-minute breather to observe neighborhood landmarks and horizon views.",
                        "Complete a 30-minute relaxed return loop, keeping footsteps soft and breathing calm."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Extended Perimeter Walk",
                    listOf(
                        "Walk for 25 minutes maintaining a continuous, moderate aerobic tempo through your area.",
                        "Navigate varied sidewalk terrain, checking off four different neighborhood sectors or park zones.",
                        "Complete a 30-minute return loop sustaining purposeful stride and consistent pace."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Endurance Power March",
                    listOf(
                        "Begin with a 10-minute progressive warmup leading into a fast, athletic walking pace.",
                        "Sustain high-cadence walking for 35 minutes across rolling street terrain, maintaining upright core posture.",
                        "Power through a 10-minute brisk return section, followed by a 5-minute cool-down stroll."
                    )
                )
            }
        }
        ExpeditionActivity.HIKE -> when (duration) {
            15 -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Gentle Nature Trail Walk",
                    listOf(
                        "Hike along an even, well-trodden dirt path or green park trail for 7 minutes at a comfortable pace.",
                        "Pause to inspect surrounding foliage or natural ground textures before a relaxed 8-minute return."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Trail Elevation Loop",
                    listOf(
                        "Hike 7 minutes along natural terrain with mild grade changes, focusing on sure footing.",
                        "Ascend a moderate slope or uneven path segment, maintaining steady pacing through an 8-minute return loop."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Brisk Incline Trail Challenge",
                    listOf(
                        "Hike at an active, vigorous pace for 8 minutes up available inclines or rough natural ground.",
                        "Maintain steady upward drive and agile footing, completing a quick, focused 7-minute descent loop."
                    )
                )
            }
            30 -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Scenic Woodland Hike",
                    listOf(
                        "Follow an easy dirt trail or park path for 15 minutes at a relaxed, steady pace.",
                        "Spot two distinctive rock formations, fallen logs, or woodland features along the path.",
                        "Hike the 15-minute return trail with mindful posture and balanced steps."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Ridge & Woods Trail Hike",
                    listOf(
                        "Hike 14 minutes across varied outdoor terrain with continuous moderate effort.",
                        "Navigate roots and mild switchbacks or slopes, pausing briefly for a 2-minute panoramic scan.",
                        "Maintain a rhythmic hiking tempo on the 14-minute return route across changing surface grades."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Summit Ridge Trek",
                    listOf(
                        "Power up available trail inclines or steeper terrain for 15 minutes with strong leg drive.",
                        "Traverse uneven dirt or gravel paths at a demanding, steady cadence while staying vigilant of foot placement.",
                        "Complete a focused 15-minute return leg, controlling descent speed with balanced knee flexion."
                    )
                )
            }
            else -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Gentle Wilderness Loop",
                    listOf(
                        "Hike along shaded, well-marked park trails or nature reserves for 25 minutes at an unhurried pace.",
                        "Take a 5-minute scenic rest to listen to natural ambience and inspect woodland canopy layers.",
                        "Navigate a peaceful 30-minute return loop on flat or gently rolling natural paths."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Woodland Explorer Hike",
                    listOf(
                        "Hike for 25 minutes into local conservation trails, maintaining a steady, athletic pace.",
                        "Overcome moderate elevation gains and rocky trail sections, noting trail markers and natural landmarks.",
                        "Complete a 30-minute return segment across mixed natural surfaces, keeping consistent stamina."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Backcountry Endurance Hike",
                    listOf(
                        "Tackle continuous elevation gains and rugged trail sections for 25 minutes at an intense, athletic hiking pace.",
                        "Maintain rigorous uphill push and technical footing across 20 minutes of varied single-track or uneven ground.",
                        "Complete a demanding 15-minute return descent, engaging core stabilizers for controlled, fatigue-resistant pacing."
                    )
                )
            }
        }
        ExpeditionActivity.RUN -> when (duration) {
            15 -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Gentle Recovery Jog",
                    listOf(
                        "Jog at a very relaxed, easy conversational pace for 7 minutes along flat pavement or paths.",
                        "Transition into an 8-minute easy return jog, keeping heart rate low and foot strikes soft."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Steady Tempo Run",
                    listOf(
                        "Warm up with 3 minutes of easy jogging, then settle into a crisp, sustained pace for 9 minutes.",
                        "Finish with 3 minutes of gradual deceleration and a gentle walking cooldown."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "High-Intensity Pace Run",
                    listOf(
                        "Jog lightly for 3 minutes, then run at a hard, demanding tempo for 10 minutes along a clear route.",
                        "Push through the final 2 minutes with high knee drive, followed by controlled breathing recovery."
                    )
                )
            }
            30 -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Aerobic Foundation Run",
                    listOf(
                        "Warm up with 5 minutes of brisk walking and easy jogging.",
                        "Run at a smooth, sustainable pace for 20 minutes where speaking a full sentence remains easy.",
                        "Cool down with 5 minutes of relaxed walking and light leg stretches."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Paced Aerobic Run",
                    listOf(
                        "Warm up with 5 minutes of progressive jogging along an open circuit.",
                        "Lock into a solid, rhythmic running pace for 20 minutes, monitoring consistent split efforts.",
                        "Conclude with 5 minutes of easy jogging and active calf and hamstring cooldown."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Interval Threshold Run",
                    listOf(
                        "Warm up with 5 minutes of jogging, then alternate five 3-minute hard threshold intervals with 1-minute jog recoveries.",
                        "Maintain aggressive running posture and cadence through slight road or trail rises.",
                        "Cool down with 5 minutes of easy jogging and recovery walking."
                    )
                )
            }
            else -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Long Slow Distance Run",
                    listOf(
                        "Begin with 10 minutes of gentle, unforced jogging to ease joints and muscles into motion.",
                        "Sustain a low-intensity, relaxed conversational run for 40 minutes along flat roads or multi-use paths.",
                        "Conclude with 10 minutes of decelerating jog and walking recovery, keeping hydration in mind."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Endurance Road & Trail Run",
                    listOf(
                        "Warm up with 10 minutes of steady jogging, establishing a clean, efficient stride.",
                        "Run 40 minutes at a solid marathon-style aerobic tempo across undulating asphalt and park paths.",
                        "Finish with 10 minutes of easy cool-down jogging and post-run mobility stretches."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Progressive Marathon-Pace Run",
                    listOf(
                        "Warm up for 10 minutes, then run 35 minutes at a challenging tempo, progressively increasing pace every 10 minutes.",
                        "Execute a 10-minute fast finish at near 10k race effort while holding strict running form.",
                        "Cool down with 5 minutes of slow recovery jogging and walk-out breathing."
                    )
                )
            }
        }
        ExpeditionActivity.NATURE_EXPLORE -> when (duration) {
            15 -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Mindful Sensory Nature Walk",
                    listOf(
                        "Wander quietly for 7 minutes through a garden, park, or tree-lined street at an unhurried pace.",
                        "Pause by three different plants or trees to observe leaf textures, scents, and colors before a gentle 8-minute return."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Botanical Observation Quest",
                    listOf(
                        "Explore a green space for 7 minutes at a steady pace, looking for distinct flora and insect micro-habitats.",
                        "Find and document characteristics of four unique plant varieties or bark patterns during an 8-minute return loop."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Active Biodiversity Scavenger",
                    listOf(
                        "Move actively through a nature area for 8 minutes, spotting contrasting ecological zones (shade, sun, moss, dry ground).",
                        "Locate and catalog five distinct wildlife signs or plant adaptations while maintaining a brisk 7-minute return pace."
                    )
                )
            }
            30 -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Tranquil Park Nature Stroll",
                    listOf(
                        "Spend 12 minutes strolling leisurely through a green space, tuning in to ambient natural sounds.",
                        "Find a peaceful bench or spot under a tree canopy to sit for 6 minutes, identifying three distinct bird calls.",
                        "Complete a 12-minute relaxed walk back along a scenic, plant-lined path."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Botanical & Biodiversity Search",
                    listOf(
                        "Hike through an urban park or nature reserve for 12 minutes at an active, exploratory pace.",
                        "Locate five different species of trees, analyzing leaf serration, bark furrowing, and seed pods.",
                        "Spend 12 minutes exploring a secondary trail loop, observing how terrain dampness influences plant life."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Ecosystem Field Survey",
                    listOf(
                        "Trek briskly for 12 minutes through varied terrain to map transitions between distinct plant communities.",
                        "Catalog six distinct species (trees, shrubs, ferns, or pollinators), noting microclimate differences.",
                        "Cover an extensive 14-minute return loop at an athletic pace, observing soil composition and canopy density."
                    )
                )
            }
            else -> when (difficulty) {
                ExpeditionDifficulty.EASY -> Pair(
                    "Immersive Forest Bathing Stroll",
                    listOf(
                        "Walk at a slow, meditative pace for 25 minutes through a densely vegetated park or wooded reserve.",
                        "Take a mindful 10-minute rest observing sunlight filtering through tree canopies and wind patterns.",
                        "Complete a 25-minute gentle return walk along a meandering natural path, breathing deeply."
                    )
                )
                ExpeditionDifficulty.MODERATE -> Pair(
                    "Comprehensive Naturalist Journey",
                    listOf(
                        "Explore a large park or conservation area for 25 minutes, maintaining a purposeful, curious walking pace.",
                        "Identify and compare eight different flora varieties, recording differences between sun-exposed and shaded growth.",
                        "Navigate a 25-minute return route along a riverbank, pond edge, or wooded border, observing wildlife signs."
                    )
                )
                ExpeditionDifficulty.CHALLENGING -> Pair(
                    "Expedition Habitat Mapping",
                    listOf(
                        "Hike at an energetic, athletic pace for 25 minutes across extensive parklands or nature trails.",
                        "Perform a thorough 15-minute environmental audit: classify 10 distinct botanical and geological features across diverse topography.",
                        "Power through a 20-minute return loop on varied, uneven terrain, connecting multiple natural habitats."
                    )
                )
            }
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
 * - When expectedConfig != null: requires all 5 fields (title, activity, durationMinutes, difficulty, missions),
 *   enforcing that duration matches expectedConfig.durationMinutes, activity matches expectedConfig.activity.title
 *   (case-insensitively), and difficulty matches expectedConfig.difficulty.title (case-insensitively).
 * - When expectedConfig == null: preserves benchmark compatibility (title, durationMinutes, missions).
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

    // 4. Expedition-specific validation: when expectedConfig != null, require activity and difficulty
    if (expectedConfig != null) {
        val rawActivity = rawJson.opt("activity")
        if (rawActivity !is String || rawActivity.trim().isEmpty()) {
            missingOrInvalid.add("activity (missing or empty string)")
        } else if (!rawActivity.trim().equals(expectedConfig.activity.title, ignoreCase = true)) {
            missingOrInvalid.add("activity ($rawActivity does not match requested ${expectedConfig.activity.title})")
        }

        val rawDifficulty = rawJson.opt("difficulty")
        if (rawDifficulty !is String || rawDifficulty.trim().isEmpty()) {
            missingOrInvalid.add("difficulty (missing or empty string)")
        } else if (!rawDifficulty.trim().equals(expectedConfig.difficulty.title, ignoreCase = true)) {
            missingOrInvalid.add("difficulty ($rawDifficulty does not match requested ${expectedConfig.difficulty.title})")
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

/**
 * Converts a strictly validated JSONObject or deterministic fallback into a [MissionPlan].
 * Does not silently invent fallback values.
 */
internal fun JSONObject.toMissionPlan(config: ExpeditionConfig, isAiGenerated: Boolean): MissionPlan {
    val title = getString("title")
    val durationMinutes = getInt("durationMinutes")
    val missionsArray = getJSONArray("missions")
    val tasks = (0 until missionsArray.length()).map { missionsArray.getString(it) }
    return MissionPlan(
        title = title,
        durationMinutes = durationMinutes,
        tasks = tasks,
        activity = config.activity,
        difficulty = config.difficulty,
        isAiGenerated = isAiGenerated
    )
}
