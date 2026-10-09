package com.example.trailquest

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

internal fun getDeterministicFallbackMission(): JSONObject {
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

/**
 * Validates complete mission schema:
 * - non-empty title (String)
 * - actual positive integer durationMinutes (Int > 0)
 * - non-empty missions array (JSONArray containing only non-empty Strings)
 *
 * Strict validation: does not accept singular "mission" field.
 * Returns deterministic fallback when raw JSON is null/malformed or schema is incomplete.
 */
internal fun validateAndResolveMission(rawJson: JSONObject?): MissionValidationResult {
    if (rawJson == null) {
        return MissionValidationResult(
            isValid = false,
            isFallbackApplied = true,
            isMalformed = true,
            statusLabel = "Malformed (Fallback Applied)",
            failureReason = "No valid JSON object parsed from raw generation",
            finalMission = getDeterministicFallbackMission()
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

    if (hasValidTitle && hasValidDuration && hasValidMissions) {
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
        finalMission = getDeterministicFallbackMission()
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
