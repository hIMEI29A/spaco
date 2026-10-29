package com.spaco.app.core.data.work

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.data.db.ReminderDao
import com.spaco.app.core.data.db.ReminderStateEntity
import com.spaco.app.core.domain.repository.ReminderPreferences
import com.spaco.app.feature.session.FakeItemRepository
import com.spaco.app.feature.session.FakeReviewScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DueReviewCheckerTest {

    private val now = Instant.parse("2026-07-01T08:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ids = object : IdGenerator {
        private var counter = 0
        override fun newId(): String {
            counter += 1
            return "item-$counter"
        }
    }

    private lateinit var items: FakeItemRepository
    private lateinit var preferences: FakeReminderPreferences
    private lateinit var reminderDao: FakeReminderDao
    private lateinit var notifier: FakeReviewNotifier
    private lateinit var scheduler: FakeReviewScheduler

    @Before
    fun setUp() {
        items = FakeItemRepository(clock = { clock.instant() }, ids = ids)
        preferences = FakeReminderPreferences()
        reminderDao = FakeReminderDao()
        notifier = FakeReviewNotifier()
        scheduler = FakeReviewScheduler()
    }

    private fun checker() = DueReviewChecker(
        itemRepository = items,
        reminderPreferences = preferences,
        reminderDao = reminderDao,
        notifier = notifier,
        scheduler = scheduler,
        clock = clock,
    )

    private suspend fun dueItem() = items.add("deck-1", "prompt", "answer", null, false)

    @Test
    fun `due items produce a notification and remember the due set`() = runTest {
        dueItem()
        dueItem()

        val outcome = checker().check()

        assertThat(outcome).isEqualTo(DueReviewOutcome.NOTIFIED)
        assertThat(notifier.notifications).containsExactly(2)
        assertThat(reminderDao.stored?.lastNotifiedDueSignature).isNotNull()
    }

    @Test
    fun `no due items produce no notification`() = runTest {
        val item = dueItem()
        items.reschedule(item.id, now.plusSeconds(86_400))

        val outcome = checker().check()

        assertThat(outcome).isEqualTo(DueReviewOutcome.NOTHING_DUE)
        assertThat(notifier.notifications).isEmpty()
    }

    @Test
    fun `reminders turned off suppress the notification`() = runTest {
        dueItem()
        preferences.enabled.value = false

        val outcome = checker().check()

        assertThat(outcome).isEqualTo(DueReviewOutcome.REMINDERS_DISABLED)
        assertThat(notifier.notifications).isEmpty()
    }

    @Test
    fun `denied permission suppresses the notification`() = runTest {
        dueItem()
        notifier.permissionGranted = false

        val outcome = checker().check()

        assertThat(outcome).isEqualTo(DueReviewOutcome.PERMISSION_DENIED)
        assertThat(notifier.notifications).isEmpty()
    }

    @Test
    fun `the same due set is not notified twice`() = runTest {
        dueItem()
        dueItem()
        val checker = checker()

        checker.check()
        val second = checker.check()

        assertThat(second).isEqualTo(DueReviewOutcome.DUPLICATE_SUPPRESSED)
        assertThat(notifier.notifications).hasSize(1)
    }

    @Test
    fun `a changed due set is notified again`() = runTest {
        val a = dueItem()
        val checker = checker()
        checker.check()

        // One item is now scheduled in the future: the due set changed.
        items.reschedule(a.id, now.plusSeconds(86_400))
        val outcome = checker().check()

        assertThat(outcome).isEqualTo(DueReviewOutcome.NOTHING_DUE)
    }

    @Test
    fun `the next one-time check is rescheduled for the earliest future due time`() = runTest {
        val item = dueItem()
        val future = now.plusSeconds(3_600)
        items.reschedule(item.id, future)

        checker().check()

        assertThat(scheduler.nextDueChecks).contains(future)
    }

    private class FakeReminderPreferences : ReminderPreferences {
        val enabled = MutableStateFlow(true)
        override val remindersEnabled: Flow<Boolean> = enabled
        override suspend fun setRemindersEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }
    }

    private class FakeReminderDao : ReminderDao {
        var stored: ReminderStateEntity? = null
        override suspend fun upsert(state: ReminderStateEntity) {
            stored = state
        }

        override suspend fun getById(id: String): ReminderStateEntity? = stored

        override fun observeById(id: String): Flow<ReminderStateEntity?> =
            MutableStateFlow(stored).map { it }
    }

    private class FakeReviewNotifier : ReviewNotifier {
        val notifications = mutableListOf<Int>()
        var permissionGranted = true
        override fun isPermissionGranted(): Boolean = permissionGranted
        override fun ensureChannel() = Unit
        override fun notifyDue(count: Int, deckId: String?) {
            notifications += count
        }

        override fun cancel() = Unit
    }
}
