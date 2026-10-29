package com.spaco.app.core.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * `core/domain` is the pure heart of the app: it must not depend on Android, so
 * it can be unit tested on the JVM and so the coach port stays replaceable.
 * This test enforces that boundary rather than trusting review to catch it.
 */
class DomainPurityTest {

    private val domainDir = File("src/main/java/com/spaco/app/core/domain")

    @Test
    fun `domain sources are present`() {
        assertThat(domainDir.isDirectory).isTrue()
        assertThat(domainDir.walkTopDown().count { it.isFile && it.extension == "kt" })
            .isGreaterThan(0)
    }

    @Test
    fun `no domain source imports android`() {
        val offenders = domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) -> line.trimStart().startsWith("import android") }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }
            .toList()

        assertThat(offenders).isEmpty()
    }
}
