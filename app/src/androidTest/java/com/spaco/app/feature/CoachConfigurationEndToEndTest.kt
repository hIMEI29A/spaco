package com.spaco.app.feature

import android.security.NetworkSecurityPolicy
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.model.CoachConfiguration
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.repository.CoachConfigurationStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import java.util.Collections
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The on-device half of the spec's opt-in rule, exercised through the real Hilt
 * graph and the real keystore-backed store: an install with nothing saved serves
 * a session from the built-in coach and sends no request; a saved credential-less
 * endpoint serves it remotely; and clearing the configuration returns to the
 * built-in coach in the same run, with no restart.
 *
 * The endpoint is a real loopback HTTP server, so the request that leaves the
 * app and the answer that comes back are real. Nothing here depends on the
 * network beyond loopback.
 */
@HiltAndroidTest
class CoachConfigurationEndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var store: CoachConfigurationStore

    @Inject
    lateinit var coachAgent: CoachAgent

    private lateinit var endpoint: LoopbackEndpoint

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        store.clear()
        endpoint = LoopbackEndpoint()
    }

    @After
    fun tearDown() {
        endpoint.close()
    }

    @Test
    fun unconfigured_install_serves_locally_and_makes_no_request() = runBlocking {
        val exercise = coachAgent.generateExercise(exerciseRequest())

        assertThat(exercise).isNotNull()
        assertThat(endpoint.requests).isEmpty()
    }

    @Test
    fun cleartext_is_permitted_only_for_local_endpoints() {
        val policy = NetworkSecurityPolicy.getInstance()

        assertThat(policy.isCleartextTrafficPermitted("127.0.0.1")).isTrue()
        assertThat(policy.isCleartextTrafficPermitted("localhost")).isTrue()
        assertThat(policy.isCleartextTrafficPermitted("10.0.2.2")).isTrue()
        assertThat(policy.isCleartextTrafficPermitted("api.example.com")).isFalse()
    }

    @Test
    fun a_saved_endpoint_serves_remotely_and_clearing_returns_to_the_built_in_coach() =
        runBlocking {
            val offlinePrompt = coachAgent.generateExercise(exerciseRequest())!!.prompt
            assertThat(endpoint.requests).isEmpty()

            endpoint.respondWith(REMOTE_EXERCISE)
            store.save(
                CoachConfiguration(
                    endpoint = "http://127.0.0.1:${endpoint.port}",
                    model = "test-model",
                ),
            )

            val remote = coachAgent.generateExercise(exerciseRequest())
            assertThat(remote?.prompt).isEqualTo("remote prompt")
            assertThat(endpoint.requests).hasSize(1)
            // Credential-less endpoint: no authorization value is sent.
            assertThat(endpoint.requests.single().authorization).isNull()

            store.clear()
            val afterClear = coachAgent.generateExercise(exerciseRequest())
            assertThat(afterClear?.prompt).isEqualTo(offlinePrompt)
            // Clearing sent nothing over the network.
            assertThat(endpoint.requests).hasSize(1)
        }

    @Test
    fun a_stored_credential_travels_only_in_the_authorization_header() = runBlocking {
        endpoint.respondWith(REMOTE_EXERCISE)
        store.save(
            CoachConfiguration(
                endpoint = "http://127.0.0.1:${endpoint.port}",
                model = "test-model",
                credential = CREDENTIAL,
            ),
        )

        coachAgent.generateExercise(exerciseRequest())

        val request = endpoint.requests.single()
        assertThat(request.authorization).isEqualTo("Bearer $CREDENTIAL")
        assertThat(request.body).doesNotContain(CREDENTIAL)
    }

    private fun exerciseRequest() = ExerciseRequest(item = item(), seed = 1L)

    private fun item() = PracticeItem(
        id = "item-a",
        deckId = "deck-1",
        prompt = "What is the capital of France?",
        expectedAnswer = "Paris",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        schedule = ItemSchedule(
            nextDueAt = Instant.parse("2026-01-01T00:00:00Z"),
            state = LearningState.NEW,
            intervalIndex = 0,
            correctStreak = 0,
        ),
    )

    private companion object {
        const val CREDENTIAL = "test-bearer-value"

        /** The assistant content the endpoint will return, as `LlmCoachAgent` expects it. */
        val REMOTE_EXERCISE =
            """{"targetItemId":"item-a","prompt":"remote prompt","expectedAnswer":"remote answer"}"""
    }

    /**
     * A one-shot-at-a-time HTTP server on loopback that records what it received
     * and answers with a fixed chat-completions envelope. `com.sun.net.httpserver`
     * is not on Android, so this is a small hand-rolled server over a [ServerSocket].
     */
    private class LoopbackEndpoint : AutoCloseable {

        data class Recorded(
            val path: String,
            val body: String,
            val authorization: String?,
        )

        private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))

        val requests: MutableList<Recorded> = Collections.synchronizedList(mutableListOf())

        val port: Int get() = server.localPort

        @Volatile
        private var assistantContent: String = "{}"

        private val worker = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { handle(it) }
                } catch (closed: Exception) {
                    if (server.isClosed) return@Thread
                    throw closed
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        fun respondWith(assistantContent: String) {
            this.assistantContent = assistantContent
        }

        private fun handle(socket: Socket) {
            val input = socket.getInputStream()
            val requestLine = readLine(input)
            if (requestLine.isEmpty()) return
            val path = requestLine.split(' ').getOrElse(1) { "" }

            var contentLength = 0
            var authorization: String? = null
            while (true) {
                val header = readLine(input)
                if (header.isEmpty()) break
                val name = header.substringBefore(':').trim()
                val value = header.substringAfter(':', "").trim()
                when {
                    name.equals("Content-Length", ignoreCase = true) ->
                        contentLength = value.toIntOrNull() ?: 0
                    name.equals("Authorization", ignoreCase = true) -> authorization = value
                }
            }
            val body = String(readExactly(input, contentLength), Charsets.UTF_8)
            requests += Recorded(path = path, body = body, authorization = authorization)

            val envelope =
                """{"choices":[{"message":{"content":${JsonPrimitive(assistantContent)}}}]}"""
            val bytes = envelope.toByteArray(Charsets.UTF_8)
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(header.toByteArray(Charsets.UTF_8))
                write(bytes)
                flush()
            }
        }

        private fun readLine(input: InputStream): String {
            val line = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte == -1 || byte == '\n'.code) break
                if (byte != '\r'.code) line.append(byte.toChar())
            }
            return line.toString()
        }

        private fun readExactly(input: InputStream, length: Int): ByteArray {
            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(bytes, read, length - read)
                if (count < 0) break
                read += count
            }
            return bytes
        }

        override fun close() {
            server.close()
            worker.join(1_000)
        }
    }
}
