package com.example.ghostai.service

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.ghostai.audioeffects.GhostRenderersFactory
import com.example.ghostai.network.model.ElevenLabsSpeechToTextResult
import com.example.ghostai.service.ElevenLabsVoiceIds.CHAROLETTE_VOICE_ID
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private const val ELEVEN_LABS_API_BASE = "https://api.elevenlabs.io/v1"

object ElevenLabsVoiceIds {
    const val CHAROLETTE_VOICE_ID = "XB0fDUnXU5powFXDhCwa"
    const val DEMON_MONSTER_VOICE_ID = "vfaqCOvlrKi4Zp7C2IAm"
    const val COCKY_MALE_VILLAN = "zYcjlYFOd3taleS0gkk3"
    const val BRITNEY_FEMALE_VILLAN = "esy0r39YPLQjOczyOib8"
}

private const val MODEL_ID = "eleven_flash_v2_5"

@OptIn(UnstableApi::class)
class ElevenLabsService(
    private val apiKey: String,
    private val client: HttpClient,
    private val okHttpClient: OkHttpClient,
    private val application: Application,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    suspend fun startStreamingSpeech(
        text: String,
        voiceId: String = CHAROLETTE_VOICE_ID,
        callbacks: TtsCallbacks,
    ) {
        stopStreamingSpeech()

        val buffer = ByteArrayOutputStream()
        var exoPlayer: ExoPlayer? = null

        val request = Request.Builder()
            .url("wss://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream-input")
            .addHeader("xi-api-key", apiKey)
            .build()

        withContext(Dispatchers.IO) {
            webSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Timber.d("WebSocket opened for streaming")

                        val msg = """
                {
                    "text": ${text.quoteJson()},
                    "model_id": "$MODEL_ID",
                    "voice_settings": {
                        "stability": 0.5,
                        "similarity_boost": 0.8
                    }
                }
                        """.trimIndent()

                        webSocket.send(msg)

                        // Signal end of input (required so server knows you're done)
                        val endMsg = """{"text": ""}"""
                        webSocket.send(endMsg)

                        // Set up ExoPlayer
                        exoPlayer = ExoPlayer.Builder(application.applicationContext)
                            .setRenderersFactory(GhostRenderersFactory(application.applicationContext))
                            .build().apply {
                                addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(state: Int) {
                                        when (state) {
                                            Player.STATE_READY -> {
                                                if (exoPlayer?.playWhenReady == true) {
                                                    callbacks.onStart()
                                                }
                                            }

                                            Player.STATE_ENDED -> {
                                                stopStreamingSpeech()
                                                callbacks.onEnd()
                                            }

                                            else -> {}
                                        }
                                    }

                                    override fun onPlayerError(error: PlaybackException) {
                                        Timber.e(error, "ExoPlayer playback error")
                                        stopStreamingSpeech()
                                        callbacks.onError(error)
                                    }
                                })
                            }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Timber.d("Control message: $text")
                        val json = JSONObject(text)

                        if (json.has("audio")) {
                            val audioBase64 = json.getString("audio")
                            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                            buffer.write(audioBytes)
                        }

                        if (json.optBoolean("isFinal", false)) {
                            Timber.d("isFinal received — finalizing playback")
                            playBufferedMp3(
                                application.applicationContext,
                                buffer.toByteArray(),
                                exoPlayer!!,
                            )
                        }

                        if (json.has("error")) {
                            stopStreamingSpeech()
                            callbacks.onError(Exception("Server error: $text"))
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Timber.d("WebSocket closing: $code $reason")
                        stopStreamingSpeech()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        Timber.e(t, "WebSocket failure")
                        stopStreamingSpeech()
                        callbacks.onError(Exception("WebSocket failure: ${t.message}"))
                    }
                },
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun playBufferedMp3(context: Context, mp3Data: ByteArray, exoPlayer: ExoPlayer) {
        val tempFile = File.createTempFile("ghost_stream", ".mp3", context.cacheDir).apply {
            writeBytes(mp3Data)
            deleteOnExit()
        }

        scope.launch(Dispatchers.Main) {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(tempFile.toUri()))

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun stopStreamingSpeech() {
        webSocket?.close(1000, "Closed by client")
        webSocket = null
    }

    // Helper to safely quote JSON strings
    private fun String.quoteJson(): String =
        "\"" + this.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
