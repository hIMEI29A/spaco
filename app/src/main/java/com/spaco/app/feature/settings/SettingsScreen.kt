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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

object SettingsTags {
    const val TOGGLE = "settings-reminders-toggle"
    const val BLOCKED = "settings-reminders-blocked"
    const val REQUEST_PERMISSION = "settings-request-permission"
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
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onToggle: (Boolean) -> Unit = {},
    onRequestPermission: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
    }
}
