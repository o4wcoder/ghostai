package com.fourthwardai.ghostai.network.model

sealed interface ElevenLabsSpeechToTextResult {
    data class Success(val audioData: ByteArray) : ElevenLabsSpeechToTextResult

    data class Failure(val errorMessage: String) : ElevenLabsSpeechToTextResult
}
