package com.spaco.app.core.data.security

import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.model.CoachConfiguration
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The on-device half of the stored credential's guarantees: the platform
 * keystore really seals the payload, tampering is detected, and losing the key
 * degrades to "no configuration" instead of crashing or handing over something
 * half-open.
 *
 * These need a real Android keystore, which is why they run as instrumented
 * tests rather than on the JVM. Plain JVM assertions about file handling live
 * in `EncryptedCoachConfigurationStoreTest`.
 */
class KeystoreCredentialCipherTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var alias: String
    private lateinit var cipher: KeystoreCredentialCipher
    private lateinit var file: File

    @Before
    fun setUp() {
        alias = "spaco.test.cipher.${System.nanoTime()}"
        cipher = KeystoreCredentialCipher(alias)
        file = File(context.filesDir, EncryptedCoachConfigurationStore.FILE_NAME)
        file.delete()
    }

    @After
    fun tearDown() {
        keyStore().deleteEntry(alias)
        file.delete()
    }

    @Test
    fun a_sealed_payload_opens_again() {
        val plain = "the payload that must survive a round trip".toByteArray()

        val sealed = cipher.encrypt(plain)

        assertThat(cipher.decrypt(sealed)).isEqualTo(plain)
    }

    @Test
    fun the_same_plaintext_seals_differently_each_time() {
        val plain = "repeatable input".toByteArray()

        val first = cipher.encrypt(plain)
        val second = cipher.encrypt(plain)

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun a_tampered_payload_is_rejected() {
        val sealed = cipher.encrypt("tamper me".toByteArray())
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 0x01).toByte()

        val failure = runCatching { cipher.decrypt(sealed) }

        assertThat(failure.isFailure).isTrue()
    }

    @Test
    fun stored_bytes_do_not_contain_the_configuration_in_readable_form() = runBlocking {
        val configuration = CoachConfiguration(
            endpoint = "https://api.keystore-test.example",
            model = "gpt-4o-mini",
            credential = "test-bearer-value",
        )
        val store = EncryptedCoachConfigurationStore(context, cipher)

        store.save(configuration)

        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertThat(raw).doesNotContain("test-bearer-value")
        assertThat(raw).doesNotContain(configuration.endpoint)
        assertThat(raw).doesNotContain(configuration.model)
    }

    @Test
    fun a_saved_configuration_survives_a_new_store_instance() = runBlocking {
        val configuration = CoachConfiguration(
            endpoint = "https://api.keystore-test.example",
            model = "gpt-4o-mini",
            credential = "test-bearer-value",
        )

        EncryptedCoachConfigurationStore(context, cipher).save(configuration)

        assertThat(EncryptedCoachConfigurationStore(context, cipher).configuration.first())
            .isEqualTo(configuration)
    }

    @Test
    fun losing_the_key_leaves_the_app_without_a_configuration() = runBlocking {
        val configuration = CoachConfiguration(
            endpoint = "https://api.keystore-test.example",
            model = "gpt-4o-mini",
            credential = "test-bearer-value",
        )
        EncryptedCoachConfigurationStore(context, cipher).save(configuration)

        // What a restore onto a clean install looks like: the file is there, the
        // key that sealed it is not.
        keyStore().deleteEntry(alias)

        assertThat(EncryptedCoachConfigurationStore(context, cipher).configuration.first()).isNull()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
