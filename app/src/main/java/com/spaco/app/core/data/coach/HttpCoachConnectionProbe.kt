package com.spaco.app.core.data.coach

import com.spaco.app.core.domain.coach.CoachConnectionProbe
import com.spaco.app.core.domain.coach.CoachConnectionResult
import com.spaco.app.core.domain.model.CoachConfiguration
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Checks a saved configuration with one bounded request to the endpoint's
 * chat-completions URL.
 *
 * The request carries no practice content — a single fixed user message — so a
 * connectivity check never sends learner material anywhere. Its only purpose is
 * to distinguish a working endpoint from one that rejects the credential
 * ([CoachConnectionResult.AUTHENTICATION_FAILED]) or cannot be used at all
 * ([CoachConnectionResult.UNREACHABLE]).
 */
class HttpCoachConnectionProbe(
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MS,
    private val overallTimeout: Duration = DEFAULT_OVERALL_TIMEOUT,
) : CoachConnectionProbe {

    override suspend fun check(configuration: CoachConfiguration): CoachConnectionResult = try {
        withTimeoutOrNull(overallTimeout.toMillis()) {
            withContext(Dispatchers.IO) { probe(configuration) }
        } ?: CoachConnectionResult.UNREACHABLE
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        // The exception text is deliberately discarded: it can carry parts of the
        // request, and the caller only needs the coarse outcome.
        CoachConnectionResult.UNREACHABLE
    }

    private fun probe(configuration: CoachConfiguration): CoachConnectionResult {
        val body = buildJsonObject {
            put("model", configuration.model)
            put("temperature", 0)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", PROBE_MESSAGE)
                })
            })
        }.toString()

        val connection =
            (URL(configuration.endpoint.trimEnd('/') + CHAT_COMPLETIONS_PATH).openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                configuration.credential?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            when (connection.responseCode) {
                in 200..299 -> CoachConnectionResult.SUCCESS
                401, 403 -> CoachConnectionResult.AUTHENTICATION_FAILED
                else -> CoachConnectionResult.UNREACHABLE
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 3_000
        const val DEFAULT_READ_TIMEOUT_MS = 5_000

        /** Kept in step with [LlmCoachAgent.CHAT_COMPLETIONS_PATH]. */
        const val CHAT_COMPLETIONS_PATH = LlmCoachAgent.CHAT_COMPLETIONS_PATH

        val DEFAULT_OVERALL_TIMEOUT: Duration = Duration.ofSeconds(6)

        /** Fixed, content-free probe: nothing the learner has written is sent. */
        const val PROBE_MESSAGE = "Reply with the single word: ok"
    }
}
