package com.example.ghostai.audioeffects

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class GhostEchoProcessor : BaseAudioProcessor() {

    private lateinit var delayBuffer: ShortArray
    private var delayIndex = 0
    private var sampleRateHz = 44100
    private var channelCount = 2

    private val delayMs = 500 // 500ms delay
    private val decay = 0.15f // Echo volume decay

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount

        val delaySamples = ((delayMs / 1000f) * sampleRateHz * channelCount).toInt()
        delayBuffer = ShortArray(delaySamples)
        delayIndex = 0

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val outBuffer = replaceOutputBuffer(inputBuffer.remaining())

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            val delayed = delayBuffer[delayIndex]
            val mixed = (sample + delayed * decay).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            delayBuffer[delayIndex] = sample
            delayIndex = (delayIndex + 1) % delayBuffer.size
            outBuffer.putShort(mixed.toInt().toShort())
        }

        outBuffer.flip()
    }

    override fun isActive(): Boolean = true
}
