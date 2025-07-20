package com.example.ghostai.oldui

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ghostai.R
import com.example.ghostai.util.rememberStableTime

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
            },
        )
    }
}

@Composable
fun rememberGhostShader(): RuntimeShader {
    val context = LocalContext.current
    return remember {
        val shaderCode = context.resources
            .openRawResource(R.raw.old_ghost_shader)
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ghost()

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                isSpeaking = !isSpeaking
            }) {
                Text(if (isSpeaking) "Quiet" else "Speak")
            }
        }
    }
}
