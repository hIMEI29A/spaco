package com.spaco.app.core.domain.repository

import com.spaco.app.core.domain.model.CoachConfiguration
import kotlinx.coroutines.flow.Flow

/**
 * Where the device keeps the coach settings the learner enters.
 *
 * This is the only source of coach configuration: nothing is compiled into the
 * app, so `null` — meaning "no remote coach configured" — is the state of a
 * fresh install and of one whose settings were cleared. A store implementation
 * is responsible for keeping the credential unreadable outside the app.
 */
interface CoachConfigurationStore {

    /** The current configuration, or `null` when the learner has saved none. */
    val configuration: Flow<CoachConfiguration?>

    /** Saves [configuration], replacing any previously saved one. */
    suspend fun save(configuration: CoachConfiguration)

    /** Removes the saved configuration, returning the app to the offline coach. */
    suspend fun clear()
}
