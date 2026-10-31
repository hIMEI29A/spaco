package com.spaco.app.core.domain.coach

import com.spaco.app.core.domain.model.CoachConfiguration

/** What a connectivity check against a configured endpoint found. */
enum class CoachConnectionResult {
    /** The endpoint answered successfully. */
    SUCCESS,

    /** The endpoint answered, but rejected the credential. */
    AUTHENTICATION_FAILED,

    /** The endpoint could not be reached, or answered in a way that is not usable. */
    UNREACHABLE,
}

/**
 * Checks a configuration before the learner relies on it: one bounded request
 * that sends no practice content and reports whether the endpoint answers and
 * whether it accepts the credential.
 *
 * This exists so a wrong credential or a mistyped endpoint surfaces where it was
 * entered, instead of quietly degrading every coach request to the offline coach.
 */
interface CoachConnectionProbe {

    suspend fun check(configuration: CoachConfiguration): CoachConnectionResult
}
