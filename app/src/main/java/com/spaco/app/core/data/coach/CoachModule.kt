package com.spaco.app.core.data.coach

import com.spaco.app.core.domain.coach.CoachAgent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the coach port to its MVP implementation.
 *
 * Swapping in a future LLM-backed coach is a one-line change here, which is the
 * property the `ai-coach` capability requires: nothing else knows which
 * implementation is behind the port.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoachModule {

    @Provides
    @Singleton
    fun provideCoachAgent(): CoachAgent =
        TimeoutCoachAgent(delegate = DeterministicCoachAgent())
}
