package com.example.ghostai

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghostai.model.ConversationState
import com.example.ghostai.service.OpenAIService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GhostViewModel @Inject constructor(
    private val openAIService: OpenAIService,
    application: Application) :
    AndroidViewModel(application) {

    private val tts: TextToSpeech
    private var speechRecognizerManager: SpeechRecognizerManager? = null

    private val _conversationState = MutableStateFlow(ConversationState.Idle)
    private val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    val isSpeaking = conversationState
        .map { it == ConversationState.GhostTalking }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                speak("Hello, I am a ghost. I will haunt you forever.")
            }
        }
    }

    fun setSpeechRecognizer(manager: SpeechRecognizerManager) {
        speechRecognizerManager = manager
        maybeRestartListening()
    }

    fun speak(text: String) {
        if (_conversationState.value != ConversationState.Idle || tts.isSpeaking) return

        _conversationState.value = ConversationState.GhostTalking
        speechRecognizerManager?.stopListening()

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ghost-utterance")

        viewModelScope.launch {
            delayWhileSpeaking()
            _conversationState.value = ConversationState.Idle
            maybeRestartListening()
        }
    }

    fun onUserSpeechStart() {
        Timber.d("CGH: onUserSpeechStart()")
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
        viewModelScope.launch {
            result?.let {
                val reply = openAIService.getGhostReply(result)
                speak(reply)
            }
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

