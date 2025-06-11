package com.example.ghostai.oldui

import android.graphics.RuntimeShader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.tooling.preview.Preview
import com.example.ghostai.R

@Composable
fun GhostMistBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Load the mist shader from raw resource
    val shader = remember {
        val shaderCode = context.resources
            .openRawResource(R.raw.old_mist_shader)
            .bufferedReader()
            .use { it.readText() }
        RuntimeShader(shaderCode)
    }

    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }

    // Animate time for drifting mist
    val time by rememberInfiniteTransition(label = "mist-time").animateFloat(
        initialValue = 0f,
        targetValue = 100_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100_000 * 1000, easing = LinearEasing)
        ),
        label = "mist-time"
    )

    // Update shader uniforms
    LaunchedEffect(time, canvasSize) {
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iResolution", canvasSize.width, canvasSize.height)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        canvasSize = size

        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iResolution", size.width, size.height)

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader = shader
            }
        )
    }
}

@Preview
@Composable
fun GhostMistBackgroundPreview() {
    GhostMistBackground()
}
