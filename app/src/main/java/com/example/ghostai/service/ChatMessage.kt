package com.example.ghostai.service

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("role")
sealed interface ChatMessage {
    val role: String
    val content: String
}

@Serializable
@SerialName("system")
data class SystemMessage(
    override val content: String,
) : ChatMessage {
    override val role: String get() = "system"
}

@Serializable
@SerialName("user")
data class UserMessage(
    override val content: String,
) : ChatMessage {
    override val role: String get() = "user"
}

@Serializable
@SerialName("assistant")
data class AssistantMessage(
    override val content: String,
) : ChatMessage {
    override val role: String get() = "assistant"
}
