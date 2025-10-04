package com.fourthwardai.ghostai.service

data class TtsCallbacks(
    val onError: (Throwable) -> Unit,
    val onStart: () -> Unit,
    val onEnd: () -> Unit,
)
