package com.blackbox.data.remote

import com.blackbox.domain.service.AiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * [AiClient] implementation that calls the OpenAI Chat Completions API.
 *
 * Uses [HttpURLConnection] (built-in Android SDK) — no extra dependencies needed.
 * JSON serialization uses the `kotlinx.serialization` library already in the project.
 *
 * @property apiKey OpenAI API key (sourced from BuildConfig.OPENAI_API_KEY).
 * @property model  OpenAI model name (defaults to gpt-4o-mini for cost efficiency).
 */
class OpenAiClientImpl(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
) : AiClient {

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_TOKENS = 512
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Sends a chat completion request and returns the assistant message content.
     *
     * @param systemPrompt Role/behaviour instruction for the model.
     * @param userMessage  User question plus retrieved context block.
     */
    override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildRequestBody(systemPrompt, userMessage)
                val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    throw IllegalStateException("OpenAI API error $code: $error")
                }

                val responseText = conn.inputStream.bufferedReader().readText()
                val response = json.decodeFromString<ChatResponse>(responseText)
                response.choices.firstOrNull()?.message?.content
                    ?: throw IllegalStateException("Empty response from OpenAI")
            }
        }

    private fun buildRequestBody(systemPrompt: String, userMessage: String): String {
        val requestJson = JsonObject(
            mapOf(
                "model" to JsonPrimitive(model),
                "max_tokens" to JsonPrimitive(MAX_TOKENS),
                "messages" to JsonArray(
                    listOf(
                        JsonObject(mapOf("role" to JsonPrimitive("system"), "content" to JsonPrimitive(systemPrompt))),
                        JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive(userMessage))),
                    ),
                ),
            ),
        )
        return requestJson.toString()
    }

    // ── Response DTOs ──────────────────────────────────────────────────────────

    @Serializable
    private data class ChatResponse(val choices: List<Choice>)

    @Serializable
    private data class Choice(val message: ChoiceMessage)

    @Serializable
    private data class ChoiceMessage(
        @SerialName("content") val content: String,
    )
}
