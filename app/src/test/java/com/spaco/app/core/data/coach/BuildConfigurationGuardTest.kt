package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The artifact-level half of the spec's opt-in rule: the app must expose no
 * build-time coach configuration, so no build input can put an endpoint, a model,
 * or a credential into a distributed APK.
 *
 * The check is deliberately mechanical. Documentation asked people not to publish
 * a configured build; this fails the suite instead if the ability to configure one
 * at build time ever comes back.
 */
class BuildConfigurationGuardTest {

    @Test
    fun `no build-time coach configuration is exposed`() {
        val buildConfig = runCatching { Class.forName("com.spaco.app.BuildConfig") }.getOrNull()
        val exposed = buildConfig?.declaredFields?.map { it.name }.orEmpty()

        assertThat(exposed.filter { it.startsWith("LLM_") }).isEmpty()
    }

    @Test
    fun `the app build script carries no coach configuration fields`() {
        val buildScript = appBuildScript()

        assertThat(buildScript).doesNotContain("LLM_API_KEY")
        assertThat(buildScript).doesNotContain("LLM_BASE_URL")
        assertThat(buildScript).doesNotContain("LLM_MODEL")
        assertThat(buildScript).doesNotContain("buildConfigField")
    }

    /** Finds `app/build.gradle.kts` from wherever the test runner started. */
    private fun appBuildScript(): String {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            val candidate = File(directory, "app/build.gradle.kts")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        throw AssertionError("app/build.gradle.kts was not found from ${System.getProperty("user.dir")}")
    }
}
