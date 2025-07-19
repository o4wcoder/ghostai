package com.example.ghostai.model

data class GhostReply(
    val emotion: Emotion,
    val text: String,
)

enum class Emotion(val id: Float) {
    Neutral(0f),
    Angry(1f),
    Happy(2f),
    Sad(3f),
    Spooky(4f),
    Funny(5f),
}
