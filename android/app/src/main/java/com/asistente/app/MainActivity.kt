package com.asistente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.asistente.core.ui.JarvisTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onStop() {
        // Si la app pasa a segundo plano, el micrófono y la voz se paran.
        viewModel.stopConversation()
        super.onStop()
    }
}
