package com.spaco.app.core.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.model.CoachConfiguration
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the store's file handling and state on the JVM, with a reversible
 * test cipher standing in for the platform keystore.
 *
 * The real cipher's properties — that the stored bytes are not readable and that
 * a key loss degrades to "no configuration" — are covered by
 * `KeystoreCredentialCipherTest` on a device, where a keystore exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EncryptedCoachConfigurationStoreTest {

    private lateinit var context: Context
    private lateinit var file: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        file = File(context.filesDir, EncryptedCoachConfigurationStore.FILE_NAME)
        file.delete()
    }

    @Test
    fun `an install with nothing saved has no configuration`() = runTest {
        assertThat(store().configuration.first()).isNull()
    }

    @Test
    fun `a saved configuration is read back by a new store instance`() = runTest {
        val saved = configuration(credential = "test-bearer-value")
        store().save(saved)

        assertThat(store().configuration.first()).isEqualTo(saved)
    }

    @Test
    fun `a configuration without a credential round-trips`() = runTest {
        val saved = configuration(credential = null)
        store().save(saved)

        val read = store().configuration.first()
        assertThat(read).isEqualTo(saved)
        assertThat(read?.credential).isNull()
    }

    @Test
    fun `a saved configuration replaces the previous one`() = runTest {
        val store = store()
        store.save(configuration(credential = "first-value"))
        val replacement = CoachConfiguration(
            endpoint = "http://127.0.0.1:11435",
            model = "qwen2.5:3b",
            credential = "second-value",
        )

        store.save(replacement)

        assertThat(store.configuration.first()).isEqualTo(replacement)
        assertThat(store().configuration.first()).isEqualTo(replacement)
    }

    @Test
    fun `clearing removes the configuration and the stored file`() = runTest {
        val store = store()
        store.save(configuration(credential = "test-bearer-value"))

        store.clear()

        assertThat(store.configuration.first()).isNull()
        assertThat(file.exists()).isFalse()
        assertThat(store().configuration.first()).isNull()
    }

    @Test
    fun `a payload that cannot be opened is reported as no configuration`() = runTest {
        val store = store()
        store.save(configuration(credential = "test-bearer-value"))
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertThat(store().configuration.first()).isNull()
    }

    @Test
    fun `a payload that is not a configuration is reported as no configuration`() = runTest {
        val store = store()
        store.save(configuration(credential = "test-bearer-value"))
        file.writeBytes(ShiftCipher.encrypt("""{"model":"only-a-model"}""".toByteArray()))

        assertThat(store().configuration.first()).isNull()
    }

    @Test
    fun `the flow reports later changes`() = runTest {
        val store = store()
        val observed = mutableListOf<CoachConfiguration?>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.configuration.collect { observed.add(it) }
        }

        store.save(configuration(credential = "test-bearer-value"))
        store.clear()

        collecting.cancel()
        assertThat(observed.first()).isNull()
        assertThat(observed.last()).isNull()
        assertThat(observed).contains(configuration(credential = "test-bearer-value"))
    }

    private fun configuration(credential: String?) = CoachConfiguration(
        endpoint = "https://api.example.test",
        model = "gpt-4o-mini",
        credential = credential,
    )

    private fun store() = EncryptedCoachConfigurationStore(context, ShiftCipher)

    /** Reversible, deterministic, and deliberately not secret — a test double only. */
    private object ShiftCipher : CoachCredentialCipher {
        override fun encrypt(plain: ByteArray): ByteArray =
            plain.map { (it + 1).toByte() }.toByteArray()

        override fun decrypt(sealed: ByteArray): ByteArray =
            sealed.map { (it - 1).toByte() }.toByteArray()
    }
}
