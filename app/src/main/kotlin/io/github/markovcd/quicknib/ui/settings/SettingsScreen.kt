package io.github.markovcd.quicknib.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.quicknib.R
import io.github.markovcd.quicknib.core.memos.SyncFailure
import io.github.markovcd.quicknib.ui.SyncStatusLabel
import io.github.markovcd.quicknib.ui.Tags

@Composable
fun SettingsScreen(model: SettingsViewModel, onClose: () -> Unit) {
    val server by model.server.collectAsStateWithLifecycle()
    val token by model.token.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag(Tags.BACK)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = server,
                onValueChange = { model.server.value = it },
                modifier = Modifier.fillMaxWidth().testTag(Tags.SERVER),
                label = { Text(stringResource(R.string.settings_server)) },
                placeholder = { Text(stringResource(R.string.settings_server_example)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { model.token.value = it },
                modifier = Modifier.fillMaxWidth().testTag(Tags.TOKEN),
                label = { Text(stringResource(R.string.settings_token)) },
                supportingText = { Text(stringResource(R.string.settings_token_help)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = model::connect, modifier = Modifier.testTag(Tags.CONNECT)) {
                    Text(stringResource(R.string.settings_connect))
                }
                if (model.isConnected) {
                    OutlinedButton(onClick = model::syncNow, modifier = Modifier.testTag(Tags.SYNC_NOW)) {
                        Text(stringResource(R.string.settings_sync_now))
                    }
                    OutlinedButton(onClick = model::disconnect, modifier = Modifier.testTag(Tags.DISCONNECT)) {
                        Text(stringResource(R.string.settings_disconnect))
                    }
                }
            }
            Text(messageText(message), Modifier.testTag(Tags.SETTINGS_MESSAGE), style = MaterialTheme.typography.bodyMedium)
            val label = SyncStatusLabel.of(status)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(label.icon, contentDescription = null)
                Text(stringResource(label.text, label.detail.orEmpty()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun messageText(message: SettingsMessage): String = when (message) {
    SettingsMessage.NotConnected -> stringResource(R.string.settings_not_connected)
    SettingsMessage.BadAddress -> stringResource(R.string.settings_bad_address)
    SettingsMessage.TokenMissing -> stringResource(R.string.settings_token_missing)
    SettingsMessage.Checking -> stringResource(R.string.settings_checking)
    is SettingsMessage.Connected -> stringResource(R.string.settings_connected, message.username)
    is SettingsMessage.Failed -> when (message.failure) {
        is SyncFailure.Unreachable -> stringResource(R.string.sync_offline)
        is SyncFailure.Unauthorized -> stringResource(R.string.sync_unauthorized)
        is SyncFailure.Server -> stringResource(R.string.sync_error, message.failure.message)
    }
}
