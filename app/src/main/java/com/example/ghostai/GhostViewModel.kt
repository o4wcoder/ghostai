package com.example.ghostai

import android.app.Application
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghostai.model.ConversationState
import com.example.ghostai.model.Emotion
import com.example.ghostai.model.GhostReply
import com.example.ghostai.model.GhostUiState
import com.example.ghostai.model.UserInput
import com.example.ghostai.service.AssistantMessage
import com.example.ghostai.service.ChatMessage
import com.example.ghostai.service.ElevenLabsService
import com.example.ghostai.service.GHOST_ANGRY_PROMPT
import com.example.ghostai.service.OpenAIService
import com.example.ghostai.service.SystemMessage
import com.example.ghostai.service.TtsCallbacks
import com.example.ghostai.service.TtsPreferenceService
import com.example.ghostai.service.UserMessage
import com.example.ghostai.settings.TtsService
import com.example.ghostai.settings.Voice
import com.example.ghostai.settings.VoiceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val application: Application,
) : AndroidViewModel(application) {
    private val tts: TextToSpeech
    private var speechRecognizerManager: SpeechRecognizerManager? = null
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
        observeUserInputQueue()
        observeGhostResponseQueue()
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
                addUserMessage(input)
                val openAiResponse = openAIService.getGhostReply(conversationHistory)
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
    }

    private fun updateConversationState(state: ConversationState) {
        Timber.d("CGH: updateConversationState @ ${System.currentTimeMillis()} - state ${_ghostUiState.value.conversationState}")
        _ghostUiState.update { it.copy(conversationState = state) }
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
