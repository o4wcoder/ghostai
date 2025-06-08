package com.example.ghostai

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AnimatedMistyBackground(
    imageIds: List<Int>,
    modifier: Modifier = Modifier,
    crossfadeDuration: Int = 6000
) {
    require(imageIds.size >= 2) { "Provide at least two mist images for animation." }

    var currentIndex by remember { mutableStateOf(0) }
    var nextIndex by remember { mutableStateOf((currentIndex + 1) % imageIds.size) }

    val transition = rememberInfiniteTransition(label = "mist-crossfade")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(crossfadeDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "crossfadeAlpha"
    )

    // Track alpha threshold to trigger image index change
    LaunchedEffect(alpha) {
        if (alpha > 0.99f) {
            currentIndex = nextIndex
            nextIndex = (nextIndex + 1) % imageIds.size
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = imageIds[currentIndex]),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 1f - alpha
        )

        Image(
            painter = painterResource(id = imageIds[nextIndex]),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = alpha
        )
    }
}

