package com.example.ghostai

import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.ghostai.model.ConversationState
import com.example.ghostai.model.DeviceSettings
import com.example.ghostai.model.Emotion
import com.example.ghostai.model.FormFactor
import com.example.ghostai.model.GhostUiState
import com.example.ghostai.shaders.EyesDark
import com.example.ghostai.shaders.GhostBody
import com.example.ghostai.shaders.Lighting
import com.example.ghostai.shaders.MainGhost
import com.example.ghostai.shaders.MainSky
import com.example.ghostai.shaders.Moon
import com.example.ghostai.shaders.Mouth
import com.example.ghostai.shaders.Uniforms
import com.example.ghostai.ui.theme.GhostAITheme
import com.example.ghostai.util.pointerTapEvents
import com.example.ghostai.util.rememberShaderTransitionState
import com.example.ghostai.util.rememberStableTime

@Composable
fun GhostWithMist(
    deviceSettings: DeviceSettings,
    ghostUiState: GhostUiState,
    modifier: Modifier = Modifier,
    onGhostTouched: () -> Unit,
    time: Float = rememberStableTime(),
) {
    val skyShader = listOf(
        Uniforms.uniformDefs,
        Lighting.lighting,
        Moon.moon,
        MainSky.main,
    ).joinToString("\n")

    val ghostShader = listOf(
        Uniforms.uniformDefs,
        Lighting.lighting,
        GhostBody.ghostBody,
        EyesDark.eyes,
        Mouth.mouth,
        MainGhost.main,
    ).joinToString("\n")

    val skyRuntimeShader = remember {
        RuntimeShader(skyShader)
    }
    val ghostRuntimeShader = remember {
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

    Box {
        Canvas(
            modifier = modifier
                .fillMaxSize(),

        ) {
            skyRuntimeShader.setFloatUniform("iTime", time)
            skyRuntimeShader.setFloatUniform("isSpeaking", animatedSpeaking)
            skyRuntimeShader.setFloatUniform("uTransitionProgress", emotionTransitionState.transitionProgress)
            skyRuntimeShader.setFloatUniform("uStartState", emotionTransitionState.startState.id)
            skyRuntimeShader.setFloatUniform("uTargetState", emotionTransitionState.targetState.id)
            skyRuntimeShader.setFloatUniform("iResolution", size.width, size.height)
            skyRuntimeShader.setFloatUniform("uGroundEnabled", 1.0F)
            skyRuntimeShader.setFloatUniform("uQuality", deviceSettings.quality)
            skyRuntimeShader.setFloatUniform("uFps", deviceSettings.fps)
            skyRuntimeShader.setFloatUniform("isTablet", deviceSettings.isTablet)

            drawRect(
                brush = object : ShaderBrush() {
                    override fun createShader(size: Size): Shader = skyRuntimeShader
                },
            )
        }

        Image(
            painter = painterResource(R.drawable.tablet_normal_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),

        )

        Canvas(
            modifier = modifier
                .fillMaxSize()
                .pointerTapEvents(
                    onTap = {
                        onGhostTouched()
                    },
                    onDoubleTap = {
                    },
                ),

        ) {
            ghostRuntimeShader.setFloatUniform("iTime", time)
            ghostRuntimeShader.setFloatUniform("isSpeaking", animatedSpeaking)
            ghostRuntimeShader.setFloatUniform("uTransitionProgress", emotionTransitionState.transitionProgress)
            ghostRuntimeShader.setFloatUniform("uStartState", emotionTransitionState.startState.id)
            ghostRuntimeShader.setFloatUniform("uTargetState", emotionTransitionState.targetState.id)
            ghostRuntimeShader.setFloatUniform("iResolution", size.width, size.height)
            ghostRuntimeShader.setFloatUniform("uGroundEnabled", 1.0F)
            ghostRuntimeShader.setFloatUniform("uQuality", deviceSettings.quality)
            ghostRuntimeShader.setFloatUniform("uFps", deviceSettings.fps)
            ghostRuntimeShader.setFloatUniform("isTablet", deviceSettings.isTablet)

            drawRect(
                brush = object : ShaderBrush() {
                    override fun createShader(size: Size): Shader = ghostRuntimeShader
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GhostWithMistPreview() {
    GhostAITheme {
        GhostWithMist(deviceSettings = DeviceSettings(FormFactor.Phone, 2f, 60f), ghostUiState = getGhostUiStatePreviewUiState(), time = 2.0F, modifier = Modifier.background(Color.Black), onGhostTouched = {})
    }
}

// @Preview(showBackground = true)
// @Composable
// fun GhostWithMistAngryEmotionPreview() {
//    GhostAITheme {
//        GhostWithMist(ghostUiState = getGhostUiStatePreviewUiState(targetEmotion = Emotion.Angry), time = 2.0F, modifier = Modifier.background(Color.Black), onGhostThouched = {})
//    }
// }

fun getGhostUiStatePreviewUiState(
    conversationState: ConversationState = ConversationState.GhostTalking,
    startEmotion: Emotion = Emotion.Neutral,
    targetEmotion: Emotion = Emotion.Neutral,
) = GhostUiState(
    conversationState = conversationState,
    startEmotion = startEmotion,
    targetEmotion = targetEmotion,
)
