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
import com.example.ghostai.service.ElevenLabsService
import com.example.ghostai.service.OpenAIService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GhostViewModel
@Inject
constructor(
    private val openAIService: OpenAIService,
    private val elevenLabsService: ElevenLabsService,
    private val application: Application,
) : AndroidViewModel(application) {
    private val tts: TextToSpeech
    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private var isRecoveringRecognizer = false
    private val userInputChannel = Channel<UserInput>(Channel.UNLIMITED)
    private val ghostResponseChannel = Channel<GhostReply>(Channel.UNLIMITED)
    private val _ghostUiState = MutableStateFlow(GhostUiState.default())
    val ghostUiState: StateFlow<GhostUiState> = _ghostUiState.asStateFlow()

    init {
        tts =
            TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts.language = Locale.US
                }
            }

        observeUserInputQueue()
        observeGhostResponseQueue()
    }

    private fun observeUserInputQueue() {
        viewModelScope.launch {
            for (input in userInputChannel) {
                val openAiResponse = openAIService.getGhostReply(input)
                ghostResponseChannel.send(openAiResponse)
            }
        }
    }

    private fun observeGhostResponseQueue() {
        viewModelScope.launch {
            for (reply in ghostResponseChannel) {
                withContext(Dispatchers.Main) {
                    stopListening()

                    updateGhostEmotion(reply.emotion)
                    elevenLabsService.startStreamingSpeech(
                        text = reply.text,
                        onError = {
                            Timber.e("CGH: Streaming error: $it")
                            updateConversationState(ConversationState.Idle)
                            maybeRestartListening()
                        },
                        onGhostSpeechEnd = {
                            Timber.d("CGH: Streaming finished")
                            updateConversationState(ConversationState.Idle)
                            maybeRestartListening()
                        },
                        onGhostSpeechStart = {
                            updateConversationState(ConversationState.GhostTalking)
                        },
                    )
                }
            }
        }
    }

    fun setSpeechRecognizer(manager: SpeechRecognizerManager) {
        speechRecognizerManager = manager
        maybeRestartListening()
    }

    fun onUserSpeechStart() {
        Timber.d("CGH: onUserSpeechStart() with state: ${_ghostUiState.value.conversationState}")
        if (_ghostUiState.value.conversationState == ConversationState.Idle) {
            updateConversationState(ConversationState.UserTalking)
        }
    }

    fun onUserSpeechEnd(result: String?) {
        if (_ghostUiState.value.conversationState == ConversationState.UserTalking) {
            updateConversationState(ConversationState.Idle)
        }

        Timber.d("CGH: onUserSpeechEnd: $result")

        if (!result.isNullOrBlank()) {
            userInputChannel.trySend(UserInput.Voice(result))
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

    private fun maybeRestartListening() {
        if (_ghostUiState.value.conversationState == ConversationState.Idle) {
            Timber.d("CGH: maybeRestartListening() called")
            viewModelScope.launch(Dispatchers.Main) {
                speechRecognizerManager?.startListening()
            }
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
        _ghostUiState.update { it.copy(conversationState = state) }
    }
}
