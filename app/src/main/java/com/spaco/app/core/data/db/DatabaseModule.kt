package com.spaco.app.core.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the single Room database and its DAOs. Version 1 has no migrations
 * yet; later schema changes add explicit `Migration`s here rather than relying
 * on destructive fallback.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "spaco.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpaCoDatabase =
        Room.databaseBuilder(context, SpaCoDatabase::class.java, DATABASE_NAME)
            .build()

    @Provides
    fun provideSkillDao(database: SpaCoDatabase): SkillDao = database.skillDao()

    @Provides
    fun provideDeckDao(database: SpaCoDatabase): DeckDao = database.deckDao()

    @Provides
    fun provideItemDao(database: SpaCoDatabase): ItemDao = database.itemDao()

    @Provides
    fun provideReviewDao(database: SpaCoDatabase): ReviewDao = database.reviewDao()

    @Provides
    fun provideSessionDao(database: SpaCoDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideReminderDao(database: SpaCoDatabase): ReminderDao = database.reminderDao()
}
