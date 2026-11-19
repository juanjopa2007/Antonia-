package com.asistente.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asistente.app.R
import com.asistente.core.ui.JarvisColors
import com.asistente.voice.SpanishVoice

@Composable
fun SettingsScreen(
    settings: VoiceSettings,
    voices: List<SpanishVoice>,
    voicesLoaded: Boolean,
    onRateChanged: (Float) -> Unit,
    onVoiceSelected: (String?) -> Unit,
    onTest: () -> Unit,
    onStopTest: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mientras se arrastra el control se muestra el valor local; solo se guarda al soltar.
    var draftRate by remember(settings.rate) { mutableFloatStateOf(settings.rate) }

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
