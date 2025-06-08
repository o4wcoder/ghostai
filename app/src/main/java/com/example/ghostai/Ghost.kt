package com.example.ghostai

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Ghost(modifier: Modifier = Modifier) {
    val shader = rememberGhostShader()
    val time = rememberStableTime()


    Canvas(modifier = modifier.size(240.dp)) {
        val width = size.width
        val height = size.height

        shader.setFloatUniform("iResolution", width, height)
        shader.setFloatUniform("iTime", time)

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader = shader
            }
        )
    }
}

@Composable
fun rememberStableTime(): Float {
    var timeSeconds by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsedNanos = now - startTime
            timeSeconds = elapsedNanos / 1_000_000_000f
        }
    }

    return timeSeconds
}



@Composable
fun rememberGhostShader(): RuntimeShader {
    val context = LocalContext.current
    return remember {
        val shaderCode = context.resources
            .openRawResource(R.raw.ghost_shader)
            .bufferedReader()
            .use { it.readText() }
        Log.d("GhostShader", "Shader code:\n$shaderCode")
        RuntimeShader(shaderCode)
    }
}


@Preview
@Composable
fun GhostPreview() {
    var isSpeaking by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
//        AnimatedMistyBackground(
//            imageIds = listOf(
//                R.drawable.mist1,
//                R.drawable.mist2,
//                R.drawable.mist3
//            )
//        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Ghost()

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.material3.Button(onClick = {
                isSpeaking = !isSpeaking
            }) {
                androidx.compose.material3.Text(if (isSpeaking) "Quiet" else "Speak")
            }
        }
    }
}