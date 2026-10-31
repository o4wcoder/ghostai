package com.fourthwardai.ghostai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fourthwardai.ghostai.camera.GhostCameraManager
import com.fourthwardai.ghostai.camera.VisitorDetector
import com.fourthwardai.ghostai.model.DeviceSettings
import com.fourthwardai.ghostai.model.FormFactor
import com.fourthwardai.ghostai.ui.theme.GhostAITheme
import com.fourthwardai.ghostai.ui.theme.LocalWindowClassSize
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: GhostViewModel by viewModels()
    private var recognizerManager: SpeechRecognizerManager? = null
    private var cameraManager: GhostCameraManager? = null
    private var visitorDetector: VisitorDetector? = null
    private var isPermissionRequestInFlight = false

    // Null until the saved vision setting has loaded, so we never act on the default before knowing the user's choice
    private var visionEnabled: Boolean? = null
    private var greetVisitorsEnabled = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        isPermissionRequestInFlight = false
        results[Manifest.permission.RECORD_AUDIO]?.let { isGranted ->
            if (isGranted) {
                Timber.d("Microphone permission granted")
                setupRecognizer()
            } else {
                Timber.w("Microphone permission denied")
            }
        }
        val cameraResult = results[Manifest.permission.CAMERA]
        when (cameraResult) {
            true -> Timber.d("CGH: Camera permission granted")
            false -> Timber.w("CGH: Camera permission denied, ghost will respond without sight")
            null -> {}
        }
        // If this was only a mic request, a camera request may have been deferred while it was showing, so allow one now.
        // If the camera was just answered, don't ask again (avoids re-prompting in a loop after a denial).
        visionEnabled?.let { updateCameraBinding(it, requestPermissionIfNeeded = cameraResult == null) }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide system bars
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraManager = GhostCameraManager(this).also { viewModel.setCameraManager(it) }
        visitorDetector = VisitorDetector(this).also { viewModel.setVisitorDetector(it) }

        // The camera permission is requested by observeVisionSetting() once the saved vision setting is known
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            setupRecognizer()
        } else {
            isPermissionRequestInFlight = true
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        observeVisionSetting()

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
                        onDismissMissingOpenAIKeyDialog = {
                            viewModel.dismissMissingOpenAIKeyDialog()
                        },
                        onUpdateVoiceSettings = {
                            viewModel.updateVoiceSettings(it)
                        },
                        onUpdateVisionEnabled = {
                            viewModel.updateVisionEnabled(it)
                        },
                        onUpdateGreetVisitorsEnabled = {
                            viewModel.updateGreetVisitorsEnabled(it)
                        },
                    )
                }
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Binds the camera while vision is enabled and unbinds it when disabled, so the camera isn't held open needlessly.
     * Greet Visitors adds a continuous analysis stream on top, so it's rebound when that setting changes too.
     */
    private fun observeVisionSetting() {
        lifecycleScope.launch {
            combine(viewModel.savedVisionEnabled, viewModel.savedGreetVisitorsEnabled) { vision, greet -> vision to greet }
                .distinctUntilChanged()
                .collect { (vision, greet) ->
                    visionEnabled = vision
                    greetVisitorsEnabled = greet
                    updateCameraBinding(vision, requestPermissionIfNeeded = true)
                }
        }
    }

    private fun updateCameraBinding(visionEnabled: Boolean, requestPermissionIfNeeded: Boolean) {
        val manager = cameraManager ?: return
        val analyzeVisitors = visionEnabled && greetVisitorsEnabled
        // Free the detection model while it isn't needed; it's recreated on the first analyzed frame
        if (!analyzeVisitors) visitorDetector?.reset()
        when {
            !visionEnabled -> manager.unbind()
            hasPermission(Manifest.permission.CAMERA) ->
                manager.bindToLifecycle(this, visitorAnalyzer = visitorDetector.takeIf { analyzeVisitors })
            requestPermissionIfNeeded && !isPermissionRequestInFlight -> {
                isPermissionRequestInFlight = true
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
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
        cameraManager?.release()
        visitorDetector?.reset()
        super.onDestroy()
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GhostAITheme {
        MainScreen(
            deviceSettings = DeviceSettings(FormFactor.Phone, 2f, 60f),
            getGhostUiStatePreviewUiState(),
            onGhostTouched = {},
            onShowVoiceSettings = {},
            onHideVoiceSettings = {},
            onDismissMissingOpenAIKeyDialog = {},
            onUpdateVoiceSettings = {},
            onUpdateVisionEnabled = {},
            onUpdateGreetVisitorsEnabled = {},
        )
    }
}
