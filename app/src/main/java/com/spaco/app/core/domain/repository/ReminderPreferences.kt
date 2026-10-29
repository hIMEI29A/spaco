package com.spaco.app.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** The learner's review-reminder setting. */
interface ReminderPreferences {

    val remindersEnabled: Flow<Boolean>

    suspend fun setRemindersEnabled(enabled: Boolean)
}
