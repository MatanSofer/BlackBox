package com.blackbox.domain.query

import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.ParsedQuery
import com.blackbox.domain.model.query.QueryResult
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Main orchestrator for natural language query processing.
 *
 * Pipeline:
 * 1. **Preprocess** — normalize whitespace, detect language
 * 2. **Parse time** — extract time range via [TimeExpressionParser]
 * 3. **Classify intent** — determine query type via [IntentClassifier]
 * 4. **Extract entities** — identify places, activities, apps via [EntityExtractor]
 * 5. **Build & execute** — query the database via [QueryBuilder]
 * 6. **Generate response** — format results via [ResponseGenerator]
 *
 * @property timeExpressionParser Parses time expressions from the query.
 * @property intentClassifier Classifies the query intent.
 * @property entityExtractor Extracts named entities from the query.
 * @property queryBuilder Builds and executes database queries.
 * @property responseGenerator Formats results into human-readable text.
 * @property logger Logger for pipeline events.
 */
class QueryEngine(
    private val timeExpressionParser: TimeExpressionParser,
    private val intentClassifier: IntentClassifier,
    private val entityExtractor: EntityExtractor,
    private val queryBuilder: QueryBuilder,
    private val responseGenerator: ResponseGenerator,
    private val logger: BlackBoxLogger,
) {

    /**
     * Processes a raw natural language query end-to-end.
     *
     * @param rawQuery The raw query text as entered by the user.
     * @return A [QueryResult] containing the parsed query, response, and data.
     */
    suspend fun process(rawQuery: String): QueryResult {
        logger.d(TAG, "Processing query: \"$rawQuery\"")

        // Step 1: Preprocess
        val normalizedText = preprocess(rawQuery)
        val language = detectLanguage(rawQuery)
        logger.d(TAG, "Language detected: $language")

        // Step 2: Parse time expressions
        val timeRange = timeExpressionParser.parse(normalizedText, language)
        logger.d(TAG, "Time range: ${timeRange.startEpochMs}..${timeRange.endEpochMs}")

        // Step 3: Classify intent
        val intent = intentClassifier.classify(normalizedText, language)
        logger.d(TAG, "Intent classified: $intent")

        // Step 4: Extract entities
        val entities = entityExtractor.extract(normalizedText, language)
        logger.d(TAG, "Entities extracted: ${entities.size}")

        // Assemble parsed query
        val parsedQuery = ParsedQuery(
            originalText = rawQuery,
            normalizedText = normalizedText,
            language = language,
            intent = intent,
            timeRange = timeRange,
            entities = entities,
        )

        // Step 5: Build and execute database queries
        val data = queryBuilder.execute(parsedQuery)
        logger.d(TAG, "Query executed: ${data.records.size} records, ${data.locations.size} locations")

        // Step 6: Generate response
        val response = responseGenerator.generate(parsedQuery, data)
        logger.d(TAG, "Response generated (confidence=${response.confidence})")

        return QueryResult(
            parsedQuery = parsedQuery,
            responseText = response.text,
            data = data.records,
            suggestedFollowUps = response.suggestedFollowUps,
            confidence = response.confidence,
            sourceCount = response.sourceCount,
        )
    }

    /**
     * Parses a raw query into a [ParsedQuery] without executing it.
     *
     * Useful for previewing how the query will be interpreted
     * before committing to a database query.
     *
     * @param rawQuery The raw query text.
     * @return The parsed representation of the query.
     */
    fun parse(rawQuery: String): ParsedQuery {
        val normalizedText = preprocess(rawQuery)
        val language = detectLanguage(rawQuery)
        val timeRange = timeExpressionParser.parse(normalizedText, language)
        val intent = intentClassifier.classify(normalizedText, language)
        val entities = entityExtractor.extract(normalizedText, language)

        return ParsedQuery(
            originalText = rawQuery,
            normalizedText = normalizedText,
            language = language,
            intent = intent,
            timeRange = timeRange,
            entities = entities,
        )
    }

    /**
     * Normalizes query text: trims, collapses whitespace, lowercases.
     */
    private fun preprocess(text: String): String {
        return text
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    /**
     * Detects query language by checking for Hebrew Unicode characters.
     */
    private fun detectLanguage(text: String): Language {
        val hebrewCount = text.count { it in '\u0590'..'\u05FF' }
        return if (hebrewCount > 0) Language.HEBREW else Language.ENGLISH
    }

    companion object {
        private const val TAG = "QueryEngine"
    }
}
