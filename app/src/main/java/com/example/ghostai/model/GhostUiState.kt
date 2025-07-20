package com.example.ghostai.model

data class GhostUiState(
    val conversationState: ConversationState,
    val startEmotion: Emotion,
    val targetEmotion: Emotion,
) {
    val isSpeaking: Boolean = conversationState == ConversationState.GhostTalking

    companion object {
        fun default() = GhostUiState(
            conversationState = ConversationState.Idle,
            startEmotion = Emotion.Neutral,
            targetEmotion = Emotion.Neutral,
        )
    }
}
