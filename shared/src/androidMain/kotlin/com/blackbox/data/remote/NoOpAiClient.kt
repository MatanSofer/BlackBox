package com.blackbox.data.remote

import com.blackbox.domain.service.AiClient

/**
 * Fallback [AiClient] used when no API key is configured.
 *
 * Always returns a [Result.failure] so the caller falls back gracefully
 * to the local query engine response.
 */
class NoOpAiClient : AiClient {
    override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> =
        Result.failure(IllegalStateException("No API key configured"))
}
