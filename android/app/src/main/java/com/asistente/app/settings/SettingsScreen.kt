package com.asistente.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.asistente.app.R
import com.asistente.app.brain.BrainFailure
import com.asistente.core.ui.JarvisColors
import com.asistente.voice.SpanishVoice

@Composable
fun SettingsScreen(
    settings: VoiceSettings,
    voices: List<SpanishVoice>,
    voicesLoaded: Boolean,
    connection: ConnectionSettings,
    connectionTest: ConnectionTest,
    onRateChanged: (Float) -> Unit,
    onVoiceSelected: (String?) -> Unit,
    onTest: () -> Unit,
    onStopTest: () -> Unit,
    onSaveConnection: (String, String) -> Unit,
    onTestConnection: (String, String) -> Unit,
    onNewConversation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mientras se arrastra el control se muestra el valor local; solo se guarda al soltar.
    var draftRate by remember(settings.rate) { mutableFloatStateOf(settings.rate) }
    var conversationReset by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisColors.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.settings_back), color = JarvisColors.Cyan)
            }
        }
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = JarvisColors.TextPrimary,
        )

        ConnectionSection(
            connection = connection,
            test = connectionTest,
            onSave = onSaveConnection,
            onTest = onTestConnection,
        )

        OutlinedButton(onClick = { onNewConversation(); conversationReset = true }) {
            Text(stringResource(R.string.conversation_reset), color = JarvisColors.Cyan)
        }
        if (conversationReset) {
            Text(stringResource(R.string.conversation_reset_done), color = JarvisColors.TextSecondary)
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_speed), color = JarvisColors.TextPrimary)
            Text(stringResource(R.string.settings_speed_value, draftRate), color = JarvisColors.Cyan)
        }
        Slider(
            value = draftRate,
            onValueChange = { draftRate = it },
            onValueChangeFinished = { onRateChanged(draftRate) },
            valueRange = VoiceSettings.MIN_RATE..VoiceSettings.MAX_RATE,
            // Pasos de 0,1: de 0,5 a 2,0 hay 15 tramos, es decir, 14 posiciones intermedias.
            steps = 14,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onTest) { Text(stringResource(R.string.settings_test)) }
            OutlinedButton(onClick = onStopTest) {
                Text(stringResource(R.string.settings_stop_test), color = JarvisColors.Cyan)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_voice),
            style = MaterialTheme.typography.titleMedium,
            color = JarvisColors.TextPrimary,
        )

        VoiceOption(
            label = stringResource(R.string.settings_voice_default),
            selected = settings.voiceId == null,
            onClick = { onVoiceSelected(null) },
        )
        voices.forEach { voice ->
            VoiceOption(
                label = voice.label,
                selected = settings.voiceId == voice.id,
                onClick = { onVoiceSelected(voice.id) },
            )
        }

        if (!voicesLoaded) {
            Text(stringResource(R.string.settings_voices_loading), color = JarvisColors.TextSecondary)
        } else if (voices.isEmpty()) {
            Text(stringResource(R.string.settings_voices_none), color = JarvisColors.TextSecondary)
        }
    }
}

@Composable
private fun ConnectionSection(
    connection: ConnectionSettings,
    test: ConnectionTest,
    onSave: (String, String) -> Unit,
    onTest: (String, String) -> Unit,
) {
    // Se edita una copia local; solo se guarda al pulsar «Guardar».
    var url by remember(connection.serverUrl) { mutableStateOf(connection.serverUrl) }
    var token by remember(connection.token) { mutableStateOf(connection.token) }
    var saved by remember { mutableStateOf(false) }

    val urlInvalid = url.isNotBlank() && ConnectionSettings(url, token).normalizedUrl() == null

    Text(
        text = stringResource(R.string.connection_title),
        style = MaterialTheme.typography.titleMedium,
        color = JarvisColors.TextPrimary,
    )
    OutlinedTextField(
        value = url,
        onValueChange = { url = it; saved = false },
        label = { Text(stringResource(R.string.connection_url)) },
        singleLine = true,
        isError = urlInvalid,
        supportingText = if (urlInvalid) {
            { Text(stringResource(R.string.connection_url_invalid)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Uri,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = token,
        onValueChange = { token = it; saved = false },
        label = { Text(stringResource(R.string.connection_token)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Password,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onSave(url, token); saved = true }) {
            Text(stringResource(R.string.connection_save))
        }
        OutlinedButton(onClick = { onTest(url, token) }, enabled = test != ConnectionTest.Testing) {
            Text(stringResource(R.string.connection_test), color = JarvisColors.Cyan)
        }
    }
    if (saved) {
        Text(stringResource(R.string.connection_saved), color = JarvisColors.TextSecondary)
    }
    when (test) {
        ConnectionTest.Idle -> Unit
        ConnectionTest.Testing ->
            Text(stringResource(R.string.connection_testing), color = JarvisColors.TextSecondary)
        ConnectionTest.Ok ->
            Text(stringResource(R.string.connection_ok), color = JarvisColors.Cyan)
        is ConnectionTest.Failed -> Text(
            text = stringResource(
                when (test.failure) {
                    BrainFailure.NotConfigured -> R.string.connection_not_configured
                    BrainFailure.Unauthorized -> R.string.connection_unauthorized
                    BrainFailure.Offline -> R.string.connection_offline
                    BrainFailure.Server, BrainFailure.Unknown -> R.string.connection_server_error
                },
            ),
            color = JarvisColors.Error,
        )
    }
}

@Composable
private fun VoiceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            color = JarvisColors.TextPrimary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
