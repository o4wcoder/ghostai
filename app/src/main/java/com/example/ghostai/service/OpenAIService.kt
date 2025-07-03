package com.example.ghostai.service

import com.example.ghostai.model.UserInput
import com.example.ghostai.network.ktorHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable

private val LLM_MODEL = "gpt-4o"

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String = LLM_MODEL,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.9,
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice>,
)

class OpenAIService(
    private val apiKey: String,
    private val client: HttpClient = ktorHttpClient(),
) {

    suspend fun getGhostReply(userPrompt: UserInput): String {
        val messages = when (userPrompt) {
            is UserInput.Voice -> {
                listOf(
                    ChatMessage("system", "You are a curious ghost named Whisper who haunts a foggy glade and is mischievous. Keep your responses brief, spooky, witty, sarcastic — no more than one or two sentences."),
                    ChatMessage("user", userPrompt.text),
                )
            }
            is UserInput.Touch -> {
                listOf(
                    ChatMessage("system", "You are a ghost who has been disturbed by the user's touch. Respond in an angry, spooky manner, perhaps with a threat or eerie warning. Make responses short"),
                )
            }
        }

        val request = ChatCompletionRequest(
            model = LLM_MODEL,
            messages = messages,
        )

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
