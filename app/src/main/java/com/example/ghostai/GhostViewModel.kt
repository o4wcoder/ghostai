package com.example.ghostai

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GhostViewModel @Inject constructor(private val application: Application) : AndroidViewModel(application) {

    private val tts: TextToSpeech
    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

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
        speechRecognizerManager?.startListening()
    }

    fun speak(text: String) {
        if (tts.isSpeaking) return

                tts.voices.forEach { voice ->
            Timber.d("Voice: ${voice.name}, Locale: ${voice.locale}, Features: ${voice.features}")
        }

        _isSpeaking.value = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ghost-utterance")
        viewModelScope.launch {
            delayWhileSpeaking()
            _isSpeaking.value = false
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

