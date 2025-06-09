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
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GhostViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    private val tts: TextToSpeech
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

    fun speak(text: String) {
        if (tts.isSpeaking) return

            _isSpeaking.value = true
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ghost-utterance")
        // Monitor when it stops
        viewModelScope.launch {
            delayWhileSpeaking()
            _isSpeaking.value = false
        }
    }

    private suspend fun delayWhileSpeaking() {
        while (tts.isSpeaking) {
            delay(100)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
    }
}
