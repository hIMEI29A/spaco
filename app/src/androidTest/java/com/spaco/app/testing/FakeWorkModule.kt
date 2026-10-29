package com.spaco.app.testing

import com.spaco.app.core.data.work.ReviewNotifier
import com.spaco.app.core.domain.repository.ReminderPreferences
import com.spaco.app.core.domain.repository.ReviewScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Replaces the WorkManager-backed scheduling module in instrumented tests.
 * `HiltTestApplication` does not configure WorkManager, so tests bind inert
 * stand-ins and never touch it.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [com.spaco.app.core.data.work.WorkModule::class])
abstract class FakeWorkModule {

    @Binds
    @Singleton
    abstract fun bindReviewScheduler(impl: NoOpReviewScheduler): ReviewScheduler

    @Binds
    @Singleton
    abstract fun bindReminderPreferences(impl: InMemoryReminderPreferences): ReminderPreferences

    @Binds
    @Singleton
    abstract fun bindReviewNotifier(impl: NoOpReviewNotifier): ReviewNotifier
}

@Singleton
class NoOpReviewScheduler @Inject constructor() : ReviewScheduler {
    override fun schedulePeriodic() = Unit
    override fun scheduleNextDueCheck(earliestDueAt: Instant?) = Unit
    override fun cancelAll() = Unit
}

@Singleton
class InMemoryReminderPreferences @Inject constructor() : ReminderPreferences {
    private val enabled = MutableStateFlow(true)
    override val remindersEnabled: Flow<Boolean> = enabled
    override suspend fun setRemindersEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}

@Singleton
class NoOpReviewNotifier @Inject constructor() : ReviewNotifier {
    override fun isPermissionGranted(): Boolean = true
    override fun ensureChannel() = Unit
    override fun notifyDue(count: Int, deckId: String?) = Unit
    override fun cancel() = Unit
}
