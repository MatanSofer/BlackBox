package com.blackbox.domain.service

/**
 * Abstraction over any remote AI completion API.
 *
 * The shared domain layer depends on this interface. Platform modules
 * provide concrete implementations (e.g. OpenAI gpt-4o-mini on Android,
 * a no-op stub when no API key is configured).
 */
interface AiClient {
    /**
     * Sends a chat completion request and returns the assistant's reply.
     *
     * @param systemPrompt Instruction that frames the model's role and capabilities.
     * @param userMessage  The user's question plus any retrieved context.
     * @return [Result] wrapping the assistant reply string on success, or an
     *   exception (network error, HTTP error, missing key) on failure.
     */
    suspend fun complete(systemPrompt: String, userMessage: String): Result<String>
}
