package com.example.ghostai.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ghostai.model.Emotion

@Composable
fun rememberEmotionTransitionState(
    initialEmotion: Emotion = Emotion.Neutral,
    durationMillis: Int = 800,
): EmotionTransitionState {
    return remember { EmotionTransitionState(initialEmotion, durationMillis) }
}

@Stable
class EmotionTransitionState(
    initialEmotion: Emotion,
    private val animationDurationMillis: Int = 800,
) {
    var startEmotion by mutableStateOf(initialEmotion)
        private set

    var targetEmotion by mutableStateOf(initialEmotion)
        private set

    private val progress = Animatable(1f)

    val emotionProgress: Float
        get() = progress.value

    suspend fun transitionTo(newEmotion: Emotion) {
        startEmotion = targetEmotion
        targetEmotion = newEmotion

        progress.stop()
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = animationDurationMillis),
        )
    }
}
