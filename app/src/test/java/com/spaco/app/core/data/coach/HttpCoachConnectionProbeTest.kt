package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.coach.CoachConnectionResult
import com.spaco.app.core.domain.model.CoachConfiguration
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/**
 * Drives the connectivity probe against a real, in-process HTTP server, so the
 * status mapping and the wire request are exercised for real — no network access
 * and no credentials are involved.
 */
class HttpCoachConnectionProbeTest {

    private val endpoint = FakeEndpoint()
    private val probe = HttpCoachConnectionProbe()

    @After
    fun tearDown() {
        endpoint.close()
    }

    @Test
    fun `an answer in the success range reports success`() = runBlocking<Unit> {
        endpoint.respondWith(status = 200)

        val result = probe.check(endpoint.config)

        assertThat(result).isEqualTo(CoachConnectionResult.SUCCESS)
    }

    @Test
    fun `a 401 reports a rejected credential`() = runBlocking<Unit> {
        endpoint.respondWith(status = 401)

        val result = probe.check(endpoint.config)

        assertThat(result).isEqualTo(CoachConnectionResult.AUTHENTICATION_FAILED)
        assertThat(result.toString()).doesNotContain(CREDENTIAL)
    }

    @Test
    fun `a 403 reports a rejected credential`() = runBlocking<Unit> {
        endpoint.respondWith(status = 403)

        assertThat(probe.check(endpoint.config)).isEqualTo(CoachConnectionResult.AUTHENTICATION_FAILED)
    }

    @Test
    fun `a server error reports unreachable`() = runBlocking<Unit> {
        endpoint.respondWith(status = 500)

        assertThat(probe.check(endpoint.config)).isEqualTo(CoachConnectionResult.UNREACHABLE)
    }

    @Test
    fun `an endpoint nothing listens on reports unreachable`() = runBlocking<Unit> {
        val dead = CoachConfiguration(endpoint = "http://127.0.0.1:1", model = "test-model")

        assertThat(probe.check(dead)).isEqualTo(CoachConnectionResult.UNREACHABLE)
    }

    @Test
    fun `the probe sends no practice content and never puts the credential in the body`() =
        runBlocking<Unit> {
            endpoint.respondWith(status = 200)

            probe.check(endpoint.config)

            val request = endpoint.requests.single()
            assertThat(request.body).doesNotContain(CREDENTIAL)
            assertThat(request.body).contains(HttpCoachConnectionProbe.PROBE_MESSAGE)
            assertThat(request.authorization).isEqualTo("Bearer $CREDENTIAL")
        }

    @Test
    fun `an endpoint without a credential is probed with no authorization value`() =
        runBlocking<Unit> {
            endpoint.respondWith(status = 200)

            probe.check(endpoint.config.copy(credential = null))

            assertThat(endpoint.requests.single().authorization).isNull()
        }

    private class FakeEndpoint : AutoCloseable {

        data class RecordedRequest(
            val path: String,
            val body: String,
            val headers: Map<String, List<String>>,
        ) {
            val authorization: String? get() = headers["Authorization"]?.firstOrNull()
        }

        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val requests = mutableListOf<RecordedRequest>()
        private var status: Int = 200

        val config: CoachConfiguration
            get() = CoachConfiguration(
                endpoint = "http://127.0.0.1:${server.address.port}",
                model = "test-model",
                credential = CREDENTIAL,
            )

        init {
            server.createContext("/") { exchange ->
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                requests += RecordedRequest(
                    path = exchange.requestURI.path,
                    body = body,
                    headers = exchange.requestHeaders.mapValues { it.value.toList() },
                )
                val bytes = "{}".toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.executor = null
            server.start()
        }

        fun respondWith(status: Int) {
            this.status = status
        }

        override fun close() {
            server.stop(0)
        }
    }

    private companion object {
        const val CREDENTIAL = "test-bearer-value"
    }
}
