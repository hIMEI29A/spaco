package com.spaco.app.core.data.coach

/**
 * Connection settings for the remote coach, supplied per machine at build time.
 *
 * All three values are required: a configuration with a missing part is treated
 * as "no remote coach" rather than as a half-working one, so a partially filled
 * `local.properties` can never send practice content to an endpoint without an
 * Authorization value (or with the wrong one).
 */
data class LlmCoachConfig(
    val baseUrl: String,
    val model: String,
    val bearer: String,
) {
    /**
     * The OpenAI-compatible chat-completions endpoint for this base URL. The
     * base URL is the server root, so a trailing slash is tolerated and `/v1`
     * is appended rather than expected.
     */
    val chatCompletionsUrl: String
        get() = baseUrl.trimEnd('/') + CHAT_COMPLETIONS_PATH

    companion object {
        const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"

        /** Returns `null` unless all three settings are present and non-blank. */
        fun from(baseUrl: String?, model: String?, bearer: String?): LlmCoachConfig? {
            val cleanBaseUrl = baseUrl?.trim().orEmpty()
            val cleanModel = model?.trim().orEmpty()
            val cleanBearer = bearer?.trim().orEmpty()

            if (cleanBaseUrl.isEmpty() || cleanModel.isEmpty() || cleanBearer.isEmpty()) return null

            return LlmCoachConfig(
                baseUrl = cleanBaseUrl,
                model = cleanModel,
                bearer = cleanBearer,
            )
        }
    }
}
