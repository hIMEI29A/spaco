package com.spaco.app.core.data.work

/**
 * The notification surface, behind an interface so tests and the worker never
 * touch `NotificationManagerCompat` directly.
 */
interface ReviewNotifier {

    /** True when the app may post notifications on this device/version. */
    fun isPermissionGranted(): Boolean

    /** Creates the notification channel if needed. Idempotent. */
    fun ensureChannel()

    fun notifyDue(count: Int, deckId: String?)

    fun cancel()

    companion object {
        const val CHANNEL_ID = "spaco_review_reminders"
        const val NOTIFICATION_ID = 1001
    }
}
