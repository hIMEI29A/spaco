package com.spaco.app.core.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewReminderSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: ReviewReminderScheduler
    private val now = Instant.parse("2026-07-01T08:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        scheduler = ReviewReminderScheduler(workManager, Clock.fixed(now, ZoneOffset.UTC))
    }

    @Test
    fun `the periodic sweep is enqueued`() {
        scheduler.schedulePeriodic()

        val infos = workManager.getWorkInfosForUniqueWork(WorkNames.PERIODIC_DUE_SWEEP).get()

        assertThat(infos).hasSize(1)
        assertThat(infos.first().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `a one-time check is delayed until the earliest due time`() {
        val earliest = now.plusSeconds(3_600)

        scheduler.scheduleNextDueCheck(earliest)

        val info = workManager.getWorkInfosForUniqueWork(WorkNames.NEXT_DUE_CHECK).get().single()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.initialDelayMillis).isEqualTo(3_600_000L)
    }

    @Test
    fun `a due time in the past schedules immediately rather than negatively`() {
        scheduler.scheduleNextDueCheck(now.minusSeconds(600))

        val info = workManager.getWorkInfosForUniqueWork(WorkNames.NEXT_DUE_CHECK).get().single()
        assertThat(info.initialDelayMillis).isEqualTo(0L)
    }

    @Test
    fun `re-scheduling the one-time check replaces the previous request`() {
        scheduler.scheduleNextDueCheck(now.plusSeconds(600))
        scheduler.scheduleNextDueCheck(now.plusSeconds(1_200))

        val infos = workManager.getWorkInfosForUniqueWork(WorkNames.NEXT_DUE_CHECK).get()
        // REPLACE keeps a single pending reminder.
        assertThat(infos.count { it.state == WorkInfo.State.ENQUEUED }).isEqualTo(1)
    }

    @Test
    fun `no pending due time cancels the one-time check`() {
        scheduler.scheduleNextDueCheck(now.plusSeconds(600))
        scheduler.scheduleNextDueCheck(null)

        val infos = workManager.getWorkInfosForUniqueWork(WorkNames.NEXT_DUE_CHECK).get()
        assertThat(infos.all { it.state == WorkInfo.State.CANCELLED }).isTrue()
    }

    @Test
    fun `cancelling all removes both checks`() {
        scheduler.schedulePeriodic()
        scheduler.scheduleNextDueCheck(now.plusSeconds(600))

        scheduler.cancelAll()

        val periodic = workManager.getWorkInfosForUniqueWork(WorkNames.PERIODIC_DUE_SWEEP).get()
        val oneTime = workManager.getWorkInfosForUniqueWork(WorkNames.NEXT_DUE_CHECK).get()
        assertThat(periodic.all { it.state == WorkInfo.State.CANCELLED }).isTrue()
        assertThat(oneTime.all { it.state == WorkInfo.State.CANCELLED }).isTrue()
    }
}
