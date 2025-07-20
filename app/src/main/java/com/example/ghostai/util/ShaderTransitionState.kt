package com.example.ghostai.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable

@Stable
class ShaderTransitionState<T>(
    initialState: T,
    private val durationMillis: Int = 800,
    private val onStateChange: (start: T, target: T) -> Unit,
) {
    var startState: T = initialState
        private set

    var targetState: T = initialState
        private set

    private val progress = Animatable(1f)

    val transitionProgress: Float
        get() = progress.value

    suspend fun transitionTo(newState: T) {
        startState = targetState
        targetState = newState

        onStateChange(startState, targetState)

        progress.stop()
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis))
    }
}
