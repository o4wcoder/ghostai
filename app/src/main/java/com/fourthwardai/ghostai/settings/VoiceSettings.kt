package com.fourthwardai.ghostai.settings

data class VoiceSettings(
    val selectedService: TtsService,
    val selectedVoiceId: String,
    val voicesByService: Map<TtsService, List<Voice>>,
)
