package com.example.ghostai.service

import android.app.Application
import android.content.Context
import android.media.AudioTrack
import android.media.MediaPlayer
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

private const val ELEVEN_LABS_API_BASE = "https://api.elevenlabs.io/v1"

private const val CHAROLETTE_VOICE_ID = "XB0fDUnXU5powFXDhCwa"
private const val DEMON_MONSTER_VOICE_ID = "vfaqCOvlrKi4Zp7C2IAm"

class ElevenLabsService(
    private val apiKey: String,
    private val client: HttpClient,
    private val okHttpClient: OkHttpClient,
    private val application: Application,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var webSocket: WebSocket? = null

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

    fun startStreamingSpeech(
        text: String,
        voiceId: String = CHAROLETTE_VOICE_ID,
        onError: (Exception) -> Unit = {},
        onEnd: () -> Unit = {},
    ) {
        stopStreamingSpeech()

        val mp3Buffer = ByteArrayOutputStream()

        val request = Request.Builder()
            .url("wss://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream-input")
            .addHeader("xi-api-key", apiKey)
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Timber.d("CGH: WebSocket opened for streaming")

                    val msg = """
                {
                    "text": ${text.quoteJson()},
                    "model_id": "eleven_monolingual_v1",
                    "voice_settings": {
                        "stability": 0.5,
                        "similarity_boost": 0.8
                    }
                }
                    """.trimIndent()

                    Timber.d("CGH: sending WebSocket message: $msg")
                    webSocket.send(msg)

                    val endMsg = """{"text": ""}"""
                    Timber.d("CGH: sending WebSocket end-of-input: $endMsg")
                    webSocket.send(endMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Timber.d("CGH: got control message: $text")
                    try {
                        val json = JSONObject(text)
                        if (json.has("audio") && !json.isNull("audio")) {
                            val base64Audio = json.getString("audio")
                            val audioBytes = Base64.getDecoder().decode(base64Audio)
                            mp3Buffer.write(audioBytes)
                        }

                        if (json.optBoolean("isFinal", false)) {
                            stopStreamingSpeech()

                            val tempFile = File.createTempFile("streamed_tts", ".mp3", application.cacheDir)
                            tempFile.writeBytes(mp3Buffer.toByteArray())
                            Timber.d("CGH: wrote MP3 file: ${tempFile.absolutePath}")

                            val mediaPlayer = MediaPlayer().apply {
                                setDataSource(tempFile.absolutePath)
                                setOnPreparedListener { start() }
                                setOnCompletionListener {
                                    release()
                                    onEnd()
                                }
                                setOnErrorListener { _, what, extra ->
                                    release()
                                    onError(Exception("MediaPlayer error: what=$what extra=$extra"))
                                    true
                                }
                                prepareAsync()
                            }
                        }

                        if (json.has("error")) {
                            Timber.e("CGH: ElevenLabs error: $text")
                            stopStreamingSpeech()
                            onError(Exception("Server error: $text"))
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "CGH: Error parsing control message")
                        stopStreamingSpeech()
                        onError(e)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Timber.d("CGH: WebSocket closing: $code $reason")
                    stopStreamingSpeech()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Timber.e(t, "CGH: WebSocket failure")
                    stopStreamingSpeech()
                    onError(Exception("WebSocket failure: ${t.message}"))
                }
            },
        )
    }

    fun stopStreamingSpeech() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        webSocket?.close(1000, "Closed by client")
        webSocket = null
    }

    // Helper to safely quote JSON strings
    private fun String.quoteJson(): String =
        "\"" + this.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
