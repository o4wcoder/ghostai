package com.fourthwardai.ghostai.model

data class GhostReply(
    val emotion: Emotion,
    val text: String,
)

enum class Emotion(val id: Float) {
    // Amber
    Neutral(0f),

    // Red
    Angry(1f),

    // Yellow
    Happy(2f),

    // Blue
    Sad(3f),

    // Purple
    Spooky(4f),

    // Green
    Funny(5f),
}
