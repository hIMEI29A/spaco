package com.spaco.app.core.data.security

import android.content.Context
import com.spaco.app.core.domain.model.CoachConfiguration
import com.spaco.app.core.domain.repository.CoachConfigurationStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the learner's coach settings in a private file, sealed by
 * [CoachCredentialCipher], and publishes them as a flow so the running app
 * follows changes without a restart.
 *
 * The file lives in the app's own storage rather than in the shared preferences
 * store, which keeps a single, obvious thing to exclude from backup. It is read
 * lazily on first use — never on the main thread at construction — and any
 * failure to open it is treated as "no configuration": a wiped key, a corrupted
 * file, or a restored backup must degrade to the offline coach, not crash.
 */
@Singleton
class EncryptedCoachConfigurationStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: CoachCredentialCipher,
) : CoachConfigurationStore {

    private val state = MutableStateFlow<CoachConfiguration?>(null)
    private val loadMutex = Mutex()

    @Volatile
    private var loaded = false

    override val configuration: Flow<CoachConfiguration?> = flow {
        ensureLoaded()
        emitAll(state)
    }

    override suspend fun save(configuration: CoachConfiguration) {
        withContext(Dispatchers.IO) { write(configuration) }
        loaded = true
        state.value = configuration
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            val file = configurationFile()
            if (file.exists() && !file.delete()) {
                throw IOException("the stored coach configuration could not be removed")
            }
        }
        loaded = true
        state.value = null
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            state.value = read()
            loaded = true
        }
    }

    private fun read(): CoachConfiguration? {
        val file = configurationFile()
        if (!file.isFile) return null

        // A failure here means the payload cannot be opened by this device any
        // more (missing or rotated key, tampering, truncation). That is "no
        // configuration", not an error the learner has to resolve.
        val plain = runCatching { cipher.decrypt(file.readBytes()) }.getOrNull() ?: return null
        return CoachConfigurationCodec.decode(String(plain, Charsets.UTF_8))
    }

    private fun write(configuration: CoachConfiguration) {
        val file = configurationFile()
        val sealed = cipher.encrypt(
            CoachConfigurationCodec.encode(configuration).toByteArray(Charsets.UTF_8),
        )

        // Write beside the target and move it into place, so a failure part-way
        // through cannot leave a half-written configuration behind.
        val pending = File(file.parentFile, file.name + ".pending")
        FileOutputStream(pending).use { output ->
            output.write(sealed)
            output.flush()
            output.fd.sync()
        }
        if (!pending.renameTo(file)) {
            pending.delete()
            throw IOException("the coach configuration could not be stored")
        }
    }

    private fun configurationFile(): File = File(context.filesDir, FILE_NAME)

    companion object {
        /** Kept in sync with the backup rules that exclude this file. */
        internal const val FILE_NAME = "coach_configuration.enc"
    }
}
