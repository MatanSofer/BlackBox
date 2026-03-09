package com.blackbox.domain.usecase.insight

import com.blackbox.domain.service.AiClient
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Generates natural language observations about the user's week using an LLM.
 *
 * Builds a compact context block from [InsightsBrief] and sends it to [AiClient].
 * The model is instructed to produce 3–5 short, specific, pattern-focused sentences.
 *
 * When the network is unavailable or the API call fails, callers should fall back
 * to [generateLocalObservations] which applies simple rules to produce a few
 * basic but always-available observations without internet.
 *
 * @property aiClient AI completion client (e.g. OpenAI gpt-4o-mini).
 * @property logger Logger for diagnostics.
 */
class GenerateInsightObservationsUseCase(
    private val aiClient: AiClient,
    private val logger: BlackBoxLogger,
) {

    /**
     * Calls the LLM with the user's weekly brief and returns parsed observations.
     *
     * @param brief The [InsightsBrief] computed by [GetInsightsBriefUseCase].
     * @return [Result] with a list of observation strings, or a failure.
     */
    suspend operator fun invoke(brief: InsightsBrief): Result<List<String>> = runCatching {
        logger.d(TAG, "Requesting AI observations")
        val context = buildContext(brief)
        val response = aiClient.complete(SYSTEM_PROMPT, context).getOrThrow()
        parseObservations(response).also {
            logger.d(TAG, "AI returned ${it.size} observations")
        }
    }.also { result ->
        if (result.isFailure) {
            logger.w(TAG, "AI observations failed: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Rule-based fallback observations computed entirely offline.
     *
     * Produces up to 5 observations using simple threshold comparisons.
     * Always call this when [invoke] returns a failure.
     *
     * @param brief The [InsightsBrief] to analyse.
     * @return List of plain-text observation strings.
     */
    fun generateLocalObservations(brief: InsightsBrief): List<String> {
        val obs = mutableListOf<String>()

        // Steps vs goal
        if (brief.avgDailySteps > 0) {
            when {
                brief.avgDailySteps >= 10_000 ->
                    obs.add("You're averaging ${brief.avgDailySteps.formatK()} steps/day — above the 10k target.")
                brief.avgDailySteps >= 7_000 ->
                    obs.add("Averaging ${brief.avgDailySteps.formatK()} steps/day — close to the 10k goal.")
                else ->
                    obs.add("Averaging ${brief.avgDailySteps.formatK()} steps/day this week.")
            }
        }

        // Screen time
        if (brief.avgDailyScreenMinutes > 0) {
            obs.add("Average screen time is ${formatMinutes(brief.avgDailyScreenMinutes)} per day.")
        }

        // Today vs average
        if (brief.avgDailySteps > 0 && brief.todaySteps > 0) {
            val pct = ((brief.todayStepsVsAvg - 1f) * 100).toInt()
            when {
                pct >= 20 -> obs.add("Today you're ${pct}% more active than your weekly average.")
                pct <= -20 -> obs.add("Today is a slower day — ${-pct}% below your weekly average.")
            }
        }

        // Best day
        brief.bestStepDay?.let { best ->
            obs.add("Best day this week: ${best.date.takeLast(5)} with ${best.steps.formatK()} steps.")
        }

        // Top place
        brief.weekTopPlaces.firstOrNull()?.let { place ->
            val days = place.visitCount
            obs.add("Most visited place: ${place.address} · $days day${if (days != 1) "s" else ""}.")
        }

        // Top app
        brief.weekTopApps.firstOrNull()?.let { app ->
            obs.add("Most used app: ${app.displayName} · ${formatMinutes(app.totalMinutes.toInt())} this week.")
        }

        return obs.take(5)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildContext(brief: InsightsBrief): String = buildString {
        appendLine("User's last 7 days of sensor data:")
        appendLine()
        appendLine("TODAY:")
        appendLine("  Steps: ${brief.todaySteps} (personal avg: ${brief.avgDailySteps}/day)")
        appendLine("  Screen time: ${formatMinutes(brief.todayScreenMinutes)}")
        appendLine("  Places visited: ${brief.todayPlacesCount}")
        brief.todayTopApp?.let { appendLine("  Top app today: ${it.displayName}") }
        appendLine()
        appendLine("7-DAY STEP TREND:")
        brief.weekStepTrend.forEach { appendLine("  ${it.date}: ${it.steps} steps") }
        appendLine("  Weekly avg: ${brief.avgDailySteps} steps/day")
        appendLine()
        appendLine("7-DAY SCREEN TIME TREND:")
        brief.weekScreenTrend.forEach {
            appendLine("  ${it.date}: ${formatMinutes(it.totalMinutes)} (${it.pickupCount} pickups)")
        }
        appendLine("  Weekly avg: ${formatMinutes(brief.avgDailyScreenMinutes)}/day")
        if (brief.weekTopPlaces.isNotEmpty()) {
            appendLine()
            appendLine("TOP PLACES THIS WEEK:")
            brief.weekTopPlaces.forEach { appendLine("  ${it.address}: ${it.visitCount} day(s)") }
        }
        if (brief.weekTopApps.isNotEmpty()) {
            appendLine()
            appendLine("TOP APPS THIS WEEK:")
            brief.weekTopApps.forEach {
                appendLine("  ${it.displayName}: ${formatMinutes(it.totalMinutes.toInt())}")
            }
        }
    }

    private fun parseObservations(raw: String): List<String> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                // Strip leading bullets / numbering: "1.", "•", "-", "*"
                line.replace(Regex("^[•\\-*]\\s*"), "")
                    .replace(Regex("^\\d+[.)\\s]+"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(5)

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    companion object {
        private const val TAG = "GenerateInsightObservationsUseCase"

        private const val SYSTEM_PROMPT = """You are the intelligence layer of BlackBox — a personal life recorder app.

You receive 7 days of the user's sensor data: steps, screen time, app usage, locations visited.

Your task: generate exactly 3 to 5 SHORT, specific, human-friendly observations about the user's patterns.

RULES:
1. Each observation is ONE sentence, maximum 12 words. Be specific — use real numbers.
2. Focus on patterns, comparisons, and anomalies. Not just raw numbers.
3. Write directly: "You walk more on weekdays than weekends." not "It seems like..."
4. Include at least one observation about physical activity and one about phone habits.
5. Output ONLY the observations — one per line, no numbering, no bullets, no extra text.
6. Be neutral and factual. Never preachy or judgmental."""
    }
}

private fun Int.formatK(): String = if (this >= 1000) "${this / 1000}k" else this.toString()
