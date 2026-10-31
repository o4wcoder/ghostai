package com.fourthwardai.ghostai

import android.app.Application
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fourthwardai.ghostai.camera.GhostCameraManager
import com.fourthwardai.ghostai.camera.VisionPreferenceService
import com.fourthwardai.ghostai.camera.VisitorDetector
import com.fourthwardai.ghostai.model.ConversationState
import com.fourthwardai.ghostai.model.Emotion
import com.fourthwardai.ghostai.model.GhostReply
import com.fourthwardai.ghostai.model.GhostUiState
import com.fourthwardai.ghostai.model.UserInput
import com.fourthwardai.ghostai.service.AssistantMessage
import com.fourthwardai.ghostai.service.ChatMessage
import com.fourthwardai.ghostai.service.ElevenLabsService
import com.fourthwardai.ghostai.service.GHOST_ANGRY_PROMPT
import com.fourthwardai.ghostai.service.HueLightService
import com.fourthwardai.ghostai.service.OpenAIService
import com.fourthwardai.ghostai.service.SystemMessage
import com.fourthwardai.ghostai.service.TtsCallbacks
import com.fourthwardai.ghostai.service.TtsPreferenceService
import com.fourthwardai.ghostai.service.UserMessage
import com.fourthwardai.ghostai.settings.TtsService
import com.fourthwardai.ghostai.settings.Voice
import com.fourthwardai.ghostai.settings.VoiceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

private const val MAX_NUM_MESSAGES = 30
private const val USER_TALKING_TIMEOUT_MS = 7000L

@HiltViewModel
class GhostViewModel
@Inject
constructor(
    private val openAIService: OpenAIService,
    private val elevenLabsService: ElevenLabsService,
    private val ttsPrefs: TtsPreferenceService,
    private val visionPrefs: VisionPreferenceService,
    private val hueLightService: HueLightService,
    private val application: Application,
) : AndroidViewModel(application) {
    private val tts: TextToSpeech
    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private var ghostCameraManager: GhostCameraManager? = null
    private var visitorDetector: VisitorDetector? = null
    private var isRecoveringRecognizer = false
    private var userTalkingTimeoutJob: Job? = null

    private val userInputChannel = Channel<UserInput>(Channel.UNLIMITED)
    private val ghostResponseChannel = Channel<GhostReply>(Channel.UNLIMITED)
    private val _ghostUiState = MutableStateFlow(GhostUiState.default())
    val ghostUiState: StateFlow<GhostUiState> = _ghostUiState.asStateFlow()

    var conversationHistory = mutableListOf<ChatMessage>()

    init {
        tts =
            TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts.language = Locale.US
                }
            }
        loadVoiceSettings()
        loadVisionSetting()
        observeUserInputQueue()
        observeGhostResponseQueue()

//        viewModelScope.launch(Dispatchers.IO) {
//           val lights = hueLightService.getLights()
//           // Timber.d("CGH: Hue lights: $lights")
//            lights.forEach { light ->
//                Timber.d("CGH: Light name: ${light.value.name} id: ${light.key}\n")
//               // if(light.value.name) {)
//
//            }
//
//
//            hueLightService.setLightState(lightId = "37", on = false)
//        }
    }

    val ttsCallbacks = TtsCallbacks(
        onError = {
            Timber.e("CGH: Streaming error: $it")
            updateConversationState(ConversationState.Idle)
            maybeRestartListening()
        },
        onStart = {
            Timber.d("CGH: onGhostSpeechStart() @ ${System.currentTimeMillis()} - state ${_ghostUiState.value.conversationState}")
            updateConversationState(ConversationState.GhostTalking)
        },
        onEnd = {
            Timber.d("CGH: onGhostSpeechEnd() @ ${System.currentTimeMillis()} - state ${_ghostUiState.value.conversationState}")
            updateConversationState(ConversationState.Idle)
            maybeRestartListening()
        },
    )

    private fun loadVoiceSettings() {
        if (!openAIService.isAvailable()) {
            _ghostUiState.update { it.copy(showMissingOpenAIKeyDialog = true) }
            return
        }

        val defaultService = if (elevenLabsService.isAvailable()) TtsService.ELEVENLABS else TtsService.OPENAI
        val defaultVoiceId = if (elevenLabsService.isAvailable()) elevenLabsService.getDefaultVoiceId() else openAIService.getDefaultVoiceId()

        viewModelScope.launch {
            combine(ttsPrefs.selectedService, ttsPrefs.selectedVoiceId) { service, voiceId ->
                val voicesByService = loadVoicesByService()
                val selectedService = service ?: defaultService
                val selectedVoiceId = voiceId ?: defaultVoiceId
                _ghostUiState.update { it.copy(voiceSettings = VoiceSettings(selectedService, selectedVoiceId, voicesByService)) }
            }
                .collect()
        }
    }

    /** The saved vision setting. Unlike [ghostUiState], it never emits the default before the saved value has loaded. */
    val savedVisionEnabled: Flow<Boolean> = visionPrefs.visionEnabled

    private fun loadVisionSetting() {
        viewModelScope.launch {
            visionPrefs.visionEnabled.collect { enabled ->
                _ghostUiState.update { it.copy(visionEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            visionPrefs.greetVisitorsEnabled.collect { enabled ->
                _ghostUiState.update { it.copy(greetVisitorsEnabled = enabled) }
            }
        }
    }

    fun updateVisionEnabled(enabled: Boolean) {
        Timber.d("CGH: Vision enabled = $enabled")
        _ghostUiState.update { it.copy(visionEnabled = enabled) }
        viewModelScope.launch {
            visionPrefs.setVisionEnabled(enabled)
        }
    }

    /** The saved greet-visitors setting. Like [savedVisionEnabled], it never emits a default before loading. */
    val savedGreetVisitorsEnabled: Flow<Boolean> = visionPrefs.greetVisitorsEnabled

    fun updateGreetVisitorsEnabled(enabled: Boolean) {
        Timber.d("CGH: Greet visitors enabled = $enabled")
        _ghostUiState.update { it.copy(greetVisitorsEnabled = enabled) }
        viewModelScope.launch {
            visionPrefs.setGreetVisitorsEnabled(enabled)
        }
    }

    fun showSettingsDialog() {
        _ghostUiState.update { it.copy(showSettingsDialog = true) }
    }
    fun hideSettingsDialog() {
        _ghostUiState.update { it.copy(showSettingsDialog = false) }
    }

    fun dismissMissingOpenAIKeyDialog() {
        _ghostUiState.update { it.copy(showMissingOpenAIKeyDialog = false) }
    }

    fun updateVoiceSettings(voiceSettings: VoiceSettings) {
        _ghostUiState.update {
            it.copy(
                showSettingsDialog = false,
                voiceSettings = voiceSettings,
            )
        }

        viewModelScope.launch {
            ttsPrefs.saveSelection(voiceSettings.selectedService, voiceSettings.selectedVoiceId)
        }
    }
    private fun observeUserInputQueue() {
        viewModelScope.launch {
            for (input in userInputChannel) {
                // Start the capture first so it runs while the message is queued up. Only voice turns get an image:
                // a touch is sent as a system prompt, so there is no user message for the image to attach to.
                val imageDeferred = async(Dispatchers.IO) {
                    if (input is UserInput.Voice && _ghostUiState.value.visionEnabled) {
                        Timber.d("CGH: Starting image capture @ ${System.currentTimeMillis()}")
                        ghostCameraManager?.captureImage()
                    } else {
                        null
                    }
                }
                addUserMessage(input)
                // The user may have turned vision off while the frame was being captured/encoded; honor that
                val imageBase64 = imageDeferred.await()?.takeIf { _ghostUiState.value.visionEnabled }
                Timber.d("CGH: Calling getGhostReply @ ${System.currentTimeMillis()}, image = ${imageBase64 != null}")
                val openAiResponse = openAIService.getGhostReply(conversationHistory, imageBase64)
                ghostResponseChannel.send(openAiResponse)
            }
        }
    }

    private fun observeGhostResponseQueue() {
        viewModelScope.launch {
            for (reply in ghostResponseChannel) {
                withContext(Dispatchers.Main) {
                    stopListening()

                    addGhostReply(reply)
                    updateGhostEmotion(reply.emotion)

                    when (_ghostUiState.value.voiceSettings.selectedService) {
                        TtsService.OPENAI -> {
                            openAIService.startStreamingSpeech(
                                text = reply.text,
                                callbacks = ttsCallbacks,
                                voiceId = _ghostUiState.value.voiceSettings.selectedVoiceId,
                            )
                        }
                        TtsService.ELEVENLABS -> {
                            elevenLabsService.startStreamingSpeech(
                                text = reply.text,
                                callbacks = ttsCallbacks,
                                voiceId = _ghostUiState.value.voiceSettings.selectedVoiceId,
                            )
                        }
                    }
                }
            }
        }
    }

    fun setCameraManager(manager: GhostCameraManager) {
        ghostCameraManager = manager
    }

    fun setVisitorDetector(detector: VisitorDetector) {
        visitorDetector = detector
        detector.isPaused = _ghostUiState.value.conversationState != ConversationState.Idle
    }

    fun setSpeechRecognizer(manager: SpeechRecognizerManager) {
        speechRecognizerManager = manager
        maybeRestartListening()
    }

    fun onUserSpeechStart() {
        Timber.d("CGH: onUserSpeechStart() @ ${System.currentTimeMillis()} - state ${_ghostUiState.value.conversationState}")
        if (_ghostUiState.value.conversationState == ConversationState.Idle) {
            updateConversationState(ConversationState.UserTalking)
        }

        /**
         *   Start a timer to timeout in USER_TALKING_TIMEOUT_MS seconds if the speech recognizer gets stuck and we
         *   don't get a success or failure from it. Then restart it.
         */
        startUserTalkingWatchdogTimeout()
    }

    fun onUserSpeechEnd(result: String?) {
        Timber.d("CGH: onUserSpeechEnd() @ ${System.currentTimeMillis()} - state ${_ghostUiState.value.conversationState}")
        if (_ghostUiState.value.conversationState == ConversationState.UserTalking) {
            updateConversationState(ConversationState.Idle)
        }

        if (!result.isNullOrBlank()) {
            if (_ghostUiState.value.conversationState != ConversationState.GhostTalking) {
                userInputChannel.trySend(UserInput.Voice(result))
            } else {
                Timber.d("CGH: Ignoring user input — ghost is busy: ${_ghostUiState.value.conversationState}")
            }
        } else {
            restartRecognizerWithDelay()
        }
    }

    fun onGhostTouched() {
        if (_ghostUiState.value.conversationState == ConversationState.Idle) {
            updateConversationState(ConversationState.ProcessingTouch)
            userInputChannel.trySend(UserInput.Touch)
        } else {
            Timber.d("CGH: Ignoring touch — ghost is busy: ${_ghostUiState.value.conversationState}")
        }
    }

    fun onSpeechRecognizerError(
        code: Int,
        message: String,
    ) {
        Timber.e("CGH: SpeechRecognizer error $code: $message")

        if (_ghostUiState.value.conversationState == ConversationState.UserTalking) {
            onUserSpeechEnd(null)
        }

        if (isRecoveringRecognizer) {
            Timber.w("CGH: Already recovering recognizer — skipping reset.")
            return
        }

        when (code) {
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            -> {
                isRecoveringRecognizer = true
                speechRecognizerManager?.destroy()
                speechRecognizerManager =
                    SpeechRecognizerManager(
                        application,
                        onStart = { onUserSpeechStart() },
                        onResult = { onUserSpeechEnd(it) },
                        onError = { errCode, msg -> onSpeechRecognizerError(errCode, msg) },
                    )

                viewModelScope.launch {
                    delay(1000)
                    isRecoveringRecognizer = false
                    maybeRestartListening()
                }
            }

            else -> {
                restartRecognizerWithDelay()
            }
        }
    }

    private fun startUserTalkingWatchdogTimeout() {
        userTalkingTimeoutJob?.cancel()
        userTalkingTimeoutJob = viewModelScope.launch {
            delay(USER_TALKING_TIMEOUT_MS)
            if (ghostUiState.value.conversationState == ConversationState.UserTalking) {
                Timber.w("UserTalking timed out → forcing Idle + restart")
                updateConversationState(ConversationState.Idle)
                stopListening()
                delay(200)
                maybeRestartListening()
            }
        }
    }

    private fun maybeRestartListening() {
        Timber.d("CGH: maybeRestartListening() @ ${System.currentTimeMillis()} - state ${_ghostUiState.value.conversationState}")
        if (_ghostUiState.value.conversationState == ConversationState.Idle) {
            viewModelScope.launch(Dispatchers.Main) {
                speechRecognizerManager?.startListening()
            }
        } else {
            Timber.d("CGH: maybeRestartListening() called, but Skipping recognizer start — state: ${_ghostUiState.value.conversationState}")
        }
    }

    private fun restartRecognizerWithDelay() {
        viewModelScope.launch {
            delay(500)
            stopListening()
            delay(250)
            maybeRestartListening()
        }
    }

    fun stopListening() {
        viewModelScope.launch(Dispatchers.Main) {
            speechRecognizerManager?.stopListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
        speechRecognizerManager?.destroy()
    }

    private fun updateGhostEmotion(newEmotion: Emotion) {
        val currentTarget = _ghostUiState.value.targetEmotion
        _ghostUiState.value = _ghostUiState.value.copy(
            startEmotion = currentTarget,
            targetEmotion = newEmotion,
        )
        if (currentTarget != newEmotion) {
            updateLightWithEmotion(newEmotion)
        }
    }

    private fun updateLightWithEmotion(emotion: Emotion) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (emotion) {
                    // TODO: Put these int he Emotion enum
                    Emotion.Happy -> updateLightColor(hue = 12000, sat = 200)
                    Emotion.Sad -> updateLightColor(hue = 46920, sat = 200)
                    Emotion.Angry -> updateLightColor(hue = 0, sat = 254)
                    Emotion.Neutral -> updateLightColor(hue = 9000, sat = 128)
                    Emotion.Spooky -> updateLightColor(hue = 25000, sat = 254)
                    Emotion.Funny -> updateLightColor(56100, sat = 200)
                }
            } catch (e: Exception) {
                Timber.e("CGH: Failed to update Hue light for emotion $emotion: ${e.message}")
            }
        }
    }

    private suspend fun updateLightColor(hue: Int, sat: Int) {
        try {
            hueLightService.setLightState(
                lightId = "37",
                on = true,
                bri = 100,
                hue = hue,
                sat = sat,
            )
        } catch (e: Exception) {
            Timber.e("CGH: Failed to update Hue light color: ${e.message}")
        }
    }

    private fun updateConversationState(state: ConversationState) {
        Timber.d("CGH: updateConversationState @ ${System.currentTimeMillis()} - state ${_ghostUiState.value.conversationState}")
        _ghostUiState.update { it.copy(conversationState = state) }
        // Visitors are only greeted when idle, so skip detection (and its CPU cost) the rest of the time
        visitorDetector?.isPaused = state != ConversationState.Idle
    }

    fun addUserMessage(userInput: UserInput) {
        when (userInput) {
            is UserInput.Voice -> conversationHistory.add(
                UserMessage(
                    userInput.text,
                ),
            )

            is UserInput.Touch -> conversationHistory.add(SystemMessage(GHOST_ANGRY_PROMPT))
        }
    }

    fun addGhostReply(reply: GhostReply) {
        conversationHistory.add(AssistantMessage(content = reply.text))
        summarizeConversationIfNeeded()
    }

    // TODO: This is being called twice in a row
    fun summarizeConversationIfNeeded() {
        if (conversationHistory.size <= MAX_NUM_MESSAGES) return

        val toSummarize = conversationHistory.take(MAX_NUM_MESSAGES / 2)
        val summarizePrompt = listOf(SystemMessage("Summarize this conversation between a ghost and a user.")) + toSummarize

        viewModelScope.launch {
            val summary = openAIService.getGhostReply(summarizePrompt)
            Timber.d("CGH: summary: ${summary.text}")
            val remaining = conversationHistory.drop(toSummarize.size)
            conversationHistory.clear()
            conversationHistory.add(AssistantMessage(content = "Earlier in the conversation: ${summary.text}"))
            conversationHistory.addAll(remaining)
        }
    }

    private fun loadVoicesByService(): Map<TtsService, List<Voice>> {
        val elevenLabsVoices = elevenLabsService.getVoices()
        val openAiVoices = openAIService.getVoices()
        return elevenLabsVoices + openAiVoices
    }
}
