package com.asistente.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asistente.app.settings.SettingsScreen
import com.asistente.core.ui.OrbState

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val voicesLoaded by viewModel.voicesLoaded.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var micDenied by remember { mutableStateOf(false) }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micDenied = !granted
        // Si el usuario acaba de conceder el permiso, el toque que lo pidió empieza a escuchar.
        if (granted) viewModel.onOrbTapped()
    }

    if (showSettings) {
        BackHandler { showSettings = false; viewModel.stopTest() }
        SettingsScreen(
            settings = settings,
            voices = voices,
            voicesLoaded = voicesLoaded,
            onRateChanged = viewModel::setRate,
            onVoiceSelected = viewModel::setVoice,
            onTest = viewModel::testVoice,
            onStopTest = viewModel::stopTest,
            onBack = { showSettings = false; viewModel.stopTest() },
        )
    } else {
        HomeScreen(
            ui = ui,
            micDenied = micDenied && !hasMicPermission(),
            onOrbTapped = {
                if (ui.orb == OrbState.Idle && !hasMicPermission()) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    micDenied = false
                    viewModel.onOrbTapped()
                }
            },
            onOpenSettings = {
                // Al salir de la pantalla principal se corta cualquier escucha o respuesta en curso.
                viewModel.stopConversation()
                showSettings = true
            },
            onOpenAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    },
                )
            },
        )
    }
}
