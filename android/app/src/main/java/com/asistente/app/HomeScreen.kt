package com.asistente.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@Composable
fun HomeScreen(
    state: OrbState,
    onOrbTapped: () -> Unit,
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
) {
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
                .widthIn(max = 320.dp),
        )

        Spacer(Modifier.height(32.dp))

        // La onda solo se muestra mientras hay voz (del usuario o del asistente).
        val showWave = state == OrbState.Listening || state == OrbState.Speaking
        VoiceWave(
            amplitude = if (showWave) amplitude.coerceAtLeast(0.35f) else 0f,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp)
                .height(56.dp),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = hint,
            style = MaterialTheme.typography.titleMedium,
            color = JarvisColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02060D)
@Composable
private fun HomeScreenPreview() {
    JarvisTheme {
        HomeScreen(state = OrbState.Listening, onOrbTapped = {}, amplitude = 0.6f)
    }
}
