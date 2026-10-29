package com.spaco.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spaco.app.core.data.work.ReviewNotifier
import com.spaco.app.core.domain.repository.ReminderPreferences
import com.spaco.app.core.domain.repository.ReviewScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val remindersEnabled: Boolean = true,
    val permissionGranted: Boolean = true,
    val loaded: Boolean = false,
) {
    /** Reminders are on but the system blocks them. */
    val blockedBySystem: Boolean get() = remindersEnabled && !permissionGranted
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val reminderPreferences: ReminderPreferences,
    private val scheduler: ReviewScheduler,
    private val notifier: ReviewNotifier,
) : ViewModel() {

    private val permissionGranted = MutableStateFlow(notifier.isPermissionGranted())

    val uiState: StateFlow<SettingsUiState> = combine(
        reminderPreferences.remindersEnabled,
        permissionGranted,
    ) { enabled, granted ->
        SettingsUiState(remindersEnabled = enabled, permissionGranted = granted, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            reminderPreferences.setRemindersEnabled(enabled)
            if (enabled) {
                // Restore scheduling; the next check clears whatever is due.
                notifier.ensureChannel()
                scheduler.schedulePeriodic()
            } else {
                scheduler.cancelAll()
                notifier.cancel()
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        permissionGranted.value = granted
    }

    fun refreshPermission() {
        permissionGranted.value = notifier.isPermissionGranted()
    }
}
