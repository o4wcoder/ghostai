package com.example.ghostai.model

sealed interface UserInput {
    data class Voice(val text: String) : UserInput
    data object Touch : UserInput
}
