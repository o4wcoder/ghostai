package com.example.ghostai.service

import android.content.Context
import android.media.MediaPlayer
import com.example.ghostai.network.ktorHttpClient
import com.example.ghostai.network.model.ElevenLabsSpeechToTextResult
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
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.io.File

private const val ELEVEN_LABS_API_BASE = "https://api.elevenlabs.io/v1"

private const val CHAROLETTE_VOICE_ID = "XB0fDUnXU5powFXDhCwa"
private const val DEMON_MONSTER_VOICE_ID = "vfaqCOvlrKi4Zp7C2IAm"

class ElevenLabsService(
    private val apiKey: String,
    private val client: HttpClient = ktorHttpClient(),
) {
    private var mediaPlayer: MediaPlayer? = null

    suspend fun synthesizeSpeech(text: String): ElevenLabsSpeechToTextResult {
        val url = "$ELEVEN_LABS_API_BASE/text-to-speech/$CHAROLETTE_VOICE_ID"

        return try {
            val response: HttpResponse =
                client.post(url) {
                    header("xi-api-key", apiKey)
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
                        }.toString(),
                    )
                }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                ElevenLabsSpeechToTextResult.Failure("HTTP ${response.status.value}: $errorBody")
            } else {
                val audioBytes = response.bodyAsChannel().toByteArray()
                ElevenLabsSpeechToTextResult.Success(audioBytes)
            }
        } catch (e: Exception) {
            Timber.e(e, "TTS failed")
            ElevenLabsSpeechToTextResult.Failure("Exception during TTS: ${e.localizedMessage}")
        }
    }

    fun playAudio(
        context: Context,
        audioData: ByteArray,
        onComplete: () -> Unit = {},
        onError: (Exception) -> Unit = {},
    ) {
        try {
            val tempFile =
                File.createTempFile("tts_audio", ".mp3", context.cacheDir).apply {
                    writeBytes(audioData)
                    deleteOnExit()
                }

            mediaPlayer?.release() // Clean up previous MediaPlayer instance
            mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        release()
                        onComplete()
                    }
                    setOnErrorListener { _, what, extra ->
                        release()
                        onError(Exception("MediaPlayer error: what=$what, extra=$extra"))
                        true // indicates we handled it
                    }
                    prepareAsync()
                }
        } catch (e: Exception) {
            onError(e)
        }
    }
}
