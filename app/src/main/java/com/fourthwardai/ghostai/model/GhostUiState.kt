package com.fourthwardai.ghostai.model

import com.fourthwardai.ghostai.service.ElevenLabsVoiceIds
import com.fourthwardai.ghostai.settings.TtsService
import com.fourthwardai.ghostai.settings.VoiceSettings

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
