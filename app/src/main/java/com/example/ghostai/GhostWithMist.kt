package com.example.ghostai

import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.tooling.preview.Preview
import com.example.ghostai.model.ConversationState
import com.example.ghostai.model.Emotion
import com.example.ghostai.model.GhostUiState
import com.example.ghostai.shaders.EyesDark
import com.example.ghostai.shaders.GhostBody
import com.example.ghostai.shaders.Ground
import com.example.ghostai.shaders.Lighting
import com.example.ghostai.shaders.Main
import com.example.ghostai.shaders.Moon
import com.example.ghostai.shaders.Mouth
import com.example.ghostai.shaders.Uniforms
import com.example.ghostai.ui.theme.GhostAITheme
import com.example.ghostai.util.pointerTapEvents
import com.example.ghostai.util.rememberShaderTransitionState
import com.example.ghostai.util.rememberStableTime

@Composable
fun GhostWithMist(
    ghostUiState: GhostUiState,
    modifier: Modifier = Modifier,
    onGhostThouched: () -> Unit,
    time: Float = rememberStableTime(),
) {
    val ghostShader = listOf(
        Uniforms.uniformDefs,
        Lighting.lighting,
        Moon.moon,
        GhostBody.ghostBody,
        EyesDark.eyes,
        Mouth.mouth,
        Ground.ground,
        Main.main,
    ).joinToString("\n")

    val shader = remember {
        RuntimeShader(ghostShader)
    }

    val animatedSpeaking by animateFloatAsState(
        targetValue = if (ghostUiState.isSpeaking) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
    )

    val emotionTransitionState = rememberShaderTransitionState(initialState = Emotion.Neutral)

    LaunchedEffect(ghostUiState.targetEmotion) {
        emotionTransitionState.transitionTo(ghostUiState.targetEmotion)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerTapEvents(
                onTap = {
                    onGhostThouched()
                },
                onDoubleTap = {
                },
            ),

    ) {
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("isSpeaking", animatedSpeaking)
        shader.setFloatUniform("uTransitionProgress", emotionTransitionState.transitionProgress)
        shader.setFloatUniform("uStartState", emotionTransitionState.startState.id)
        shader.setFloatUniform("uTargetState", emotionTransitionState.targetState.id)
        shader.setFloatUniform("iResolution", size.width, size.height)

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader = shader
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GhostWithMistPreview() {
    GhostAITheme {
        GhostWithMist(ghostUiState = getGhostUiStatePreviewUiState(), time = 2.0F, modifier = Modifier.background(Color.Black), onGhostThouched = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GhostWithMistAngryEmotionPreview() {
    GhostAITheme {
        GhostWithMist(ghostUiState = getGhostUiStatePreviewUiState(targetEmotion = Emotion.Angry), time = 2.0F, modifier = Modifier.background(Color.Black), onGhostThouched = {})
    }
}

fun getGhostUiStatePreviewUiState(
    conversationState: ConversationState = ConversationState.GhostTalking,
    startEmotion: Emotion = Emotion.Neutral,
    targetEmotion: Emotion = Emotion.Neutral,
) = GhostUiState(
    conversationState = conversationState,
    startEmotion = startEmotion,
    targetEmotion = targetEmotion,
)
