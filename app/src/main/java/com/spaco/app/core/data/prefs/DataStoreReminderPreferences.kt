package com.spaco.app.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.spaco.app.core.domain.repository.ReminderPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreReminderPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderPreferences {

    override val remindersEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[REMINDERS_ENABLED] ?: true }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[REMINDERS_ENABLED] = enabled }
    }

    private companion object {
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
    }
}
