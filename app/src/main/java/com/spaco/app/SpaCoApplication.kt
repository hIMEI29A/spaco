package com.spaco.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.spaco.app.core.data.work.ReviewNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. The single composition root for the app: Hilt
 * builds the dependency graph from here, which is what makes the AI coach
 * implementation swappable in one binding.
 *
 * It also supplies WorkManager's configuration so `@HiltWorker` classes can be
 * constructed with injected dependencies, and creates the notification channel
 * once so reminders have somewhere to land.
 */
@HiltAndroidApp
class SpaCoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reviewNotifier: ReviewNotifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        reviewNotifier.ensureChannel()
    }
}
