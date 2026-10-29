package com.spaco.app.core.data.work

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
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

/**
 * Durability of the due-review work.
 *
 * A real reboot is an OS-level event that cannot be triggered from a unit test,
 * so this covers the two things a reboot depends on:
 *
 * 1. the scheduled work is persisted by WorkManager (it lives in the work
 *    database, not in memory), and
 * 2. the app ships WorkManager's `RescheduleReceiver`, which the OS invokes on
 *    `BOOT_COMPLETED` to re-register persisted work.
 *
 * The end-to-end reboot procedure (adb) is documented in `docs/dev-setup.md`.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderDurabilityTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: ReviewReminderScheduler
    private val now = Instant.parse("2026-07-01T08:00:00Z")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = ReviewReminderScheduler(workManager, Clock.fixed(now, ZoneOffset.UTC))
    }

    @Test
    fun `scheduled work survives re-reading it from the work database`() {
        scheduler.schedulePeriodic()
        scheduler.scheduleNextDueCheck(now.plusSeconds(3_600))

        // Re-reading via a fresh lookup models work that outlives the process
        // that enqueued it: WorkManager restores it from its own database.
        val periodic = workManager.getWorkInfosForUniqueWork(WorkNames.PERIODIC_DUE_SWEEP).get()
        val next = workManager.getWorkInfosForUniqueWork(WorkNames.NEXT_DUE_CHECK).get()

        assertThat(periodic).hasSize(1)
        assertThat(periodic.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(next).hasSize(1)
        assertThat(next.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `the app registers WorkManager's boot receiver so work is restored on reboot`() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_BOOT_COMPLETED),
            // WorkManager ships the receiver disabled and enables it once work
            // is persisted, so disabled components must be included.
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )

        val bootReceiverNames = receivers.map { it.activityInfo.name }
        assertThat(bootReceiverNames).contains("androidx.work.impl.background.systemalarm.RescheduleReceiver")
    }
}
