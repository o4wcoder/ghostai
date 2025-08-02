package com.example.ghostai

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    private val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

    init {
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                }

                override fun onBeginningOfSpeech() {
                    onStart()
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
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
                    val resultText =
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()

                    if (!resultText.isNullOrBlank()) {
                        onResult(resultText)
                    }
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
        Timber.d("CGH: SpeechRecognizerManager.startListening()")
        recognizer.startListening(intent)
    }

    fun stopListening() {
        Timber.d("CGH: SpeechRecognizerManager.stopListening()")
        recognizer.stopListening()
    }

    fun destroy() {
        recognizer.destroy()
    }
}
