package com.spaco.app.core.data.work

import android.content.Context
import androidx.work.WorkManager
import com.spaco.app.core.data.prefs.DataStoreReminderPreferences
import com.spaco.app.core.domain.repository.ReminderPreferences
import com.spaco.app.core.domain.repository.ReviewScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    @Singleton
    abstract fun bindReviewScheduler(impl: ReviewReminderScheduler): ReviewScheduler

    @Binds
    @Singleton
    abstract fun bindReminderPreferences(impl: DataStoreReminderPreferences): ReminderPreferences

    @Binds
    @Singleton
    abstract fun bindReviewNotifier(impl: SystemReviewNotifier): ReviewNotifier

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
