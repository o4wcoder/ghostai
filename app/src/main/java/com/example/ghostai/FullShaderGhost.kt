package com.example.ghostai

import android.graphics.RuntimeShader
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposePaint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.delay

@Composable
fun FullShaderGhost(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Load shader once
    val shader = remember {
        val inputStream = context.resources.openRawResource(R.raw.full_ghost_shader)
        val shaderCode = inputStream.bufferedReader().use { it.readText() }
        RuntimeShader(shaderCode)
    }

    // Animate time and track canvas size
    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }

    val time by rememberInfiniteTransition(label = "shaderTime").animateFloat(
        initialValue = 0f,
        targetValue = 100_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100_000 * 1000, easing = LinearEasing)
        ),
        label = "shaderTime"
    )

    LaunchedEffect(time, canvasSize, isSpeaking) {
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iResolution", canvasSize.width, canvasSize.height)
        shader.setFloatUniform("isSpeaking", if (isSpeaking) 1f else 0f)
    }

    Canvas(modifier = modifier.size(240.dp)) {
        canvasSize = size

        // Set uniforms every frame
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("isSpeaking", if (isSpeaking) 1f else 0f)

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader {
                    return shader
                }
            }
        )
    }

}
