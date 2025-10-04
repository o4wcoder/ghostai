package com.fourthwardai.ghostai.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

@Composable
fun <T> rememberShaderTransitionState(
    initialState: T,
    durationMillis: Int = 800,
    onStateChange: (start: T, target: T) -> Unit = { _, _ -> },
): ShaderTransitionState<T> {
    return remember {
        ShaderTransitionState(
            initialState = initialState,
            durationMillis = durationMillis,
            onStateChange = onStateChange,
        )
    }
}

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
