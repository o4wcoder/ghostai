package com.example.ghostai

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.ghostai.oldui.rememberStableTime
import com.example.ghostai.ui.theme.GhostAITheme

@Composable
fun GhostWithMist(isSpeaking: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val shader = remember {
        val shaderCode = context.resources
            .openRawResource(R.raw.ghost_and_mist_shader)
            .bufferedReader()
            .use { it.readText() }
        RuntimeShader(shaderCode)
    }

    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }
    val time = rememberStableTime()

    LaunchedEffect(time, isSpeaking, canvasSize) {
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("isSpeaking", if (isSpeaking) 1f else 0f)
        shader.setFloatUniform("iResolution", canvasSize.width, canvasSize.height)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        canvasSize = size
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("isSpeaking", if (isSpeaking) 1f else 0f)
        shader.setFloatUniform("iResolution", size.width, size.height)

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader = shader
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GhostWithMistPreview() {
    GhostAITheme {
        GhostWithMist(isSpeaking = false, modifier = Modifier.background(Color.Black))
    }
}

