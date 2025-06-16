package com.example.ghostai.service

import android.content.Context
import android.media.MediaPlayer
import com.example.ghostai.network.Voice
import com.example.ghostai.network.VoicesResponse
import com.example.ghostai.network.ktorHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

import io.ktor.http.headers
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

private const val ELEVEN_LABS_API_BASE = "https://api.elevenlabs.io/v1"

private const val CHAROLETTE_VOICE_ID = "XB0fDUnXU5powFXDhCwa"
class ElevenLabsService(
    private val apiKey: String,
    private val client: HttpClient = ktorHttpClient()
) {

    suspend fun getAvailableVoices(): List<Voice> {
        val response: HttpResponse = client.get("$ELEVEN_LABS_API_BASE/voices") {
            headers {
                append("xi-api-key", apiKey)
            }
        }

        val json = response.bodyAsText()

        val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val parsed = jsonParser.decodeFromString<VoicesResponse>(json)
        return parsed.voices
    }

    suspend fun synthesizeSpeech(text: String): ByteArray {
        val url = "$ELEVEN_LABS_API_BASE/text-to-speech/XB0fDUnXU5powFXDhCwa"

        val response: HttpResponse = client.post(url) {
//            headers {
//                append("xi-api-key", apiKey)
//                append("accept", "audio/mpeg")
//                contentType(ContentType.Application.Json)
//
//            }
            header("xi-api-key", apiKey) // ✅ safer than append inside `headers {}`
            accept(ContentType.Audio.MPEG)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("text", text)
                    put("model_id", "eleven_monolingual_v1")
                    putJsonObject("voice_settings") {
                        put("stability", 0.5)
                        put("similarity_boost", 0.8)
                    }
                }.toString()
            )
        }

        return response.bodyAsChannel().toByteArray()
    }

    fun playAudio(context: Context, audioData: ByteArray) {
        val tempFile = File.createTempFile("tts", ".mp3", context.cacheDir)
        tempFile.writeBytes(audioData)

        val mediaPlayer = MediaPlayer().apply {
            setDataSource(tempFile.absolutePath)
            prepare()
            start()
        }

        mediaPlayer.setOnCompletionListener {
            tempFile.delete()
            it.release()
        }
    }
}