package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.coach.CoachCandidate
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.model.CoachConfiguration
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test

/**
 * Drives the adapter against a real, in-process HTTP server, so the request that
 * leaves the device and the answer that comes back are exercised for real — no
 * network access and no credentials are involved.
 */
class LlmCoachAgentTest {

    private val endpoint = FakeEndpoint()
    private val failures = mutableListOf<Throwable>()

    @After
    fun tearDown() {
        endpoint.close()
    }

    // --- extraction ------------------------------------------------------

    @Test
    fun `a plain json object is extracted`() {
        val extracted = LlmCoachAgent.extractJsonObject("""{"a":1}""")

        assertThat(extracted?.get("a")?.jsonPrimitive?.content).isEqualTo("1")
    }

    @Test
    fun `a code fenced object is extracted`() {
        val extracted = LlmCoachAgent.extractJsonObject("```json\n{\"a\":1}\n```")

        assertThat(extracted?.get("a")?.jsonPrimitive?.content).isEqualTo("1")
    }

    @Test
    fun `an object surrounded by prose is extracted`() {
        val extracted = LlmCoachAgent.extractJsonObject("Sure! Here you go: {\"a\":\"}\"} hope that helps")

        assertThat(extracted?.get("a")?.jsonPrimitive?.content).isEqualTo("}")
    }

    @Test
    fun `text without an object yields nothing`() {
        assertThat(LlmCoachAgent.extractJsonObject("I cannot help with that.")).isNull()
    }

    // --- selection -------------------------------------------------------

    @Test
    fun `a selection answer naming a candidate is mapped`() = runBlocking {
        endpoint.respondWith(content = """{"targetItemId":"item-a","rationale":"most overdue"}""")

        val result = agent().selectNextItem(selectionRequest())

        assertThat(result?.targetItemId).isEqualTo("item-a")
        assertThat(endpoint.requests).hasSize(1)
        assertThat(endpoint.requests.single().path).isEqualTo("/v1/chat/completions")
        assertThat(endpoint.requests.single().method).isEqualTo("POST")
    }

    @Test
    fun `a selection answer without an item id is discarded`() = runBlocking {
        endpoint.respondWith(content = """{"rationale":"no idea"}""")

        assertThat(agent().selectNextItem(selectionRequest())).isNull()
    }

    // --- exercise --------------------------------------------------------

    @Test
    fun `an exercise answer for the requested item is mapped`() = runBlocking {
        endpoint.respondWith(
            content = """
            {"targetItemId":"item-a","prompt":"What is 2+2?","expectedAnswer":"4",
             "explanation":"Arithmetic.","distractors":["3","5"]}
            """.trimIndent(),
        )

        val result = agent().generateExercise(exerciseRequest())

        assertThat(result?.prompt).isEqualTo("What is 2+2?")
        assertThat(result?.expectedAnswer).isEqualTo("4")
        assertThat(result?.explanation).isEqualTo("Arithmetic.")
        assertThat(result?.distractors).containsExactly("3", "5").inOrder()
    }

    @Test
    fun `a code fenced answer is still parsed`() = runBlocking {
        endpoint.respondWith(
            content = "```json\n{\"targetItemId\":\"item-a\",\"prompt\":\"p\",\"expectedAnswer\":\"e\"}\n```",
        )

        assertThat(agent().generateExercise(exerciseRequest())?.expectedAnswer).isEqualTo("e")
    }

    @Test
    fun `an answer about a different item is discarded`() = runBlocking {
        endpoint.respondWith(content = """{"targetItemId":"item-b","prompt":"p","expectedAnswer":"e"}""")

        assertThat(agent().generateExercise(exerciseRequest())).isNull()
    }

    @Test
    fun `an answer with an empty expected answer is discarded`() = runBlocking {
        endpoint.respondWith(content = """{"targetItemId":"item-a","prompt":"p","expectedAnswer":"  "}""")

        assertThat(agent().generateExercise(exerciseRequest())).isNull()
    }

    @Test
    fun `an answer with unexpected fields still maps the defined ones`() = runBlocking {
        endpoint.respondWith(
            content = """
            {"targetItemId":"item-a","prompt":"p","expectedAnswer":"e","confidence":0.9,
             "meta":{"provider":"x"}}
            """.trimIndent(),
        )

        assertThat(agent().generateExercise(exerciseRequest())?.prompt).isEqualTo("p")
    }

    // --- explanation -----------------------------------------------------

    @Test
    fun `an explanation answer for the requested item is mapped`() = runBlocking {
        endpoint.respondWith(content = """{"targetItemId":"item-a","text":"Because."}""")

        assertThat(agent().generateExplanation(explanationRequest())?.text).isEqualTo("Because.")
    }

    @Test
    fun `an explanation answer with a blank text is discarded`() = runBlocking {
        endpoint.respondWith(content = """{"targetItemId":"item-a","text":""}""")

        assertThat(agent().generateExplanation(explanationRequest())).isNull()
    }

    // --- failures --------------------------------------------------------

    @Test
    fun `an error status is answered with no opinion`() = runBlocking {
        endpoint.respondWith(status = 500, content = """{"error":"boom"}""")

        assertThat(agent().generateExercise(exerciseRequest())).isNull()
        assertThat(failures).isNotEmpty()
    }

    @Test
    fun `prose without a json object is discarded`() = runBlocking {
        endpoint.respondWith(content = "I am afraid I cannot help with that.")

        assertThat(agent().generateExercise(exerciseRequest())).isNull()
    }

    @Test
    fun `an answer that arrives after the bound is discarded`() = runBlocking {
        endpoint.delayMillis = 2_000
        endpoint.respondWith(content = """{"targetItemId":"item-a","prompt":"p","expectedAnswer":"e"}""")

        val subject = agent(overallTimeout = Duration.ofMillis(300))

        assertThat(subject.generateExercise(exerciseRequest())).isNull()
    }

    @Test
    fun `an unreachable endpoint is answered with no opinion`() = runBlocking {
        val unreachable = LlmCoachAgent(
            config = CoachConfiguration(
                endpoint = "http://127.0.0.1:1",
                model = "test-model",
                credential = "test-bearer-value",
            ),
            onFailure = { failures += it },
        )

        assertThat(unreachable.generateExercise(exerciseRequest())).isNull()
        assertThat(failures).isNotEmpty()
    }

    // --- payload (what leaves the device) --------------------------------

    @Test
    fun `the exercise payload carries the item, its sibling answers, and nothing else`() = runBlocking<Unit> {
        endpoint.respondWith(content = """{"targetItemId":"item-a","prompt":"p","expectedAnswer":"e"}""")

        agent().generateExercise(exerciseRequest(siblings = listOf(sibling("item-b", "5"))))

        val payload = endpoint.requests.single().payload()
        assertThat(payload["task"]?.jsonPrimitive?.content).isEqualTo("generate-exercise")
        assertThat(payload["itemId"]?.jsonPrimitive?.content).isEqualTo("item-a")
        assertThat(payload["prompt"]?.jsonPrimitive?.content).isEqualTo("What is 2+2?")
        assertThat(payload["expectedAnswer"]?.jsonPrimitive?.content).isEqualTo("4")
        assertThat(payload["siblingAnswers"].toString()).isEqualTo("""["5"]""")
        assertThat(payload.keys).containsExactly(
            "task", "itemId", "prompt", "expectedAnswer", "siblingAnswers",
        )
    }

    @Test
    fun `the selection payload carries the candidates and their scheduling state`() = runBlocking<Unit> {
        endpoint.respondWith(content = """{"targetItemId":"item-a"}""")

        agent().selectNextItem(selectionRequest())

        val payload = endpoint.requests.single().payload()
        assertThat(payload["task"]?.jsonPrimitive?.content).isEqualTo("select-next-item")
        assertThat(payload["deckId"]?.jsonPrimitive?.content).isEqualTo("deck-1")
        assertThat(payload["candidates"].toString()).contains("item-a")
        assertThat(payload.keys).containsExactly(
            "task", "deckId", "now", "candidates", "recentReviewOutcomes", "excludedItemIds",
        )
    }

    @Test
    fun `the credential travels only in the authorization header`() = runBlocking<Unit> {
        endpoint.respondWith(content = """{"targetItemId":"item-a"}""")

        agent().selectNextItem(selectionRequest())

        val request = endpoint.requests.single()
        assertThat(request.authorization).isEqualTo("Bearer test-bearer-value")
        assertThat(request.body).doesNotContain("test-bearer-value")
        assertThat(request.body).doesNotContain("Authorization")
    }

    @Test
    fun `failures never carry the credential`() = runBlocking {
        endpoint.respondWith(status = 401, content = """{"error":"invalid"}""")

        assertThat(agent().generateExercise(exerciseRequest())).isNull()

        val diagnostics = failures.flatMap { failure ->
            generateSequence(failure) { it.cause }.map { it.toString() }
        }
        assertThat(diagnostics).isNotEmpty()
        assertThat(diagnostics.joinToString("\n")).doesNotContain("test-bearer-value")
    }

    // --- endpoint and credential shape -----------------------------------

    @Test
    fun `the endpoint is the server root plus the chat completions path`() = runBlocking<Unit> {
        endpoint.respondWith(content = """{"targetItemId":"item-a"}""")

        agent().selectNextItem(selectionRequest())

        assertThat(endpoint.requests.single().path).isEqualTo("/v1/chat/completions")
    }

    @Test
    fun `a trailing slash in the endpoint is tolerated`() = runBlocking<Unit> {
        endpoint.respondWith(content = """{"targetItemId":"item-a"}""")
        val trailing = LlmCoachAgent(
            config = CoachConfiguration(
                endpoint = "${endpoint.config.endpoint}/",
                model = endpoint.config.model,
                credential = "test-bearer-value",
            ),
        )

        trailing.selectNextItem(selectionRequest())

        assertThat(endpoint.requests.single().path).isEqualTo("/v1/chat/completions")
    }

    @Test
    fun `an endpoint without a credential is sent no authorization value`() = runBlocking<Unit> {
        endpoint.respondWith(content = """{"targetItemId":"item-a"}""")
        val noCredential = LlmCoachAgent(
            config = CoachConfiguration(
                endpoint = endpoint.config.endpoint,
                model = endpoint.config.model,
                credential = null,
            ),
        )

        assertThat(noCredential.selectNextItem(selectionRequest())?.targetItemId).isEqualTo("item-a")

        val request = endpoint.requests.single()
        assertThat(request.authorization).isNull()
        assertThat(request.body).doesNotContain("Authorization")
    }

    // --- helpers ---------------------------------------------------------

    private fun agent(overallTimeout: Duration = LlmCoachAgent.DEFAULT_OVERALL_TIMEOUT) =
        LlmCoachAgent(
            config = endpoint.config,
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = 1_000,
            overallTimeout = overallTimeout,
            onFailure = { failures += it },
        )

    private fun item(id: String, expectedAnswer: String) = PracticeItem(
        id = id,
        deckId = "deck-1",
        prompt = "What is 2+2?",
        expectedAnswer = expectedAnswer,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        schedule = ItemSchedule(
            nextDueAt = Instant.parse("2026-01-02T00:00:00Z"),
            state = LearningState.LEARNING,
            intervalIndex = 1,
            correctStreak = 1,
        ),
    )

    private fun sibling(id: String, expectedAnswer: String) = item(id, expectedAnswer)

    private fun selectionRequest() = CoachSelectionRequest(
        deckId = "deck-1",
        candidates = listOf(
            CoachCandidate(
                itemId = "item-a",
                prompt = "What is 2+2?",
                expectedAnswer = "4",
                learningState = LearningState.LEARNING,
                nextDueAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        ),
        now = Instant.parse("2026-01-02T00:00:00Z"),
    )

    private fun exerciseRequest(siblings: List<PracticeItem> = emptyList()) = ExerciseRequest(
        item = item("item-a", "4"),
        siblings = siblings,
        seed = 1L,
    )

    private fun explanationRequest() = ExplanationRequest(
        item = item("item-a", "4"),
        grade = Grade.CORRECT,
    )
}

/** A one-endpoint HTTP server that records what it received. */
private class FakeEndpoint : AutoCloseable {

    data class RecordedRequest(
        val method: String,
        val path: String,
        val body: String,
        val headers: Map<String, List<String>>,
    ) {
        val authorization: String? get() = headers["Authorization"]?.firstOrNull()

        /** The payload the adapter sent: the second message of the request. */
        fun payload() = Json.parseToJsonElement(userMessage(body)).jsonObject
    }

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val requests = mutableListOf<RecordedRequest>()
    var delayMillis: Long = 0
    private var status: Int = 200
    private var content: String = "{}"

    val config: CoachConfiguration
        get() = CoachConfiguration(
            endpoint = "http://127.0.0.1:${server.address.port}",
            model = "test-model",
            credential = "test-bearer-value",
        )

    init {
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            requests += RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                body = body,
                headers = exchange.requestHeaders.mapValues { it.value.toList() },
            )
            if (delayMillis > 0) Thread.sleep(delayMillis)

            val response = """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.executor = null
        server.start()
    }

    fun respondWith(status: Int = 200, content: String) {
        this.status = status
        this.content = content
    }

    override fun close() {
        server.stop(0)
    }

    companion object {
        fun userMessage(body: String): String {
            val messages = Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray
            return messages[1].jsonObject.getValue("content").jsonPrimitive.content
        }
    }
}
