package com.fourthwardai.ghostai.audioeffects

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class GhostMultiEchoProcessor : BaseAudioProcessor() {

    private var sampleRateHz = 44100
    private var channelCount = 2

    // Delay taps at 500ms, 700ms, and 900ms
    private lateinit var delayBuffers: Array<ShortArray>
    private val delaysMs = intArrayOf(500, 700) // , 900)
    private val decays = floatArrayOf(0.15f, 0.09f) // , 0.1f)
    private lateinit var delayIndices: IntArray

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount

        delayBuffers = delaysMs.map { delayMs ->
            val samples = ((delayMs / 1000f) * sampleRateHz * channelCount).toInt()
            ShortArray(samples)
        }.toTypedArray()

        delayIndices = IntArray(delayBuffers.size) { 0 }

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val outBuffer = replaceOutputBuffer(inputBuffer.remaining())
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            var mixed = sample.toFloat()

            for (i in delayBuffers.indices) {
                val delayedSample = delayBuffers[i][delayIndices[i]]
                mixed += delayedSample * decays[i]
            }

            for (i in delayBuffers.indices) {
                delayBuffers[i][delayIndices[i]] = sample
                delayIndices[i] = (delayIndices[i] + 1) % delayBuffers[i].size
            }

            val clamped = mixed.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            outBuffer.putShort(clamped.toInt().toShort())
        }

        outBuffer.flip()
    }
}
