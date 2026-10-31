package com.spaco.app.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoachConfigurationTest {

    @Test
    fun `an endpoint and a model are enough`() {
        val config = CoachConfiguration.from(
            endpoint = "http://127.0.0.1:11434",
            model = "qwen2.5:3b",
            credential = null,
        )

        assertThat(config).isEqualTo(
            CoachConfiguration(endpoint = "http://127.0.0.1:11434", model = "qwen2.5:3b"),
        )
        assertThat(config?.credential).isNull()
    }

    @Test
    fun `a credential is kept when supplied`() {
        val config = CoachConfiguration.from(
            endpoint = "https://api.example.test",
            model = "gpt-4o-mini",
            credential = "test-bearer-value",
        )

        assertThat(config?.credential).isEqualTo("test-bearer-value")
    }

    @Test
    fun `values are trimmed`() {
        val config = CoachConfiguration.from(" https://api.example.test ", " model ", " credential ")

        assertThat(config).isEqualTo(
            CoachConfiguration(
                endpoint = "https://api.example.test",
                model = "model",
                credential = "credential",
            ),
        )
    }

    @Test
    fun `a blank credential is stored as absent`() {
        assertThat(CoachConfiguration.from("https://api.example.test", "model", "   ")?.credential)
            .isNull()
    }

    @Test
    fun `a blank or missing endpoint is rejected`() {
        assertThat(CoachConfiguration.from("   ", "model", "credential")).isNull()
        assertThat(CoachConfiguration.from(null, "model", "credential")).isNull()
    }

    @Test
    fun `a blank or missing model is rejected`() {
        assertThat(CoachConfiguration.from("https://api.example.test", "", "credential")).isNull()
        assertThat(CoachConfiguration.from("https://api.example.test", null, "credential")).isNull()
    }

    @Test
    fun `an empty input is not a configuration`() {
        assertThat(CoachConfiguration.from(null, null, null)).isNull()
    }
}
