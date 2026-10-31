package com.spaco.app.core.data.security

/**
 * Seals and opens the serialized coach configuration.
 *
 * It is an interface so the store's file handling and state can be tested on the
 * JVM, where no platform keystore exists, while the shipped implementation
 * ([KeystoreCredentialCipher]) uses the Android Keystore.
 */
interface CoachCredentialCipher {

    /** Returns the sealed form of [plain]. */
    fun encrypt(plain: ByteArray): ByteArray

    /**
     * Returns the plain form of [sealed].
     *
     * @throws Exception when [sealed] cannot be opened — a different key, a
     * corrupted payload, or a key that no longer exists. Callers treat that as
     * "no configuration".
     */
    fun decrypt(sealed: ByteArray): ByteArray
}
