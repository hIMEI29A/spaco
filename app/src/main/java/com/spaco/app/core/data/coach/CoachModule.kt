package com.spaco.app.core.data.coach

import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachConnectionProbe
import com.spaco.app.core.domain.repository.CoachConfigurationStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton

/**
 * Binds the coach port to its implementation.
 *
 * Nothing outside this file knows whether coaching is remote: the session,
 * review recording, and scheduling go through the port either way. The remote
 * coach is now selected at request time from the configuration the learner saved
 * on the device ([CoachConfigurationStore]) — nothing is compiled in — and the
 * built-in offline coach answers whenever no configuration is saved or the
 * configured endpoint has no usable answer.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoachModule {

    /**
     * Bounds the whole chain, including the remote request and the fallback that
     * follows it. It is longer than [LlmCoachAgent.DEFAULT_OVERALL_TIMEOUT] on
     * purpose: the inner bound ends the remote attempt, this one guarantees a
     * session cannot wait indefinitely if a delegate ever misbehaves.
     */
    val COACH_TIMEOUT: Duration = Duration.ofSeconds(8)

    @Provides
    @Singleton
    fun provideCoachAgent(configurationStore: CoachConfigurationStore): CoachAgent =
        TimeoutCoachAgent(
            delegate = RuntimeConfiguredCoachAgent(
                configurationStore = configurationStore,
                offline = DeterministicCoachAgent(),
                remoteFactory = { configuration -> LlmCoachAgent(configuration) },
            ),
            timeout = COACH_TIMEOUT,
        )

    @Provides
    @Singleton
    fun provideCoachConnectionProbe(): CoachConnectionProbe = HttpCoachConnectionProbe()
}
