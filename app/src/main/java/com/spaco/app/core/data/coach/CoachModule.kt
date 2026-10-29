package com.spaco.app.core.data.coach

import com.spaco.app.BuildConfig
import com.spaco.app.core.domain.coach.CoachAgent
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
 * review recording, and scheduling go through the port either way. When the
 * build configures a remote coach (see `local.properties`), the chain is
 * remote-first with the offline coach as the fallback; otherwise the offline
 * coach is the only implementation, exactly as before.
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
    fun provideCoachAgent(): CoachAgent {
        val config = LlmCoachConfig.from(
            baseUrl = BuildConfig.LLM_BASE_URL,
            model = BuildConfig.LLM_MODEL,
            bearer = BuildConfig.LLM_API_KEY,
        )

        return TimeoutCoachAgent(
            delegate = if (config == null) {
                DeterministicCoachAgent()
            } else {
                FailoverCoachAgent(
                    primary = LlmCoachAgent(config),
                    fallback = DeterministicCoachAgent(),
                )
            },
            timeout = COACH_TIMEOUT,
        )
    }
}
