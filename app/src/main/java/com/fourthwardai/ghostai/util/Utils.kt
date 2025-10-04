package com.fourthwardai.ghostai.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

@Composable
fun rememberStableTime(): Float {
    var timeSeconds by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsedNanos = now - startTime
            timeSeconds = elapsedNanos / 1_000_000_000f
        }
    }

    return timeSeconds
}
