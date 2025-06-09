package com.example.ghostai

import android.graphics.RuntimeShader
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun FullShaderGhost(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Load the shader once
    val shader = remember {
        val shaderCode = context.resources
            .openRawResource(R.raw.full_ghost_shader)
            .bufferedReader()
            .use { it.readText() }
        RuntimeShader(shaderCode)
    }

    // Animate time
    val time by rememberInfiniteTransition(label = "shaderTime").animateFloat(
        initialValue = 0f,
        targetValue = 100_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100_000 * 1000, easing = LinearEasing)
        ),
        label = "shaderTime"
    )

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Update uniforms reactively
    LaunchedEffect(time, canvasSize, isSpeaking) {
        if (canvasSize.width > 0f && canvasSize.height > 0f) {
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("iResolution", canvasSize.width, canvasSize.height)
            shader.setFloatUniform("isSpeaking", if (isSpeaking) 1f else 0f)
        }
    }

    Canvas(modifier = modifier.size(300.dp)) {
        canvasSize = size

        // Defensive check (optional)
        if (size.width > 0f && size.height > 0f) {
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("isSpeaking", if (isSpeaking) 1f else 0f)
        }

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader = shader
            }
        )
    }
}


