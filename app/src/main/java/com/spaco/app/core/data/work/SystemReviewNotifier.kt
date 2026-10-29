package com.spaco.app.core.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spaco.app.MainActivity
import com.spaco.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemReviewNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReviewNotifier {

    override fun isPermissionGranted(): Boolean {
        // Before API 33 notifications need no runtime permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun ensureChannel() {
        // The notification channel API exists from API 26, which is this app's
        // minimum, so no version guard is needed.
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ReviewNotifier.CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            ReviewNotifier.CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    @android.annotation.SuppressLint("MissingPermission")
    override fun notifyDue(count: Int, deckId: String?) {
        // Permission is checked first, so the notify call below cannot throw for
        // a missing POST_NOTIFICATIONS permission.
        if (!isPermissionGranted()) return
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            deckId?.let { putExtra(EXTRA_DECK_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ReviewNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_review)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.resources.getQuantityString(R.plurals.reminder_due, count, count))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(ReviewNotifier.NOTIFICATION_ID, notification)
    }

    override fun cancel() {
        NotificationManagerCompat.from(context).cancel(ReviewNotifier.NOTIFICATION_ID)
    }

    companion object {
        const val EXTRA_DECK_ID = "deckId"
    }
}
