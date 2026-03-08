package com.blackbox.domain.usecase.query

import com.blackbox.data.query.QueryContextFormatter
import com.blackbox.domain.model.query.QueryResult
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.service.AiClient
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Enriches a local [QueryResult] with an AI-generated natural language response.
 *
 * Unlike the local query engine, which fetches only records matching the
 * classified intent (e.g. location-only for a location query), this use case
 * fetches **all** record types in the parsed time range and sends the full
 * picture to the AI. This lets the model cross-reference activity, app usage,
 * connectivity, and sensor data to give richer, more accurate answers.
 *
 * Steps:
 * 1. Fetch all records in the parsed time range from [recordRepository].
 * 2. Format [QueryResult] + all records into a compact context block.
 * 3. Send query + context to [AiClient].
 * 4. Return the model's reply.
 *
 * If [AiClient] has no API key or the network call fails, the [Result] is a
 * failure and the caller falls back to the local rule-based response.
 *
 * @property aiClient Platform AI completion client.
 * @property contextFormatter Formats [QueryResult] + records into LLM-readable text.
 * @property recordRepository Source for broad record fetches across all collector types.
 * @property logger Logger for diagnostic output.
 */
class ProcessQueryWithAiUseCase(
    private val aiClient: AiClient,
    private val contextFormatter: QueryContextFormatter,
    private val recordRepository: RecordRepository,
    private val logger: BlackBoxLogger,
) {
    companion object {
        private const val TAG = "ProcessQueryWithAiUseCase"

        private const val SYSTEM_PROMPT = """You are BlackBox AI — the intelligence layer of a personal life recorder app.

The app passively captures sensor metadata from the user's Android smartphone:
• GPS/location — where the user was, how long they stayed, movement speed
• Physical activity — walking, running, still, in-vehicle (with step counts)
• WiFi environment — connected network, nearby SSIDs, signal strength
• App usage — which apps were in the foreground and for how long
• Screen state — on/off/unlocked events and brightness
• Audio levels — ambient decibel level and noise classification
• Battery — charge level, charging state, temperature
• Connectivity — cellular type, Bluetooth devices, VPN
• Barometer — atmospheric pressure and relative altitude changes
• Light sensor — ambient lux level
• Call log — call type (incoming/outgoing/missed) and duration
• Media playback — music/audio playing, output device (speaker/headset/BT)

You receive the user's question AND structured sensor data from the local database.
Your task is to answer naturally and helpfully, like a knowledgeable personal assistant.

IMPORTANT RULES:
1. Cross-reference ALL data types — location + activity + app usage together often reveal much more than any single type alone.
2. Be specific: use real times, durations, counts, and observations from the data.
3. Translate raw sensor data into human terms: "you were stationary at one location for 2 hours" not "LAT=31.7 LNG=35.2 for 120 min".
4. Handle multi-part questions fully — if the user asks "where was I and what was I doing", answer both parts.
5. If data is sparse or missing for a period, say so clearly ("no location data was recorded during that window").
6. Answer in the SAME LANGUAGE as the user's question (English or Hebrew).
7. Keep answers focused and readable — 3–6 sentences, or use short bullet points for complex multi-part answers.
8. Do NOT fabricate data. If you are uncertain, say so."""
    }

    /**
     * Generates an AI response for [rawQuery] using [localResult] as the starting context,
     * supplemented by ALL records in the query's time range.
     *
     * @param rawQuery The user's original natural language query.
     * @param localResult The result already produced by the local query engine.
     * @return [Result] containing the AI response string, or a failure with
     *   a descriptive message (network error, no API key, rate limit, etc.).
     */
    suspend operator fun invoke(rawQuery: String, localResult: QueryResult): Result<String> {
        logger.d(TAG, "Requesting AI enrichment for: $rawQuery")
        return runCatching {
            // Fetch ALL collector types in the time range — not just the intent-filtered subset.
            val allRecords = recordRepository.getRecordsInRange(
                startTime = localResult.parsedQuery.timeRange.startEpochMs,
                endTime = localResult.parsedQuery.timeRange.endEpochMs,
            )
            logger.d(TAG, "AI context: ${allRecords.size} total records (local engine had ${localResult.data.size})")

            val context = contextFormatter.format(localResult, allRecords)
            val userMessage = "$rawQuery\n\n$context"
            aiClient.complete(SYSTEM_PROMPT, userMessage).getOrThrow()
        }.also { result ->
            if (result.isSuccess) logger.d(TAG, "AI response received (${result.getOrNull()?.length} chars)")
            else logger.w(TAG, "AI enrichment failed: ${result.exceptionOrNull()?.message}")
        }
    }
}
