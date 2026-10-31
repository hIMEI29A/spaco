package com.spaco.app.core.data.coach

import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation
import com.spaco.app.core.domain.model.CoachConfiguration
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The remote coach: asks an OpenAI-compatible chat-completions endpoint to make
 * the three coaching decisions the [CoachAgent] port defines.
 *
 * Every outcome that is not a usable, on-shape answer — an unreachable endpoint,
 * an error status, a body the model wrapped in prose, an answer about a
 * different item, or simply a slow response — becomes `null`, the port's "no
 * opinion". Callers already treat that as the normal path, so a failing model
 * degrades to stored content instead of interrupting a session.
 *
 * The credential, when the configuration has one, travels only in the
 * `Authorization` header; it is never logged, embedded in a message, or sent
 * anywhere else. An endpoint that needs no credential is supported: no
 * authorization value is sent at all.
 */
class LlmCoachAgent(
    private val config: CoachConfiguration,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MS,
    /** Bound on the whole call, including parsing; see design D6. */
    private val overallTimeout: Duration = DEFAULT_OVERALL_TIMEOUT,
    /** Where failures go. The single place they are surfaced, and never with the credential. */
    private val onFailure: (Throwable) -> Unit = {},
) : CoachAgent {

    override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? =
        call(SELECTION_INSTRUCTIONS, selectionPayload(request)) { content ->
            val target = extractJsonObject(content)?.stringOrNull("targetItemId")
            if (target.isNullOrBlank()) null else CoachSelection(targetItemId = target)
        }

    override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? =
        call(EXERCISE_INSTRUCTIONS, exercisePayload(request)) { content ->
            val obj = extractJsonObject(content) ?: return@call null
            val target = obj.stringOrNull("targetItemId")
            val prompt = obj.stringOrNull("prompt")
            val expectedAnswer = obj.stringOrNull("expectedAnswer")
            if (target != request.item.id || prompt.isNullOrBlank() || expectedAnswer.isNullOrBlank()) {
                return@call null
            }
            GeneratedExercise(
                targetItemId = target,
                prompt = prompt,
                expectedAnswer = expectedAnswer,
                explanation = obj.stringOrNull("explanation"),
                distractors = obj.stringList("distractors"),
            )
        }

    override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? =
        call(EXPLANATION_INSTRUCTIONS, explanationPayload(request)) { content ->
            val obj = extractJsonObject(content) ?: return@call null
            val target = obj.stringOrNull("targetItemId")
            val text = obj.stringOrNull("text")
            if (target != request.item.id || text.isNullOrBlank()) {
                return@call null
            }
            GeneratedExplanation(targetItemId = target, text = text)
        }

    /**
     * Sends one chat-completions request and maps its answer, turning every
     * failure into `null`.
     */
    private suspend fun <T> call(
        instructions: String,
        payload: JsonObject,
        parse: ((String) -> T?),
    ): T? = try {
        withTimeoutOrNull(overallTimeout.toMillis()) {
            withContext(Dispatchers.IO) {
                val content = postChatCompletion(instructions, payload)
                if (content == null) null else parse(content)
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        onFailure(failure)
        null
    }

    private fun postChatCompletion(instructions: String, payload: JsonObject): String? {
        val body = buildJsonObject {
            put("model", config.model)
            put("temperature", 0)
            put("messages", buildJsonArray {
                add(message("system", instructions))
                add(message("user", payload.toString()))
            })
        }.toString()

        val connection = openConnection().apply {
            setRequestProperty("Content-Type", "application/json")
            config.credential?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                // No response body text is echoed: it can contain anything the
                // endpoint sends back, including request details.
                throw IOException("Coach endpoint answered with status $status")
            }

            parseChatCompletion(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens the chat-completions connection. The endpoint is the server root, so
     * a trailing slash is tolerated and the path is appended.
     */
    private fun openConnection(): HttpURLConnection =
        (URL(config.endpoint.trimEnd('/') + CHAT_COMPLETIONS_PATH).openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
            }


    private fun message(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    /** Pulls the assistant's message text out of a chat-completions response. */
    private fun parseChatCompletion(responseBody: String): String? {
        val root = runCatching { JSON.parseToJsonElement(responseBody) as? JsonObject }.getOrNull()
        val choices = root?.get("choices")?.let { runCatching { it.jsonArray }.getOrNull() }
        val first = choices?.firstOrNull()?.let { runCatching { it.jsonObject }.getOrNull() }
        val content = first?.get("message")?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.get("content")
            ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        return content?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.stringOrNull(field: String): String? =
        get(field)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    private fun JsonObject.stringList(field: String): List<String> =
        get(field)?.let { element ->
            runCatching { element.jsonArray.mapNotNull { it.jsonPrimitive.content } }.getOrNull()
        }.orEmpty()

    private fun selectionPayload(request: CoachSelectionRequest): JsonObject = buildJsonObject {
        put("task", "select-next-item")
        put("deckId", request.deckId)
        put("now", request.now.toString())
        put("candidates", buildJsonArray {
            request.candidates.forEach { candidate ->
                add(buildJsonObject {
                    put("itemId", candidate.itemId)
                    put("prompt", candidate.prompt)
                    put("expectedAnswer", candidate.expectedAnswer)
                    put("learningState", candidate.learningState.name)
                    put("nextDueAt", candidate.nextDueAt.toString())
                })
            }
        })
        put("recentReviewOutcomes", buildJsonArray {
            request.recentEvents.forEach { event ->
                add(buildJsonObject {
                    put("itemId", event.itemId)
                    put("correct", event.correct)
                })
            }
        })
        put("excludedItemIds", buildJsonArray {
            request.excludedItemIds.forEach { add(JsonPrimitive(it)) }
        })
    }

    private fun exercisePayload(request: ExerciseRequest): JsonObject = buildJsonObject {
        put("task", "generate-exercise")
        put("itemId", request.item.id)
        put("prompt", request.item.prompt)
        put("expectedAnswer", request.item.expectedAnswer)
        request.item.explanation?.let { put("storedExplanation", it) }
        put("siblingAnswers", buildJsonArray {
            request.siblings
                .filter { it.id != request.item.id }
                .map { it.expectedAnswer.trim() }
                .filter { it.isNotEmpty() && it != request.item.expectedAnswer.trim() }
                .distinct()
                .forEach { add(JsonPrimitive(it)) }
        })
    }

    private fun explanationPayload(request: ExplanationRequest): JsonObject = buildJsonObject {
        put("task", "explain-answer")
        put("itemId", request.item.id)
        put("prompt", request.item.prompt)
        put("expectedAnswer", request.item.expectedAnswer)
        put("grade", request.grade.name)
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 3_000
        const val DEFAULT_READ_TIMEOUT_MS = 5_000

        /** Appended to the configured endpoint, which is the server root. */
        const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"

        /** Longer than the transport timeouts, so it bounds the whole call. */
        val DEFAULT_OVERALL_TIMEOUT: Duration = Duration.ofSeconds(6)

        private val JSON = Json { ignoreUnknownKeys = true }

        private const val SHARED_RULES = """
Reply with a single JSON object and nothing else: no prose, no markdown, no code fences.
Do not add fields that were not requested. Keep every string in the language of the item's text.
""" 

        private val SELECTION_INSTRUCTIONS = """
You are choosing what a learner should practise next.
Pick exactly one of the supplied candidates: the one that will help the learner most right now.
Candidates that appear in excludedItemIds must not be chosen.
$SHARED_RULES
Required fields: targetItemId (string, exactly one of the candidate itemIds).
""".trimIndent()

        private val EXERCISE_INSTRUCTIONS = """
You are writing one practice exercise for the item identified by itemId.
Use siblingAnswers only as material for wrong answers; never as the expected answer.
Keep the exercise answerable from the item's own content and attributable to that item.
$SHARED_RULES
Required fields: targetItemId (string, exactly the given itemId), prompt (string),
expectedAnswer (string). Optional fields: explanation (string), distractors (array of strings).
""".trimIndent()

        private val EXPLANATION_INSTRUCTIONS = """
You are explaining to a learner why the expected answer is the right one, given the grade.
Be brief and concrete; do not introduce new material.
$SHARED_RULES
Required fields: targetItemId (string, exactly the given itemId), text (string).
""".trimIndent()

        /**
         * Reads the first complete JSON object out of a model's answer, tolerating
         * a fenced code block or surrounding prose, since support for strict JSON
         * output varies across OpenAI-compatible servers.
         */
        fun extractJsonObject(content: String): JsonObject? {
            val text = content.trim()
            val start = text.indexOf('{')
            if (start < 0) return null

            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until text.length) {
                val char = text[index]
                when {
                    escaped -> escaped = false
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    inString -> Unit
                    char == '{' -> depth += 1
                    char == '}' -> {
                        depth -= 1
                        if (depth == 0) {
                            return runCatching {
                                JSON.parseToJsonElement(text.substring(start, index + 1)) as? JsonObject
                            }.getOrNull()
                        }
                    }
                }
            }
            return null
        }
    }
}
