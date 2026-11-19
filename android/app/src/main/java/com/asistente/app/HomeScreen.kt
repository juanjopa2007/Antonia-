package com.asistente.app

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.asistente.core.ui.JarvisColors
import com.asistente.core.ui.JarvisOrb
import com.asistente.core.ui.JarvisTheme
import com.asistente.core.ui.OrbState
import com.asistente.core.ui.VoiceWave

@StringRes
private fun ConversationMessage.stringRes(): Int = when (this) {
    ConversationMessage.NoSpeechHeard -> R.string.msg_no_speech
    ConversationMessage.RecognizerBusy -> R.string.msg_busy
    ConversationMessage.NeedsPermission -> R.string.msg_needs_permission
    ConversationMessage.NetworkProblem -> R.string.msg_network
    ConversationMessage.RecognizerUnavailable -> R.string.msg_unavailable
    ConversationMessage.SpeechFailed -> R.string.msg_speech_failed
    ConversationMessage.BrainFailed -> R.string.msg_brain_failed
    ConversationMessage.Generic -> R.string.msg_generic
}

@Composable
fun HomeScreen(
    ui: ConversationUi,
    micDenied: Boolean,
    onOrbTapped: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = ui.orb
    val stateLabel = stringResource(
        when (state) {
            OrbState.Idle -> R.string.state_idle
            OrbState.Listening -> R.string.state_listening
            OrbState.Thinking -> R.string.state_thinking
            OrbState.Speaking -> R.string.state_speaking
        },
    )
    val hint = stringResource(
        when (state) {
            OrbState.Idle -> R.string.hint_idle
            OrbState.Listening -> R.string.hint_listening
            OrbState.Thinking -> R.string.hint_thinking
            OrbState.Speaking -> R.string.hint_speaking
        },
    )

    // El motor de voz no informa del volumen al hablar: en ese estado la animación es
    // solo visual y no refleja la voz real.
    val amplitude = if (state == OrbState.Speaking) 0.45f else ui.level
    val showWave = state == OrbState.Listening || state == OrbState.Speaking

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(JarvisColors.Black, JarvisColors.Background, JarvisColors.DeepBlue.copy(alpha = 0.35f)),
                ),
            )
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings_open), color = JarvisColors.Cyan)
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JarvisOrb(
                state = state,
                onClick = onOrbTapped,
                amplitude = amplitude,
                description = stringResource(R.string.orb_description),
                stateLabel = stateLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 280.dp),
            )

            Spacer(Modifier.height(24.dp))

            VoiceWave(
                amplitude = if (showWave) amplitude.coerceAtLeast(0.35f) else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 280.dp)
                    .height(48.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = hint,
                style = MaterialTheme.typography.titleMedium,
                color = JarvisColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (micDenied) {
                Text(
                    text = stringResource(R.string.mic_permission_denied),
                    color = JarvisColors.Error,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onOpenAppSettings) {
                    Text(stringResource(R.string.open_app_settings), color = JarvisColors.Cyan)
                }
            }
            ui.message?.let {
                Text(
                    text = stringResource(it.stringRes()),
                    color = JarvisColors.Error,
                    textAlign = TextAlign.Center,
                )
            }
            if (ui.transcript.isNotEmpty()) {
                Text(
                    text = "${stringResource(R.string.you_said)}: ${ui.transcript}",
                    color = JarvisColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (ui.reply.isNotEmpty()) {
                Text(
                    text = "${stringResource(R.string.antonia_says)}: ${ui.reply}",
                    color = JarvisColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02060D)
@Composable
private fun HomeScreenPreview() {
    JarvisTheme {
        Box {
            HomeScreen(
                ui = ConversationUi(orb = OrbState.Listening, transcript = "qué tengo mañana", level = 0.6f),
                micDenied = false,
                onOrbTapped = {},
                onOpenSettings = {},
                onOpenAppSettings = {},
            )
        }
    }
}
