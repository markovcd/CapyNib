package io.github.markovcd.capynib.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.memos.LoopbackReceiver
import io.github.markovcd.capynib.core.memos.SyncFailure
import io.github.markovcd.capynib.core.store.Trash
import io.github.markovcd.capynib.ui.SyncStatusLabel
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.theme.color

@Composable
fun SettingsScreen(model: SettingsViewModel, onClose: () -> Unit) {
    val server by model.server.collectAsStateWithLifecycle()
    val token by model.token.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val trashDays by model.trashDays.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(12.dp)
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
            Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_section).uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (saved.isConfigured) {
                TextButton(
                    onClick = model::disconnect,
                    modifier = Modifier.testTag(Tags.DISCONNECT),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) {
                    Text(stringResource(R.string.settings_disconnect))
                }
            } else {
                OutlinedTextField(
                    value = server,
                    onValueChange = { model.server.value = it },
                    modifier = Modifier.fillMaxWidth().testTag(Tags.SERVER),
                    label = { Text(stringResource(R.string.settings_server)) },
                    placeholder = { Text(stringResource(R.string.settings_server_example)) },
                    singleLine = true,
                    shape = fieldShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = model::signInWithBrowser, modifier = Modifier.fillMaxWidth().testTag(Tags.SIGN_IN)) {
                        Text(stringResource(R.string.settings_sign_in))
                    }
                    Text(stringResource(R.string.settings_sign_in_help), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.settings_redirect_help), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    SelectionContainer {
                        Text(
                            LoopbackReceiver.REDIRECT_URI,
                            Modifier.testTag(Tags.REDIRECT_URI),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { model.token.value = it },
                    modifier = Modifier.fillMaxWidth().testTag(Tags.TOKEN),
                    label = { Text(stringResource(R.string.settings_token)) },
                    supportingText = { Text(stringResource(R.string.settings_token_help)) },
                    singleLine = true,
                    shape = fieldShape,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                FilledTonalButton(onClick = model::connect, modifier = Modifier.testTag(Tags.CONNECT)) {
                    Text(stringResource(R.string.settings_connect))
                }
            }
            Column(
                Modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(messageText(message), Modifier.testTag(Tags.SETTINGS_MESSAGE), style = MaterialTheme.typography.titleSmall)
                val label = SyncStatusLabel.of(status)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(label.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = label.tone.color())
                    Text(stringResource(label.text, label.detail.orEmpty()), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.settings_trash_section).uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            OutlinedTextField(
                value = trashDays,
                onValueChange = model::setTrashDays,
                modifier = Modifier.fillMaxWidth().testTag(Tags.TRASH_DAYS),
                label = { Text(stringResource(R.string.settings_trash_days)) },
                supportingText = { Text(stringResource(R.string.settings_trash_days_help, Trash.DAYS.first, Trash.DAYS.last)) },
                isError = Trash.parseDays(trashDays) == null,
                singleLine = true,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

@Composable
private fun messageText(message: SettingsMessage): String = when (message) {
    SettingsMessage.NotConnected -> stringResource(R.string.settings_not_connected)
    SettingsMessage.BadAddress -> stringResource(R.string.settings_bad_address)
    SettingsMessage.TokenMissing -> stringResource(R.string.settings_token_missing)
    SettingsMessage.Checking -> stringResource(R.string.settings_checking)
    is SettingsMessage.Connected -> stringResource(R.string.settings_connected, message.host, message.username)
    is SettingsMessage.WaitingForBrowser -> stringResource(R.string.settings_waiting_for_browser, message.provider)
    SettingsMessage.NoSignInProvider -> stringResource(R.string.settings_no_sign_in_provider)
    is SettingsMessage.SignInRefused -> stringResource(R.string.settings_sign_in_refused, message.reason)
    SettingsMessage.NoBrowser -> stringResource(R.string.settings_no_browser)
    is SettingsMessage.Failed -> when (message.failure) {
        is SyncFailure.Unreachable -> stringResource(R.string.sync_offline)
        is SyncFailure.Unauthorized -> stringResource(R.string.sync_unauthorized)
        is SyncFailure.Server -> stringResource(R.string.sync_error, message.failure.message)
    }
}
