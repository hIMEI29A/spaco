package com.spaco.app.core.domain.model

/**
 * The coach settings a learner supplies on the device: where to send coach
 * requests, which model to ask for, and — optionally — the credential that
 * endpoint expects.
 *
 * A credential is optional because an endpoint on the local network commonly
 * needs none. An endpoint and a model are both required: a configuration that
 * cannot name a target is not a configuration, and the caller treats `null` as
 * "no remote coach" rather than as a half-working one.
 */
data class CoachConfiguration(
    val endpoint: String,
    val model: String,
    val credential: String? = null,
) {
    companion object {
        /**
         * Builds a configuration from learner input, trimming each value.
         *
         * Returns `null` unless an endpoint and a model are both present and
         * non-blank. A blank credential is stored as `null`, never as an empty
         * authorization value.
         */
        fun from(endpoint: String?, model: String?, credential: String?): CoachConfiguration? {
            val cleanEndpoint = endpoint?.trim().orEmpty()
            val cleanModel = model?.trim().orEmpty()
            if (cleanEndpoint.isEmpty() || cleanModel.isEmpty()) return null

            val cleanCredential = credential?.trim().orEmpty()

            return CoachConfiguration(
                endpoint = cleanEndpoint,
                model = cleanModel,
                credential = cleanCredential.ifEmpty { null },
            )
        }
    }
}
