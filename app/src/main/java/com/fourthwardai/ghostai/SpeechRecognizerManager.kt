package com.fourthwardai.ghostai

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import timber.log.Timber
import java.util.Locale

class SpeechRecognizerManager(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (Int, String) -> Unit,
) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
    }

    private var isListening = false
    private var isStarting = false

    /**
     * Some recognizer services (e.g. on tablets) play a beep when listening starts and stops. There is no API to
     * disable it, so the streams it plays on are muted for the duration of a listening session.
     */
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutedStreams = mutableSetOf<Int>()
    private val unmuteRunnable = Runnable { unmuteBeepStreams() }

    init {
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    isStarting = false
                }

                override fun onBeginningOfSpeech() {
                    onStart()
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    isListening = false
                    isStarting = false
                    scheduleUnmute(SESSION_END_UNMUTE_DELAY_MS)
                    val message =
                        when (error) {
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            else -> {
                                "Unknown error code: $error"
                            }
                        }
                    onError(error, message)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    isStarting = false
                    scheduleUnmute(SESSION_END_UNMUTE_DELAY_MS)
                    val resultText =
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()

                    if (resultText.isNullOrBlank()) {
                        Timber.d("SR: empty final → NO_MATCH (auto-restart)")
                        onError(SpeechRecognizer.ERROR_NO_MATCH, "Empty recognition result")
                        return
                    }

                    onResult(resultText)
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) {
                }
            },
        )
    }

    fun startListening() {
        if (isListening || isStarting) {
            Timber.d("CGH: SpeechRecognizerManager.startListening() already listening/starting")
            return
        }
        isStarting = true
        Timber.d("CGH: SpeechRecognizerManager.startListening()")
        muteBeepStreams()
        recognizer.startListening(intent)
    }

    fun stopListening() {
        Timber.d("CGH: SpeechRecognizerManager.stopListening()")
        recognizer.cancel()
        // Shorter delay here since TTS playback typically follows a cancel
        scheduleUnmute(CANCEL_UNMUTE_DELAY_MS)
    }

    fun destroy() {
        recognizer.destroy()
        mainHandler.removeCallbacks(unmuteRunnable)
        unmuteBeepStreams()
    }

    private fun muteBeepStreams() {
        mainHandler.removeCallbacks(unmuteRunnable)
        for (stream in BEEP_STREAMS) {
            // Leave streams the user already muted alone so we don't unmute them later
            if (stream in mutedStreams || audioManager.isStreamMute(stream)) continue
            try {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                mutedStreams += stream
            } catch (e: SecurityException) {
                // Thrown for some streams while Do Not Disturb is active
                Timber.w(e, "CGH: Unable to mute stream $stream")
            }
        }
    }

    private fun scheduleUnmute(delayMs: Long) {
        mainHandler.removeCallbacks(unmuteRunnable)
        mainHandler.postDelayed(unmuteRunnable, delayMs)
    }

    private fun unmuteBeepStreams() {
        for (stream in mutedStreams) {
            try {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            } catch (e: SecurityException) {
                Timber.w(e, "CGH: Unable to unmute stream $stream")
            }
        }
        mutedStreams.clear()
    }

    companion object {
        private val BEEP_STREAMS = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
        )

        // Long enough to cover the end-of-session beep and the view model's restart delay (~750ms)
        private const val SESSION_END_UNMUTE_DELAY_MS = 1000L
        private const val CANCEL_UNMUTE_DELAY_MS = 300L
    }
}
