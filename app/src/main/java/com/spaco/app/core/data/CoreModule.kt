package com.spaco.app.core.data

import com.spaco.app.core.domain.scheduling.SchedulingParameters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Application-wide, injectable sources of ambient state. Both are injected so
 * that repositories and the scheduler never reach for a clock or a random
 * directly, which is what keeps them testable.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = UuidIdGenerator()

    @Provides
    @Singleton
    fun provideSchedulingParameters(): SchedulingParameters = SchedulingParameters()
}
