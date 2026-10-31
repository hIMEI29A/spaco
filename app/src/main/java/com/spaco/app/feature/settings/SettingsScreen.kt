package com.spaco.app.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

object SettingsTags {
    const val TOGGLE = "settings-reminders-toggle"
    const val BLOCKED = "settings-reminders-blocked"
    const val REQUEST_PERMISSION = "settings-request-permission"

    const val COACH_ENDPOINT = "settings-coach-endpoint"
    const val COACH_MODEL = "settings-coach-model"
    const val COACH_CREDENTIAL = "settings-coach-credential"
    const val COACH_CREDENTIAL_SET = "settings-coach-credential-set"
    const val COACH_SAVE = "settings-coach-save"
    const val COACH_CLEAR = "settings-coach-clear"
    const val COACH_CHECK = "settings-coach-check"
    const val COACH_MESSAGE = "settings-coach-message"
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    SettingsScreen(
        state = state,
        onToggle = viewModel::setRemindersEnabled,
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.refreshPermission()
            }
        },
        onCoachEndpointChanged = viewModel::onCoachEndpointChanged,
        onCoachModelChanged = viewModel::onCoachModelChanged,
        onCoachCredentialChanged = viewModel::onCoachCredentialChanged,
        onCoachSave = viewModel::saveCoachConfiguration,
        onCoachClear = viewModel::clearCoachConfiguration,
        onCoachCheck = viewModel::checkCoachConnection,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onToggle: (Boolean) -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onCoachEndpointChanged: (String) -> Unit = {},
    onCoachModelChanged: (String) -> Unit = {},
    onCoachCredentialChanged: (String) -> Unit = {},
    onCoachSave: () -> Unit = {},
    onCoachClear: () -> Unit = {},
    onCoachCheck: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Review reminders",
                modifier = Modifier.weight(1f),
            )
            Switch(
                modifier = Modifier.testTag(SettingsTags.TOGGLE),
                checked = state.remindersEnabled,
                onCheckedChange = onToggle,
            )
        }

        if (state.blockedBySystem) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Reminders are blocked by the system. Notifications are off until you allow them.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(SettingsTags.BLOCKED),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                modifier = Modifier.testTag(SettingsTags.REQUEST_PERMISSION),
                onClick = onRequestPermission,
            ) { Text("Allow notifications") }
        }

        Spacer(Modifier.height(24.dp))
        CoachSettings(
            state = state,
            onEndpointChanged = onCoachEndpointChanged,
            onModelChanged = onCoachModelChanged,
            onCredentialChanged = onCoachCredentialChanged,
            onSave = onCoachSave,
            onClear = onCoachClear,
            onCheck = onCoachCheck,
        )
    }
}

@Composable
private fun CoachSettings(
    state: SettingsUiState,
    onEndpointChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onCredentialChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onCheck: () -> Unit,
) {
    Text("AI coach", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Leave this empty to practise offline. Enter an OpenAI-compatible " +
            "endpoint and a model to have a remote model coach you.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().testTag(SettingsTags.COACH_ENDPOINT),
        value = state.coachDraft.endpoint,
        onValueChange = onEndpointChanged,
        label = { Text("Endpoint") },
        singleLine = true,
    )
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().testTag(SettingsTags.COACH_MODEL),
        value = state.coachDraft.model,
        onValueChange = onModelChanged,
        label = { Text("Model") },
        singleLine = true,
    )
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().testTag(SettingsTags.COACH_CREDENTIAL),
        value = state.coachDraft.credential,
        onValueChange = onCredentialChanged,
        label = { Text("Credential (optional)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )

    if (state.coachCredentialSet) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "A credential is set. Entering a new one replaces it; saving " +
                "without one deletes it.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(SettingsTags.COACH_CREDENTIAL_SET),
        )
    }

    Spacer(Modifier.height(12.dp))
    Row {
        Button(
            modifier = Modifier.testTag(SettingsTags.COACH_SAVE),
            onClick = onSave,
        ) { Text("Save") }
        Spacer(Modifier.height(0.dp))
        OutlinedButton(
            modifier = Modifier.testTag(SettingsTags.COACH_CLEAR),
            onClick = onClear,
        ) { Text("Clear") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        modifier = Modifier.fillMaxWidth().testTag(SettingsTags.COACH_CHECK),
        onClick = onCheck,
        enabled = state.coachConfigured,
    ) { Text("Check connection") }

    state.coachMessage?.let { message ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(SettingsTags.COACH_MESSAGE),
        )
    }
}
