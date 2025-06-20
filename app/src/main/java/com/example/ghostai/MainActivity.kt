package com.example.ghostai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ghostai.ui.theme.GhostAITheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: GhostViewModel by viewModels()
    private var recognizerManager: SpeechRecognizerManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permission = Manifest.permission.RECORD_AUDIO

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Timber.d("Microphone permission granted")
                setupRecognizer()
            } else {
                Timber.w("Microphone permission denied")
            }
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        } else {
            setupRecognizer()
        }

        setContent {
            GhostAITheme {
                val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
                MainScreen(isSpeaking = isSpeaking)
            }
        }
    }

    private fun setupRecognizer() {
        Timber.d("CGH: setupRecognizer()")
        recognizerManager = SpeechRecognizerManager(
            context = this,
            onStart = { viewModel.onUserSpeechStart() },
            onResult = { result ->
                viewModel.onUserSpeechEnd(result)
                Timber.d("CGH: Heard: $result")
            },
            onError = { error, message ->
                viewModel.onUserSpeechEnd(null)
                Timber.e("CGH: Speech error: $message")

                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    viewModel.restartRecognizerWithDelay()
                }
            }
        )

        viewModel.setSpeechRecognizer(recognizerManager!!)

    }

    override fun onDestroy() {
        recognizerManager?.destroy()
        viewModel.stopListening()
        super.onDestroy()
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GhostAITheme {
        MainScreen(false)
    }
}