package com.fourthwardai.ghostai.service

import com.fourthwardai.ghostai.settings.TtsService
import com.fourthwardai.ghostai.settings.Voice

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
