package com.spaco.app.core.data.security

import com.spaco.app.core.domain.model.CoachConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Turns a [CoachConfiguration] into the text that gets encrypted, and back.
 *
 * The credential is serialized only when it is present, and the same
 * all-or-nothing rule the domain model applies is re-applied on the way in, so
 * a corrupted or hand-edited payload cannot produce a half-configured coach.
 */
internal object CoachConfigurationCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(configuration: CoachConfiguration): String = buildJsonObject {
        put("endpoint", configuration.endpoint)
        put("model", configuration.model)
        configuration.credential?.let { put("credential", it) }
    }.toString()

    fun decode(text: String): CoachConfiguration? = runCatching {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return null
        CoachConfiguration.from(
            endpoint = root.stringOrNull("endpoint"),
            model = root.stringOrNull("model"),
            credential = root.stringOrNull("credential"),
        )
    }.getOrNull()

    private fun JsonObject.stringOrNull(field: String): String? =
        get(field)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
}
