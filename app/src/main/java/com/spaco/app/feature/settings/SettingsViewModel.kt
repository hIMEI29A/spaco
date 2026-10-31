package com.spaco.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spaco.app.core.data.work.ReviewNotifier
import com.spaco.app.core.domain.coach.CoachConnectionProbe
import com.spaco.app.core.domain.coach.CoachConnectionResult
import com.spaco.app.core.domain.model.CoachConfiguration
import com.spaco.app.core.domain.repository.CoachConfigurationStore
import com.spaco.app.core.domain.repository.ReminderPreferences
import com.spaco.app.core.domain.repository.ReviewScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the learner has typed into the coach form but not yet saved. */
data class CoachDraft(
    val endpoint: String = "",
    val model: String = "",
    val credential: String = "",
)

data class SettingsUiState(
    val remindersEnabled: Boolean = true,
    val permissionGranted: Boolean = true,
    val loaded: Boolean = false,
    /** Editable form values. The credential here is only ever what was just typed. */
    val coachDraft: CoachDraft = CoachDraft(),
    /** Whether a usable configuration is saved; drives which coach answers. */
    val coachConfigured: Boolean = false,
    /** Whether a saved credential exists, so the form can say so without showing it. */
    val coachCredentialSet: Boolean = false,
    /** Outcome of the last connection check, or of the last save/clear. */
    val coachMessage: String? = null,
) {
    /** Reminders are on but the system blocks them. */
    val blockedBySystem: Boolean get() = remindersEnabled && !permissionGranted
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val reminderPreferences: ReminderPreferences,
    private val scheduler: ReviewScheduler,
    private val notifier: ReviewNotifier,
    private val configurationStore: CoachConfigurationStore,
    private val connectionProbe: CoachConnectionProbe,
) : ViewModel() {

    private val permissionGranted = MutableStateFlow(notifier.isPermissionGranted())
    private val draft = MutableStateFlow(CoachDraft())
    private val coachMessage = MutableStateFlow<String?>(null)
    private var draftSeeded = false

    init {
        viewModelScope.launch {
            seedDraftFrom(configurationStore.configuration.first())
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        reminderPreferences.remindersEnabled,
        permissionGranted,
        configurationStore.configuration,
        draft,
        coachMessage,
    ) { enabled, granted, saved, currentDraft, message ->
        SettingsUiState(
            remindersEnabled = enabled,
            permissionGranted = granted,
            loaded = true,
            coachDraft = currentDraft,
            coachConfigured = saved != null,
            coachCredentialSet = saved?.credential != null,
            coachMessage = message,
        )
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

    // --- coach configuration ---------------------------------------------

    fun onCoachEndpointChanged(value: String) {
        draft.value = draft.value.copy(endpoint = value)
    }

    fun onCoachModelChanged(value: String) {
        draft.value = draft.value.copy(model = value)
    }

    fun onCoachCredentialChanged(value: String) {
        draft.value = draft.value.copy(credential = value)
    }

    /**
     * Saves the form as the coach configuration. A blank credential saves an
     * endpoint that needs none — and, for an existing configuration, is how the
     * learner deletes a stored credential.
     */
    fun saveCoachConfiguration() {
        val typed = draft.value
        val configuration = CoachConfiguration.from(typed.endpoint, typed.model, typed.credential)
        if (configuration == null) {
            coachMessage.value = "Enter both an endpoint and a model."
            return
        }
        viewModelScope.launch {
            configurationStore.save(configuration)
            // Never keep a credential in the view model longer than needed.
            draft.value = typed.copy(credential = "")
            coachMessage.value = if (configuration.credential == null) {
                "Configuration saved. Requests will carry no credential."
            } else {
                "Configuration saved. A credential is set."
            }
        }
    }

    /** Removes the saved configuration and returns the app to the offline coach. */
    fun clearCoachConfiguration() {
        viewModelScope.launch {
            configurationStore.clear()
            draft.value = CoachDraft()
            coachMessage.value = "Configuration cleared. Coaching is offline again."
        }
    }

    /**
     * Probes the saved configuration once and reports the outcome. Runs against
     * what is saved, not what is typed, so it checks what coach requests will
     * actually use.
     */
    fun checkCoachConnection() {
        viewModelScope.launch {
            val saved = configurationStore.configuration.first()
            if (saved == null) {
                coachMessage.value = "Save a configuration before checking the connection."
                return@launch
            }
            coachMessage.value = (connectionProbe.check(saved)).toMessage()
        }
    }

    companion object {
        /** Mapping is from a closed enum, so no typed text can leak into a message. */
        fun CoachConnectionResult.toMessage(): String = when (this) {
            CoachConnectionResult.SUCCESS -> "Connection succeeded."
            CoachConnectionResult.AUTHENTICATION_FAILED ->
                "The endpoint rejected the credential. Check it and save again."
            CoachConnectionResult.UNREACHABLE -> "The endpoint could not be reached."
        }
    }

    private fun seedDraftFrom(saved: CoachConfiguration?) {
        if (draftSeeded) return
        draftSeeded = true
        if (saved != null) {
            // Endpoint and model are shown back; the credential never is.
            draft.value = draft.value.copy(endpoint = saved.endpoint, model = saved.model)
        }
    }
}
