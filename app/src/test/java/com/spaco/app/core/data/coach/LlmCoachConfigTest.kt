package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LlmCoachConfigTest {

    @Test
    fun `fully configured settings are accepted`() {
        val config = LlmCoachConfig.from(
            baseUrl = "https://api.example.test",
            model = "some-model",
            bearer = "test-bearer-value",
        )

        assertThat(config).isEqualTo(
            LlmCoachConfig(
                baseUrl = "https://api.example.test",
                model = "some-model",
                bearer = "test-bearer-value",
            ),
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        val config = LlmCoachConfig.from(" https://api.example.test ", " model ", " bearer ")

        assertThat(config?.baseUrl).isEqualTo("https://api.example.test")
        assertThat(config?.model).isEqualTo("model")
        assertThat(config?.bearer).isEqualTo("bearer")
    }

    @Test
    fun `a blank base url disables the remote coach`() {
        assertThat(LlmCoachConfig.from("   ", "model", "bearer")).isNull()
    }

    @Test
    fun `a blank model disables the remote coach`() {
        assertThat(LlmCoachConfig.from("https://api.example.test", "", "bearer")).isNull()
    }

    @Test
    fun `a blank authorization value disables the remote coach`() {
        assertThat(LlmCoachConfig.from("https://api.example.test", "model", "")).isNull()
    }

    @Test
    fun `a missing setting disables the remote coach`() {
        assertThat(LlmCoachConfig.from(null, null, null)).isNull()
    }

    @Test
    fun `the endpoint is the server root plus the chat completions path`() {
        val config = LlmCoachConfig.from("https://api.example.test", "model", "bearer")

        assertThat(config?.chatCompletionsUrl)
            .isEqualTo("https://api.example.test/v1/chat/completions")
    }

    @Test
    fun `a trailing slash in the base url is tolerated`() {
        val config = LlmCoachConfig.from("https://api.example.test/", "model", "bearer")

        assertThat(config?.chatCompletionsUrl)
            .isEqualTo("https://api.example.test/v1/chat/completions")
    }
}
