package com.example.ghostai

import android.app.Application
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghostai.model.ConversationState
import com.example.ghostai.network.model.ElevenLabsSpeechToTextResult
import com.example.ghostai.service.ElevenLabsService
import com.example.ghostai.service.OpenAIService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private var isProcessingResponse = false

    private val _conversationState = MutableStateFlow(ConversationState.Idle)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    val isSpeaking =
        conversationState
            .map { it == ConversationState.GhostTalking }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        tts =
            TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts.language = Locale.US
                }
            }
    }

    fun setSpeechRecognizer(manager: SpeechRecognizerManager) {
        speechRecognizerManager = manager
        maybeRestartListening()
    }

    fun onUserSpeechStart() {
        Timber.d("CGH: onUserSpeechStart() with state: ${_conversationState.value}")
        if (_conversationState.value == ConversationState.Idle) {
            _conversationState.value = ConversationState.UserTalking
        }
    }

    fun onUserSpeechEnd(result: String?) {
        if (_conversationState.value == ConversationState.UserTalking) {
            _conversationState.value = ConversationState.Idle
        }

        Timber.d("CGH: onUserSpeechEnd: $result")

        if (!result.isNullOrBlank() && !isProcessingResponse) {
            isProcessingResponse = true
            viewModelScope.launch {
                val openAiResponse = async { openAIService.getGhostReply(result) }

                val elevenLabsAudio =
                    async {
                        val text = openAiResponse.await().trim()
                        Timber.d("CGH: OpenAI response: $text")
                        elevenLabsService.synthesizeSpeech(text)
                    }

                speakWithCustomVoice(elevenLabsAudio.await())
                isProcessingResponse = false
            }
        } else {
            Timber.d("CGH: null or blank user speech — restarting recognizer")
            viewModelScope.launch {
                delay(500)
                speechRecognizerManager?.stopListening()
                delay(250)
                maybeRestartListening()
            }
        }
    }

    suspend fun speakWithCustomVoice(result: ElevenLabsSpeechToTextResult) {
        if (result is ElevenLabsSpeechToTextResult.Success) {
            withContext(Dispatchers.Main) {
                _conversationState.value = ConversationState.GhostTalking

                speechRecognizerManager?.stopListening()

                elevenLabsService.playAudio(
                    application,
                    result.audioData,
                    onComplete = {
                        _conversationState.value = ConversationState.Idle
                        maybeRestartListening()
                    },
                    onError = {
                        Timber.e("CGH: Playback error: $it")
                        _conversationState.value = ConversationState.Idle
                        maybeRestartListening()
                    },
                )
            }
        } else {
            Timber.e("CGH: TTS synthesis failed: ${(result as ElevenLabsSpeechToTextResult.Failure).errorMessage}")
            _conversationState.value = ConversationState.Idle
            maybeRestartListening()
        }
    }

    fun onSpeechRecognizerError(
        code: Int,
        message: String,
    ) {
        Timber.e("CGH: SpeechRecognizer error $code: $message")

        if (_conversationState.value == ConversationState.UserTalking) {
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
                viewModelScope.launch {
                    delay(500)
                    speechRecognizerManager?.stopListening()
                    delay(250)
                    maybeRestartListening()
                }
            }
        }
    }

    private fun maybeRestartListening() {
        if (_conversationState.value == ConversationState.Idle) {
            Timber.d("CGH: maybeRestartListening() called")
            speechRecognizerManager?.startListening()
        }
    }

    fun stopListening() {
        speechRecognizerManager?.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
        speechRecognizerManager?.destroy()
    }
}
