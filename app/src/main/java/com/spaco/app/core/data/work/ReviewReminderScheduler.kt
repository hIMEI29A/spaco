package com.spaco.app.core.data.work

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the two pieces of background work that keep due reviews visible:
 *
 * - a periodic sweep, so even without an exact next-due time the app checks
 *   roughly twice a day;
 * - a one-time check delayed to the earliest next-due time, so the learner is
 *   not notified late.
 *
 * Both are unique named work, so re-scheduling does not pile up requests.
 */
@Singleton
class ReviewReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val clock: Clock,
) : com.spaco.app.core.domain.repository.ReviewScheduler {

    private val noNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .build()

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<DueReviewWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(noNetwork)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WorkNames.PERIODIC_DUE_SWEEP,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedules (or replaces) the one-time check for [earliestDueAt]. A `null`
     * time means nothing is pending, so the check is cancelled.
     */
    override fun scheduleNextDueCheck(earliestDueAt: Instant?) {
        if (earliestDueAt == null) {
            workManager.cancelUniqueWork(WorkNames.NEXT_DUE_CHECK)
            return
        }

        val delay = Duration.between(clock.instant(), earliestDueAt)
        val initialDelayMillis = delay.toMillis().coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<DueReviewWorker>()
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(noNetwork)
            .build()

        workManager.enqueueUniqueWork(WorkNames.NEXT_DUE_CHECK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork(WorkNames.PERIODIC_DUE_SWEEP)
        workManager.cancelUniqueWork(WorkNames.NEXT_DUE_CHECK)
    }

    companion object {
        const val PERIODIC_INTERVAL_HOURS = 12L
    }
}
