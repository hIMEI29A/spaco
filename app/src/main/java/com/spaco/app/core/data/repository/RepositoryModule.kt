package com.spaco.app.core.data.repository

import com.spaco.app.core.data.prefs.DataStoreActiveDeckStore
import com.spaco.app.core.data.repository.ProgressRepositoryImpl
import com.spaco.app.core.data.security.EncryptedCoachConfigurationStore
import com.spaco.app.core.domain.repository.ActiveDeckStore
import com.spaco.app.core.domain.repository.CoachConfigurationStore
import com.spaco.app.core.domain.repository.DeckRepository
import com.spaco.app.core.domain.repository.ItemRepository
import com.spaco.app.core.domain.repository.ProgressRepository
import com.spaco.app.core.domain.repository.ReviewRepository
import com.spaco.app.core.domain.repository.SessionRepository
import com.spaco.app.core.domain.repository.SkillRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds each repository port to its Room-backed implementation. Features depend
 * on the interfaces, so an implementation can be swapped here alone.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSkillRepository(impl: SkillRepositoryImpl): SkillRepository

    @Binds
    @Singleton
    abstract fun bindDeckRepository(impl: DeckRepositoryImpl): DeckRepository

    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindActiveDeckStore(impl: DataStoreActiveDeckStore): ActiveDeckStore

    @Binds
    @Singleton
    abstract fun bindCoachConfigurationStore(
        impl: EncryptedCoachConfigurationStore,
    ): CoachConfigurationStore

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository
}
