package com.example.ghostai.service

import com.example.ghostai.settings.TtsService
import com.example.ghostai.settings.Voice

interface AIService {

    // Checks if the service is available (e.g., API key is set and valid)
    fun isAvailable(): Boolean

    // Gets a list of available voices for each TTS service
    fun getVoices(): Map<TtsService, List<Voice>>

    // Gets the default voice ID for a given TTS service
    fun getDefaultVoiceId(): String

    suspend fun startStreamingSpeech(
        text: String,
        voiceId: String,
        callbacks: TtsCallbacks,
    )
}
