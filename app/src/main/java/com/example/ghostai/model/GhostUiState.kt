package com.example.ghostai.model

import com.example.ghostai.service.ElevenLabsVoiceIds
import com.example.ghostai.settings.TtsService
import com.example.ghostai.settings.VoiceSettings

data class GhostUiState(
    val conversationState: ConversationState,
    val startEmotion: Emotion,
    val targetEmotion: Emotion,
    val showSettingsDialog: Boolean = false,
    val showMissingOpenAIKeyDialog: Boolean = false,
    val voiceSettings: VoiceSettings,
) {
    val isSpeaking: Boolean = conversationState == ConversationState.GhostTalking

    companion object {
        fun default() = GhostUiState(
            conversationState = ConversationState.Idle,
            startEmotion = Emotion.Neutral,
            targetEmotion = Emotion.Neutral,
            voiceSettings = VoiceSettings(
                selectedService = TtsService.ELEVENLABS,
                selectedVoiceId = ElevenLabsVoiceIds.CHAROLETTE_VOICE_ID,
                voicesByService = mapOf(),
            ),
        )
    }
}
