package com.spaco.app.core.data.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemReviewNotifierTest {

    private lateinit var context: Application
    private lateinit var notifier: SystemReviewNotifier
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notifier = SystemReviewNotifier(context)
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun `denied permission reports unavailable and posts nothing`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertThat(notifier.isPermissionGranted()).isFalse()

        notifier.notifyDue(count = 3, deckId = "deck-1")

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun `granted permission posts a notification`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notifyDue(count = 3, deckId = "deck-1")

        assertThat(shadowOf(manager).getNotification(ReviewNotifier.NOTIFICATION_ID)).isNotNull()
    }

    @Test
    fun `the channel is created exactly once`() {
        notifier.ensureChannel()
        notifier.ensureChannel()

        val channelIds = shadowOf(manager).notificationChannels.map { it.id }
        assertThat(channelIds.count { it == ReviewNotifier.CHANNEL_ID }).isEqualTo(1)
    }

    @Test
    fun `cancelling removes the reminder`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.notifyDue(count = 1, deckId = null)

        notifier.cancel()

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }
}
