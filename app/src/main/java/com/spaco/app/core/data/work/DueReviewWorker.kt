package com.spaco.app.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Evaluates whether any practice items are due and, if so, reminds the learner.
 *
 * All the decisions live in [DueReviewChecker]; this is the WorkManager shell
 * that survives restarts and reboots.
 */
@HiltWorker
class DueReviewWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val checker: DueReviewChecker,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        checker.check()
        Result.success()
    } catch (throwable: Throwable) {
        // A transient failure should be retried rather than silently dropping
        // the reminder.
        Result.retry()
    }
}
