package com.example.ghostai.service

import com.example.ghostai.BuildConfig
import com.example.ghostai.network.ktorHttpClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.*

private val LLM_MODEL = "gpt-4o"

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String = LLM_MODEL,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.9
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice>
)

class OpenAIService(
    private val apiKey: String,
    private val client: HttpClient = ktorHttpClient()
) {

    suspend fun getGhostReply(userPrompt: String): String {
        val messages = listOf(
            ChatMessage("system", "You are a sarcastic, witty ghost named Whisper who haunts a foggy glade and loves playful banter."),
            ChatMessage("user", userPrompt)
        )

        val request = ChatCompletionRequest(
            model = LLM_MODEL,
            messages = messages)

        return try {
            val response: HttpResponse = client.post("https://api.openai.com/v1/chat/completions") {
                headers {
                    append("Authorization", "Bearer $apiKey")
                }
                setBody(request)
            }

            val completion = response.body<ChatCompletionResponse>()
            completion.choices.firstOrNull()?.message?.content ?: "Boo... I'm speechless!"
        } catch (e: Exception) {
            e.printStackTrace()
            "A chill runs down my circuits... something went wrong!"
        }
    }
}

