package com.example.ghostai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ghostai.model.DeviceSettings
import com.example.ghostai.model.FormFactor
import com.example.ghostai.ui.theme.GhostAITheme
import com.example.ghostai.ui.theme.LocalWindowClassSize
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: GhostViewModel by viewModels()
    private var recognizerManager: SpeechRecognizerManager? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permission = Manifest.permission.RECORD_AUDIO

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                Timber.d("Microphone permission granted")
                setupRecognizer()
            } else {
                Timber.w("Microphone permission denied")
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                permission,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(permission)
        } else {
            setupRecognizer()
        }

        setContent {
            GhostAITheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                CompositionLocalProvider(
                    LocalWindowClassSize provides windowSizeClass,
                ) {
                    val device = if (windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium) {
                        FormFactor.Tablet
                    } else {
                        FormFactor.Phone
                    }

                    // TODO: May not need this after splitting up the shaders
                    val quality = 2f // when (device) {
//                        FormFactor.Phone -> 2f // high
//                        FormFactor.Tablet -> 0f // low (we’ll make this drive octaves/fps later)
                    //  }
                    val targetFps = 60f // if (device == FormFactor.Tablet) 30f else 60f

                    val deviceSettings = DeviceSettings(
                        device = device,
                        quality = quality,
                        fps = targetFps,
                    )

                    Timber.d("CGH: Device: $device")
                    val ghostUiState by viewModel.ghostUiState.collectAsStateWithLifecycle()
                    MainScreen(
                        deviceSettings = deviceSettings,
                        ghostUiState = ghostUiState,
                        onGhostTouched = {
                            viewModel.onGhostTouched()
                        },
                        onShowVoiceSettings = {
                            viewModel.showSettingsDialog()
                        },
                        onHideVoiceSettings = {
                            viewModel.hideSettingsDialog()
                        },
                        onUpdateVoiceSettings = {
                            viewModel.updateVoiceSettings(it)
                        },
                    )
                }
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
                Timber.e("CGH: Speech error: $message")
                viewModel.onSpeechRecognizerError(error, message)
            },
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
        MainScreen(deviceSettings = DeviceSettings(FormFactor.Phone, 2f, 60f), getGhostUiStatePreviewUiState(), onGhostTouched = {}, onShowVoiceSettings = {}, onHideVoiceSettings = {}, onUpdateVoiceSettings = {})
    }
}
