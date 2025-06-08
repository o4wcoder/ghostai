package com.example.ghostai

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun Ghost(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ghost-float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isSpeaking) 0.8f else 0.4f,
        animationSpec = tween(500),
        label = "glowAlpha"
    )

    var blinkAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000L + (1000..3000).random())
            blinkAlpha = 0f
            delay(200L)
            blinkAlpha = 1f
        }
    }

    Canvas(
        modifier = modifier
            .size(240.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Green.copy(alpha = glowAlpha * 0.4f),
                            Color.Transparent
                        ),
                        radius = size.minDimension * 0.9f,
                        center = center
                    )
                )
            }
    ) {
        translate(top = floatOffset) {
            val width = size.width
            val height = size.height

            val bodyPath = Path().apply {
                moveTo(width * 0.2f, height * 0.4f)
                quadraticTo(width * 0.5f, height * 0.2f, width * 0.8f, height * 0.4f)
                lineTo(width * 0.8f, height * 0.8f)
                quadraticTo(width * 0.65f, height * 0.75f, width * 0.6f, height * 0.9f)
                quadraticTo(width * 0.5f, height * 0.75f, width * 0.4f, height * 0.9f)
                quadraticTo(width * 0.35f, height * 0.75f, width * 0.2f, height * 0.8f)
                close()
            }

            drawPath(
                path = bodyPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Green.copy(alpha = 0.95f),
                        Color.Green.copy(alpha = 0.7f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            val eyeRadius = width * 0.04f
            val eyeY = height * 0.45f
            drawCircle(
                color = Color.Black.copy(alpha = blinkAlpha),
                radius = eyeRadius,
                center = Offset(width * 0.4f, eyeY)
            )
            drawCircle(
                color = Color.Black.copy(alpha = blinkAlpha),
                radius = eyeRadius,
                center = Offset(width * 0.6f, eyeY)
            )
        }
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
            Ghost(isSpeaking = isSpeaking)

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.material3.Button(onClick = {
                isSpeaking = !isSpeaking
            }) {
                androidx.compose.material3.Text(if (isSpeaking) "Quiet" else "Speak")
            }
        }
    }
}