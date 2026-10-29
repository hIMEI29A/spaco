package com.spaco.app.core.data.work

import com.spaco.app.core.data.db.ReminderDao
import com.spaco.app.core.data.db.ReminderStateEntity
import com.spaco.app.core.domain.repository.ItemRepository
import com.spaco.app.core.domain.repository.ReminderPreferences
import com.spaco.app.core.domain.repository.ReviewScheduler
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** What one due-review check concluded. */
enum class DueReviewOutcome {
    NOTHING_DUE,
    NOTIFIED,
    DUPLICATE_SUPPRESSED,
    REMINDERS_DISABLED,
    PERMISSION_DENIED,
}

/**
 * The logic behind [DueReviewWorker], kept separate from WorkManager so it can
 * be tested directly.
 *
 * It always refreshes the next one-time check, and posts a notification only
 * when every guard passes: items are due, reminders are on, permission is
 * granted, and the due set differs from the last one notified.
 */
@Singleton
class DueReviewChecker @Inject constructor(
    private val itemRepository: ItemRepository,
    private val reminderPreferences: ReminderPreferences,
    private val reminderDao: ReminderDao,
    private val notifier: ReviewNotifier,
    private val scheduler: ReviewScheduler,
    private val clock: Clock,
) {

    suspend fun check(): DueReviewOutcome {
        val now = clock.instant()
        val due = itemRepository.getAllDueItems(now)

        // Reschedule for the next time something becomes due. A time that is not
        // in the future would re-run immediately, so it is treated as "no
        // pending check" — the periodic sweep still covers it.
        val earliest = itemRepository.getEarliestNextDueAt()?.takeIf { it.isAfter(now) }
        scheduler.scheduleNextDueCheck(earliest)

        if (due.isEmpty()) return DueReviewOutcome.NOTHING_DUE

        if (!reminderPreferences.remindersEnabled.first()) {
            return DueReviewOutcome.REMINDERS_DISABLED
        }
        if (!notifier.isPermissionGranted()) {
            return DueReviewOutcome.PERMISSION_DENIED
        }

        val signature = due.map { it.id }.sorted().joinToString(",")
        val lastNotified = reminderDao.getById(REMINDER_ID)?.lastNotifiedDueSignature
        if (signature == lastNotified) return DueReviewOutcome.DUPLICATE_SUPPRESSED

        notifier.notifyDue(count = due.size, deckId = due.first().deckId)
        reminderDao.upsert(
            ReminderStateEntity(
                id = REMINDER_ID,
                lastNotifiedDueSignature = signature,
                lastNotifiedAt = now.toEpochMilli(),
            ),
        )
        return DueReviewOutcome.NOTIFIED
    }

    companion object {
        const val REMINDER_ID = "reminders"
    }
}
