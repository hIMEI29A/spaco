package com.spaco.app.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals coach configuration with a 256-bit AES key that lives in the Android
 * Keystore and never leaves the device: the app can ask the Keystore to encrypt
 * and decrypt, but cannot read the key itself, and the key is not included in
 * backups or copied to another device.
 *
 * The initialization vector is stored in front of the ciphertext, and the GCM
 * tag makes tampering with either part a decryption failure.
 */
class KeystoreCredentialCipher(
    private val alias: String = DEFAULT_ALIAS,
) : CoachCredentialCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "sealed payload is too short to hold an IV" }
        val iv = sealed.copyOfRange(0, IV_BYTES)
        val payload = sealed.copyOfRange(IV_BYTES, sealed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(payload)
    }

    /** Returns the app's wrapping key, creating it on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        /** Keystore entry name; changing it makes previously stored data unreadable. */
        const val DEFAULT_ALIAS = "spaco.coach.configuration"

        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
