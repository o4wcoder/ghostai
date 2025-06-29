package com.example.ghostai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun AnimatedMistyBackground(
    imageIds: List<Int>,
    modifier: Modifier = Modifier,
    crossfadeDuration: Int = 6000,
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
            repeatMode = RepeatMode.Restart,
        ),
        label = "crossfadeAlpha",
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
            alpha = 1f - alpha,
        )

        Image(
            painter = painterResource(id = imageIds[nextIndex]),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = alpha,
        )
    }
}
