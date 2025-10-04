package com.example.ghostai.service

import android.app.Application
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.ghostai.BuildConfig
import com.example.ghostai.audioeffects.GhostRenderersFactory
import com.example.ghostai.model.Emotion
import com.example.ghostai.model.GhostReply
import com.example.ghostai.network.ktorHttpClient
import com.example.ghostai.settings.TtsService
import com.example.ghostai.settings.Voice
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.Locale

private const val LLM_MODEL = "gpt-4o"

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
    private val application: Application,
) : AIService {

    suspend fun getGhostReply(conversationHistory: List<ChatMessage>): GhostReply {
        val messages = listOf(
            SystemMessage(GHOST_BACK_STORY_SYSTEM_PROMPT),
        ) + conversationHistory

        Timber.d("CGH: size of messages = ${messages.count()}")
        val request = ChatCompletionRequest(
            model = LLM_MODEL,
            messages = messages,
        )

        val replyText = try {
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

        return parseEmotionTag(replyText.trim())
    }

    @OptIn(UnstableApi::class)
    override suspend fun startStreamingSpeech(
        text: String,
        voiceId: String,
        callbacks: TtsCallbacks,
    ) {
        val tempFile = File.createTempFile("openai_tts", ".mp3", application.cacheDir).apply {
            deleteOnExit()
        }

        try {
            withContext(Dispatchers.IO) {
                client.preparePost("https://api.openai.com/v1/audio/speech") {
                    expectSuccess = false
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $apiKey")
                        append(HttpHeaders.Accept, "audio/mpeg")
                        append(HttpHeaders.AcceptEncoding, "identity") // no gzip
                    }
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("model", "tts-1")
                            put("voice", voiceId)
                            put("input", text)
                            put("stream", true)
                            put("format", "mp3")
                        },
                    )
                }.execute { resp ->
                    val ct = resp.headers[HttpHeaders.ContentType] ?: ""
                    if (!resp.status.isSuccess() || !ct.startsWith("audio/")) {
                        val err = runCatching { resp.bodyAsText() }.getOrNull()
                        throw IllegalStateException("TTS failed: ${resp.status} ct=$ct body=$err")
                    }

                    // 🔑 This actually blocks and copies the stream
                    resp.bodyAsChannel().toInputStream().use { input ->
                        tempFile.outputStream().use { out ->
                            input.copyTo(out)
                            out.flush()
                        }
                    }
                }

                Timber.d("MP3 TTS file size = ${tempFile.length()}")
                val head = ByteArray(4)
                FileInputStream(tempFile).use { it.read(head) }
                Timber.d("MP3 First bytes = ${head.joinToString { "%02X".format(it) }}")
                val looksMp3 = String(head, 0, 3) == "ID3" || (head[0].toInt() and 0xFF) == 0xFF
                Timber.d("MP3 header? $looksMp3  bytes=${head.joinToString { "%02X".format(it) }}")
            }

            withContext(Dispatchers.Main) {
                ExoPlayer.Builder(application)
                    .setRenderersFactory(GhostRenderersFactory(application.applicationContext))
                    .build().apply {
                        val mediaItem = MediaItem.fromUri(tempFile.toUri())
                        setMediaItem(mediaItem)
                        prepare()
                        playWhenReady = true
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                when (state) {
                                    Player.STATE_READY -> {
                                        if (playWhenReady) {
                                            callbacks.onStart()
                                        }
                                    }
                                    Player.STATE_ENDED -> {
                                        release()
                                        tempFile.delete()
                                        callbacks.onEnd()
                                    }
                                    else -> {}
                                }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                callbacks.onError(error)
                                release()
                            }
                        })
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "OpenAI TTS streaming failed")
            withContext(Dispatchers.Main) {
                callbacks.onError(e)
            }
        }
    }

    fun parseEmotionTag(rawText: String): GhostReply {
        val regex = Regex("""^\[Emotion:\s*(\w+)]\s*(.*)""", RegexOption.IGNORE_CASE)
        val match = regex.find(rawText)

        return if (match != null) {
            val emotionName = match.groupValues[1].trim().capitalize(Locale.ROOT)
            val message = match.groupValues[2].trim()

            val emotion = Emotion.entries.firstOrNull {
                it.name.equals(emotionName, ignoreCase = true)
            } ?: Emotion.Neutral

            GhostReply(emotion, message)
        } else {
            // Fallback if the emotion tag is missing
            GhostReply(Emotion.Neutral, rawText.trim())
        }
    }

    override fun isAvailable(): Boolean {
        return BuildConfig.OPENAI_API_KEY.trim().isNotEmpty()
    }

    override fun getVoices(): Map<TtsService, List<Voice>> {
        return mapOf(
            TtsService.OPENAI to listOf(
                Voice("alloy", "Alloy"),
                Voice("ash", "Ash"),
                Voice("echo", "Echo"),
                Voice("fable", "Fable"),
                Voice("onyx", "Onyx"),
                Voice("nova", "Nova"),
                Voice("sage", "Sage"),
                Voice("shimmer", "Shimmer"),
            ),
        )
    }

    override fun getDefaultVoiceId(): String {
        return "sage"
    }
}
