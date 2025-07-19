package com.example.ghostai.model

data class GhostUiState(
    val conversationState: ConversationState,
    val emotion: Emotion,
) {
    val isSpeaking: Boolean = conversationState == ConversationState.GhostTalking
}
