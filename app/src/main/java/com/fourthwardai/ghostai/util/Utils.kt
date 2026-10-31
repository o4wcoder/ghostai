package com.fourthwardai.ghostai.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import timber.log.Timber

private const val FRAME_RATE_LOG_INTERVAL_NANOS = 30_000_000_000L
private const val SLOW_FRAME_NANOS = 25_000_000L

@Composable
fun rememberStableTime(): Float {
    var timeSeconds by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        // Frame rate is logged so tablet performance (e.g. thermal throttling with visitor detection on) shows in logcat
        var windowStart = startTime
        var lastFrame = startTime
        var frames = 0
        var slowFrames = 0
        while (true) {
            val now = withFrameNanos { it }
            val elapsedNanos = now - startTime
            timeSeconds = elapsedNanos / 1_000_000_000f

            frames++
            if (now - lastFrame > SLOW_FRAME_NANOS) slowFrames++
            lastFrame = now
            if (now - windowStart >= FRAME_RATE_LOG_INTERVAL_NANOS) {
                val fps = frames * 1_000_000_000f / (now - windowStart)
                Timber.d("CGH: Shader frame rate: %.1f fps, slow frames (>%dms) = %d", fps, SLOW_FRAME_NANOS / 1_000_000, slowFrames)
                windowStart = now
                frames = 0
                slowFrames = 0
            }
        }
    }

    return timeSeconds
}
