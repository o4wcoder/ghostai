package com.example.ghostai.settings

enum class TtsService { ELEVENLABS, OPENAI }

data class Voice(
    val id: String,
    val displayName: String,
)
