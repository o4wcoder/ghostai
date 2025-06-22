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
class GhostViewModel @Inject constructor(
    private val openAIService: OpenAIService,
    private val elevenLabsService: ElevenLabsService,
    private val application: Application
) :
    AndroidViewModel(application) {

    private val tts: TextToSpeech
    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private var isRecoveringRecognizer = false

    private val _conversationState = MutableStateFlow(ConversationState.Idle)
    private val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    val isSpeaking = conversationState
        .map { it == ConversationState.GhostTalking }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                //  speak("Hello, I am a ghost. I will haunt you forever.")
            }
        }
    }

    suspend fun speakWithCustomVoice(result: ElevenLabsSpeechToTextResult) {
        if (result is ElevenLabsSpeechToTextResult.Success) {
            withContext(Dispatchers.Main) {
                // Indicate that the ghost is speaking (triggers mouth animation)
                _conversationState.value = ConversationState.GhostTalking

                elevenLabsService.playAudio(
                    application, result.audioData,
                    onComplete = {
                        // Ghost is done speaking
                        _conversationState.value = ConversationState.Idle
                    },
                    onError = {
                        Timber.e("Error during playback: $it")
                        _conversationState.value = ConversationState.Idle
                    }
                )
            }
        } else {
            Timber.e("Failed to synthesize voice: ${(result as ElevenLabsSpeechToTextResult.Failure).errorMessage}")
            _conversationState.value = ConversationState.Idle
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
            maybeRestartListening()
        }

        Timber.d("CGH: onUserSpeechEnd: $result")
        if (!result.isNullOrBlank()) {
            viewModelScope.launch {
                val openAiResponse = async { openAIService.getGhostReply(result) }

                val elevenLabsAudio = async {
                    val text = openAiResponse.await()
                    Timber.d("CGH: OpenAI response: $text")
                    elevenLabsService.synthesizeSpeech(text)
                }

                speakWithCustomVoice(elevenLabsAudio.await())
            }
        } else {
            // If result is null or blank, restart the mic directly
            Timber.d("CGH: null response from speech recognizer. Restarting mic.")
            viewModelScope.launch {
                delay(500) // Give the recognizer a brief pause to reset
                speechRecognizerManager?.stopListening()
                delay(250)
                speechRecognizerManager?.startListening()
            }
        }
    }

    fun onSpeechRecognizerError(code: Int, message: String) {
        Timber.e("SpeechRecognizer error $code: $message")

        // Ensure we exit user-talking state
        if (_conversationState.value == ConversationState.UserTalking) {
            onUserSpeechEnd(null)
        }

        // Prevent recursive or rapid repeated resets
        if (isRecoveringRecognizer) {
            Timber.w("Already recovering recognizer — skipping duplicate reset.")
            return
        }

        when (code) {
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                isRecoveringRecognizer = true
                // These typically require full reset
                speechRecognizerManager?.destroy()
                speechRecognizerManager = SpeechRecognizerManager(
                    application,
                    onStart = { onUserSpeechStart() },
                    onResult = { onUserSpeechEnd(it) },
                    onError = { code, msg -> onSpeechRecognizerError(code, msg) }
                )
            }

            else -> {
                // Recoverable errors — just restart
                Timber.d("Handling recoverable recognizer error: $code")

                // Delay and try to recover
                viewModelScope.launch {
                    delay(500)
                    speechRecognizerManager?.stopListening()
                    delay(250)
                    speechRecognizerManager?.startListening()
                }
            }
        }
    }


    fun restartRecognizerWithDelay() {
        viewModelScope.launch {
            delay(500)
            speechRecognizerManager?.stopListening()
            delay(100)
            speechRecognizerManager?.startListening()
        }
    }

    private fun maybeRestartListening() {
        if (_conversationState.value == ConversationState.Idle) {
            speechRecognizerManager?.startListening()
        }
    }

    fun stopListening() {
        speechRecognizerManager?.stopListening()
    }

    private suspend fun delayWhileSpeaking() {
        while (tts.isSpeaking) {
            delay(100)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
        speechRecognizerManager?.destroy()
    }
}
