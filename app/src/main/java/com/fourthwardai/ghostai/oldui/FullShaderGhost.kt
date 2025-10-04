package com.fourthwardai.ghostai.oldui

import android.graphics.RuntimeShader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fourthwardai.ghostai.R

@Composable
fun FullShaderGhost(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Load the shader once
    val shader = remember {
        val shaderCode = context.resources
            .openRawResource(R.raw.old_full_ghost_shader)
            .bufferedReader()
            .use { it.readText() }
        RuntimeShader(shaderCode)
    }

    // Animate time
    val time by rememberInfiniteTransition(label = "shaderTime").animateFloat(
        initialValue = 0f,
        targetValue = 100_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100_000 * 1000, easing = LinearEasing),
        ),
        label = "shaderTime",
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
            },
        )
    }
}
